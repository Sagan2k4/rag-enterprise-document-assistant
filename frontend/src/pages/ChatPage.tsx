import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import { api, ApiError } from '../api';
import { useAuth } from '../auth';
import AnswerText from '../components/AnswerText';
import { ms, usd } from '../format';
import type { AskResponse, ConversationSummary, Role, Source } from '../types';

interface Message {
  id: number;
  question: string;
  response?: AskResponse;
  error?: string;
}

const SUGGESTIONS: Record<Role, string[]> = {
  EMPLOYEE: [
    'How many days of paid annual leave do I get?',
    'What is the hotel limit per night in London?',
    'I clicked a phishing link — what should I do?',
  ],
  HR: ['What is the salary range for an L5 Senior Software Engineer?', 'When does the merit cycle run?'],
  ENGINEERING: ['How many approvals does a pull request need?', 'What is the SEV1 acknowledgement time?'],
  FINANCE: ['What is the accrual threshold at quarter end?', 'Who approves a $300,000 journal entry?'],
  ADMIN: ['What is the salary range for an L5 Senior Software Engineer?', 'What is the deploy window?'],
};

export default function ChatPage() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const activeId = Number(params.get('c')) || null;
  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [busy, setBusy] = useState(false);
  const [loadingChat, setLoadingChat] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  // Set when a new chat gets its id from the server, so the URL change doesn't refetch what is already shown.
  const justCreated = useRef<number | null>(null);

  const refreshHistory = useCallback(() => {
    api.conversations().then(setConversations).catch(() => {});
  }, []);

  useEffect(refreshHistory, [refreshHistory]);

  // Open the chat named in the URL (?c=id), so a reload or returning later shows it again.
  useEffect(() => {
    if (activeId == null) {
      setMessages([]);
      return;
    }
    if (justCreated.current === activeId) {
      justCreated.current = null;
      return;
    }
    let cancelled = false;
    setLoadingChat(true);
    api
      .conversation(activeId)
      .then((c) => {
        if (!cancelled) setMessages(c.messages.map((m) => ({ id: m.id, question: m.question, response: m.response })));
      })
      .catch((e) => {
        if (!cancelled && e instanceof ApiError && e.status === 404) setParams({}, { replace: true });
      })
      .finally(() => {
        if (!cancelled) setLoadingChat(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeId, setParams]);

  // Block body on purpose: newer browsers return a Promise from smooth scrollIntoView, and
  // React would call any returned value as the effect's cleanup function (blank-screen crash).
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Grow the textarea with its content, up to the CSS max-height.
  useEffect(() => {
    const el = inputRef.current;
    if (!el) return;
    el.style.height = 'auto';
    el.style.height = `${el.scrollHeight}px`;
  }, [input]);

  const suggestions = [...new Set((user?.roles ?? []).flatMap((r) => SUGGESTIONS[r] ?? []))].slice(0, 4);
  const firstName = (user?.fullName ?? user?.username ?? '').split(/[\s(]/)[0];

  const openChat = (id: number | null) => {
    if (busy) return;
    setHistoryOpen(false);
    setParams(id == null ? {} : { c: String(id) });
    inputRef.current?.focus();
  };

  const removeChat = async (c: ConversationSummary) => {
    if (busy || !window.confirm(`Delete “${c.title}”?`)) return;
    try {
      await api.deleteConversation(c.id);
      setConversations((list) => list.filter((x) => x.id !== c.id));
      if (c.id === activeId) setParams({});
    } catch {
      refreshHistory();
    }
  };

  const ask = async (question: string) => {
    const q = question.trim();
    if (!q || busy) return;
    const id = -Date.now();
    setMessages((m) => [...m, { id, question: q }]);
    setInput('');
    setBusy(true);
    try {
      const { conversationId, ...response } = await api.ask(q, activeId);
      setMessages((m) => m.map((x) => (x.id === id ? { ...x, response } : x)));
      if (conversationId !== activeId) {
        justCreated.current = conversationId;
        setParams({ c: String(conversationId) }, { replace: true });
      }
      refreshHistory();
    } catch (e) {
      setMessages((m) => m.map((x) => (x.id === id ? { ...x, error: e instanceof Error ? e.message : 'Failed' } : x)));
    } finally {
      setBusy(false);
      inputRef.current?.focus();
    }
  };

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      ask(input);
    }
  };

  return (
    <div className="chat-layout">
      <aside className={`history ${historyOpen ? 'open' : ''}`}>
        <button className="btn primary block" onClick={() => openChat(null)} disabled={busy}>
          + New chat
        </button>
        <div className="history-list">
          {conversations.length === 0 && <p className="history-empty">Your chats will be saved here.</p>}
          {groupByDate(conversations).map(([label, items]) => (
            <div key={label} className="history-group">
              <div className="history-label">{label}</div>
              {items.map((c) => (
                <div key={c.id} className={`history-item ${c.id === activeId ? 'active' : ''}`}>
                  <button className="history-open" onClick={() => openChat(c.id)} disabled={busy} title={c.title}>
                    {c.title}
                  </button>
                  <button className="history-delete" onClick={() => removeChat(c)} disabled={busy} aria-label="Delete chat" title="Delete chat">
                    ×
                  </button>
                </div>
              ))}
            </div>
          ))}
        </div>
      </aside>
      {historyOpen && <div className="history-backdrop" onClick={() => setHistoryOpen(false)} />}

      <div className="chat">
        <div className="thread">
          <button className="history-toggle" onClick={() => setHistoryOpen(true)}>
            ☰ Chats
          </button>
          <div className="thread-inner">
            {loadingChat ? (
              <div className="typing center" aria-label="Loading chat">
                <span />
                <span />
                <span />
              </div>
            ) : messages.length === 0 ? (
              <div className="welcome">
                <div className="welcome-logo" aria-hidden>
                  ◆
                </div>
                <h1>{firstName ? `Hi ${firstName}, how can I help?` : 'How can I help?'}</h1>
                <p className="muted">
                  Answers come only from documents you have access to, with a source for every claim.
                </p>
                <div className="suggestions">
                  {suggestions.map((s) => (
                    <button key={s} className="suggestion" onClick={() => ask(s)}>
                      {s}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              messages.map((m) => (
                <div key={m.id} className="exchange">
                  <div className="msg-user">{m.question}</div>
                  <div className="msg-assistant">
                    <div className="avatar" aria-hidden>
                      ◆
                    </div>
                    <div className="msg-body">
                      {!m.response && !m.error && (
                        <div className="typing" aria-label="Searching documents">
                          <span />
                          <span />
                          <span />
                        </div>
                      )}
                      {m.error && <div className="alert error">{m.error}</div>}
                      {m.response && <Answer response={m.response} msgId={m.id} />}
                    </div>
                  </div>
                </div>
              ))
            )}
            <div ref={bottomRef} />
          </div>
        </div>

        <div className="composer-wrap">
          <div className="composer">
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={onKey}
              placeholder="Ask about policies, procedures, benefits…"
              rows={1}
              maxLength={2000}
              autoFocus
            />
            <button
              className="send"
              onClick={() => ask(input)}
              disabled={busy || loadingChat || !input.trim()}
              aria-label="Send"
              title="Send (Enter)"
            >
              <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
                <path d="M12 19V5M5 12l7-7 7 7" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            </button>
          </div>
          <div className="composer-hint">Enter to send · Shift + Enter for a new line · Chats are saved automatically</div>
        </div>
      </div>
    </div>
  );
}

/** Buckets chats (already newest first) into Today / Yesterday / Previous 7 days / Older. */
function groupByDate(list: ConversationSummary[]): [string, ConversationSummary[]][] {
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);
  const day = 86_400_000;
  const label = (iso: string) => {
    const t = new Date(iso).getTime();
    if (t >= startOfToday.getTime()) return 'Today';
    if (t >= startOfToday.getTime() - day) return 'Yesterday';
    if (t >= startOfToday.getTime() - 7 * day) return 'Previous 7 days';
    return 'Older';
  };
  const groups = new Map<string, ConversationSummary[]>();
  for (const c of list) {
    const key = label(c.updatedAt);
    groups.set(key, [...(groups.get(key) ?? []), c]);
  }
  return [...groups.entries()];
}

function Answer({ response, msgId }: { response: AskResponse; msgId: number }) {
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState<number | null>(null);
  const cited = response.sources.filter((s) => s.cited);
  const shown = cited.length ? cited : response.sources;

  const showSource = (n: number) => {
    setOpen(true);
    setHighlight(n);
    requestAnimationFrame(() => {
      document.getElementById(`src-${msgId}-${n}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  };

  return (
    <>
      <AnswerText text={response.answer} onCite={showSource} />
      {!response.grounded && <div className="note">Nothing in your accessible documents answers this.</div>}

      {response.grounded && shown.length > 0 && (
        <div className="sources">
          <button className="sources-toggle" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
            <span className="chevron" data-open={open} aria-hidden>
              ›
            </span>
            {cited.length || shown.length} source{(cited.length || shown.length) === 1 ? '' : 's'}
            <span className="sources-preview">
              {[...new Set(shown.map((s) => s.documentTitle))].join(' · ')}
            </span>
          </button>
          {open && (
            <div className="source-list">
              {shown.map((s) => (
                <SourceCard key={s.number} source={s} msgId={msgId} highlighted={highlight === s.number} />
              ))}
            </div>
          )}
        </div>
      )}

      <div className="metrics-line">
        {response.cached && <span>cached</span>}
        <span>{ms(response.metrics.latencyMs)}</span>
        <span>{usd(response.metrics.costUsd)}</span>
      </div>
    </>
  );
}

function SourceCard({ source: s, msgId, highlighted }: { source: Source; msgId: number; highlighted: boolean }) {
  return (
    <div id={`src-${msgId}-${s.number}`} className={`source ${highlighted ? 'highlight' : ''}`}>
      <div className="source-title">
        <span className="cite static">{s.number}</span>
        <strong>{s.documentTitle}</strong>
        <span className="source-loc">
          {s.pageNumber != null ? `Page ${s.pageNumber}` : s.section ?? s.filename}
        </span>
      </div>
      <div className="snippet">{s.snippet}</div>
    </div>
  );
}
