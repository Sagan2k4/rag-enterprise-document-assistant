export type Role = 'ADMIN' | 'HR' | 'FINANCE' | 'ENGINEERING' | 'EMPLOYEE';

export const ALL_ROLES: Role[] = ['EMPLOYEE', 'HR', 'FINANCE', 'ENGINEERING', 'ADMIN'];

export interface User {
  id: number;
  username: string;
  fullName: string | null;
  roles: Role[];
}

export interface LoginResponse {
  token: string;
  expiresInSeconds: number;
  user: User;
}

export interface Source {
  number: number;
  cited: boolean;
  documentId: number;
  documentTitle: string;
  filename: string;
  pageNumber: number | null;
  section: string | null;
  snippet: string;
  fusedScore: number;
  vectorRank: number | null;
  keywordRank: number | null;
}

export interface AskResponse {
  answer: string;
  sources: Source[];
  grounded: boolean;
  cached: boolean;
  model: string | null;
  metrics: {
    latencyMs: number;
    retrievalMs: number;
    generationMs: number;
    promptTokens: number;
    completionTokens: number;
    embeddingTokens: number;
    costUsd: number;
  };
}

export interface AskReply extends AskResponse {
  conversationId: number;
}

export interface ConversationSummary {
  id: number;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConversationDetail extends ConversationSummary {
  messages: { id: number; question: string; response: AskResponse; createdAt: string }[];
}

export type DocumentStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED';

export interface DocumentInfo {
  id: number;
  title: string;
  filename: string;
  sourceType: string;
  sourceUrl: string | null;
  sizeBytes: number;
  allowedRoles: Role[];
  status: DocumentStatus;
  pageCount: number | null;
  chunkCount: number;
  tokenCount: number;
  embeddingCostUsd: number;
  errorMessage: string | null;
  uploadedBy: string | null;
  createdAt: string;
}

export interface Dashboard {
  days: number;
  summary: {
    totalQueries: number;
    avgLatencyMs: number;
    p50LatencyMs: number;
    p95LatencyMs: number;
    totalCostUsd: number;
    avgCostPerQueryUsd: number;
    cacheHitRate: number;
    groundedRate: number;
    promptTokens: number;
    completionTokens: number;
  };
  daily: { day: string; queries: number; costUsd: number; avgLatencyMs: number }[];
  corpus: { documents: number; ready: number; failed: number; chunks: number; tokens: number; embeddingCostUsd: number };
  recent: {
    id: number;
    username: string;
    question: string;
    grounded: boolean;
    cacheHit: boolean;
    sources: number;
    latencyMs: number;
    costUsd: number;
    createdAt: string;
  }[];
}

export interface EvalSummary {
  id: number;
  createdAt: string;
  caseCount: number;
  hitRate: number;
  mrr: number;
  keywordRecall: number;
  citationRate: number;
  refusalAccuracy: number;
  leakageCount: number;
  faithfulness: number | null;
  avgLatencyMs: number;
  totalCostUsd: number;
}

export interface EvalCaseResult {
  id: string;
  question: string;
  asRole: Role;
  shouldAnswer: boolean;
  hit: boolean;
  reciprocalRank: number;
  keywordRecall: number;
  grounded: boolean;
  refused: boolean;
  leaked: boolean;
  faithfulness: number | null;
  passed: boolean;
  latencyMs: number;
  costUsd: number;
  answer: string;
  retrievedDocuments: string[];
}

export interface EvalRunDetail {
  summary: EvalSummary;
  cases: EvalCaseResult[];
}
