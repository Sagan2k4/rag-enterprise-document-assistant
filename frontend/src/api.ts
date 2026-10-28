import type {
  AskReply,
  ConversationDetail,
  ConversationSummary,
  Dashboard,
  DocumentInfo,
  EvalRunDetail,
  EvalSummary,
  LoginResponse,
  Role,
  User,
} from './types';

const TOKEN_KEY = 'rag.token';

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

// sessionStorage: the token lives only as long as the browser tab, so reopening the app asks
// for credentials again instead of silently signing the last user back in.
export const tokenStore = {
  get: () => safeStorage(() => sessionStorage.getItem(TOKEN_KEY)),
  set: (token: string) => safeStorage(() => sessionStorage.setItem(TOKEN_KEY, token)),
  clear: () =>
    safeStorage(() => {
      sessionStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(TOKEN_KEY);
    }),
};

// Earlier versions kept the token in localStorage; remove it so it can't linger.
safeStorage(() => localStorage.removeItem(TOKEN_KEY));

function safeStorage<T>(fn: () => T): T | null {
  try {
    return fn();
  } catch {
    return null;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');

  const res = await fetch(`/api${path}`, { ...init, headers });
  if (res.status === 401 && path !== '/auth/login') {
    tokenStore.clear();
    window.dispatchEvent(new Event('auth:logout'));
    throw new ApiError(401, 'Your session has expired. Please sign in again.');
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => null);
    throw new ApiError(res.status, problem?.detail ?? `Request failed (${res.status})`);
  }
  return res.status === 204 ? (undefined as T) : res.json();
}

export const api = {
  login: (username: string, password: string) =>
    request<LoginResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) }),
  me: () => request<User>('/auth/me'),

  ask: (question: string, conversationId: number | null) =>
    request<AskReply>('/chat/ask', { method: 'POST', body: JSON.stringify({ question, conversationId }) }),
  conversations: () => request<ConversationSummary[]>('/chat/conversations'),
  conversation: (id: number) => request<ConversationDetail>(`/chat/conversations/${id}`),
  deleteConversation: (id: number) => request<void>(`/chat/conversations/${id}`, { method: 'DELETE' }),

  documents: () => request<DocumentInfo[]>('/documents'),
  upload: (file: File, title: string, roles: Role[]) => {
    const form = new FormData();
    form.append('file', file);
    if (title) form.append('title', title);
    form.append('allowedRoles', roles.join(','));
    return request<DocumentInfo>('/documents', { method: 'POST', body: form });
  },
  importWiki: (url: string, title: string, allowedRoles: Role[]) =>
    request<DocumentInfo>('/documents/wiki', {
      method: 'POST',
      body: JSON.stringify({ url, title: title || null, allowedRoles }),
    }),
  deleteDocument: (id: number) => request<void>(`/documents/${id}`, { method: 'DELETE' }),

  dashboard: (days: number) => request<Dashboard>(`/metrics/dashboard?days=${days}`),

  runEval: (judge: boolean) => request<EvalRunDetail>(`/eval/runs?judge=${judge}`, { method: 'POST' }),
  evalRuns: () => request<EvalSummary[]>('/eval/runs'),
  evalRun: (id: number) => request<EvalRunDetail>(`/eval/runs/${id}`),
};
