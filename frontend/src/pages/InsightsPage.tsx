import { useEffect, useState } from 'react';
import { api } from '../api';
import { dateTime, ms, num, pct, usd } from '../format';
import type { Dashboard } from '../types';

export default function InsightsPage() {
  const [days, setDays] = useState(7);
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.dashboard(days).then(setData).catch((e) => setError(e.message));
  }, [days]);

  if (error) return <div className="alert error">{error}</div>;
  if (!data) return <div className="muted">Loading…</div>;

  const s = data.summary;
  const maxQueries = Math.max(1, ...data.daily.map((d) => d.queries));

  return (
    <div className="page">
      <div className="page-head row">
        <div>
          <h1>Insights</h1>
          <p className="muted">LLM cost, latency and answer quality from production traffic.</p>
        </div>
        <select value={days} onChange={(e) => setDays(Number(e.target.value))}>
          <option value={1}>Last 24 hours</option>
          <option value={7}>Last 7 days</option>
          <option value={30}>Last 30 days</option>
        </select>
      </div>

      <div className="stats">
        <Stat label="Questions" value={num(s.totalQueries)} />
        <Stat label="p50 latency" value={ms(s.p50LatencyMs)} />
        <Stat label="p95 latency" value={ms(s.p95LatencyMs)} />
        <Stat label="LLM spend" value={usd(s.totalCostUsd)} hint={`${usd(s.avgCostPerQueryUsd)} per question`} />
        <Stat label="Cache hit rate" value={pct(s.cacheHitRate)} />
        <Stat label="Grounded answers" value={pct(s.groundedRate)} hint="answers with at least one citation" />
      </div>

      <div className="two-col">
        <div className="card">
          <h2>Questions per day</h2>
          {data.daily.length === 0 && <p className="muted">No traffic yet — ask a few questions.</p>}
          <div className="bars">
            {data.daily.map((d) => (
              <div key={d.day} className="bar-row">
                <span className="bar-label">{d.day.slice(5)}</span>
                <div className="bar-track">
                  <div className="bar" style={{ width: `${(d.queries / maxQueries) * 100}%` }} />
                </div>
                <span className="bar-value">
                  {d.queries} · {usd(d.costUsd)} · {ms(d.avgLatencyMs)}
                </span>
              </div>
            ))}
          </div>
        </div>
        <div className="card">
          <h2>Knowledge base</h2>
          <dl className="kv">
            <dt>Documents</dt>
            <dd>
              {data.corpus.ready} ready / {data.corpus.documents} total
              {data.corpus.failed > 0 && <span className="error-text"> · {data.corpus.failed} failed</span>}
            </dd>
            <dt>Chunks</dt>
            <dd>{num(data.corpus.chunks)}</dd>
            <dt>Indexed tokens</dt>
            <dd>{num(data.corpus.tokens)}</dd>
            <dt>Embedding spend</dt>
            <dd>{usd(data.corpus.embeddingCostUsd)}</dd>
            <dt>Tokens (in / out)</dt>
            <dd>
              {num(s.promptTokens)} / {num(s.completionTokens)}
            </dd>
          </dl>
        </div>
      </div>

      <div className="card table-wrap">
        <h2>Recent questions</h2>
        <table>
          <thead>
            <tr>
              <th>When</th>
              <th>User</th>
              <th>Question</th>
              <th>Result</th>
              <th className="num">Latency</th>
              <th className="num">Cost</th>
            </tr>
          </thead>
          <tbody>
            {data.recent.map((q) => (
              <tr key={q.id}>
                <td className="small">{dateTime(q.createdAt)}</td>
                <td>{q.username}</td>
                <td>{q.question}</td>
                <td>
                  {q.cacheHit && <span className="pill ok">cached</span>}{' '}
                  <span className={`pill ${q.grounded ? 'ok' : 'warn'}`}>{q.grounded ? 'answered' : 'no answer'}</span>
                </td>
                <td className="num">{ms(q.latencyMs)}</td>
                <td className="num">{usd(q.costUsd)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="stat card">
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}
