import { useEffect, useState } from 'react';
import { api } from '../api';
import RoleBadge from '../components/RoleBadge';
import { dateTime, ms, pct, usd } from '../format';
import type { EvalRunDetail, EvalSummary } from '../types';

export default function EvaluationPage() {
  const [runs, setRuns] = useState<EvalSummary[]>([]);
  const [detail, setDetail] = useState<EvalRunDetail | null>(null);
  const [judge, setJudge] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadRuns = () =>
    api
      .evalRuns()
      .then((r) => {
        setRuns(r);
        return r;
      })
      .catch((e) => {
        setError(e.message);
        return [] as EvalSummary[];
      });

  useEffect(() => {
    loadRuns().then((r) => {
      if (r.length) api.evalRun(r[0].id).then(setDetail);
    });
  }, []);

  const run = async () => {
    setRunning(true);
    setError(null);
    try {
      const result = await api.runEval(judge);
      setDetail(result);
      loadRuns();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Evaluation failed');
    } finally {
      setRunning(false);
    }
  };

  const s = detail?.summary;

  return (
    <div className="page">
      <div className="page-head row">
        <div>
          <h1>Evaluation</h1>
          <p className="muted">
            Runs the golden dataset through the live pipeline: retrieval quality, answer accuracy, citations, refusal
            behaviour and permission leakage.
          </p>
        </div>
        <div className="row gap">
          <label className="check">
            <input type="checkbox" checked={judge} onChange={(e) => setJudge(e.target.checked)} />
            LLM-as-judge faithfulness
          </label>
          <button className="btn primary" onClick={run} disabled={running}>
            {running ? 'Running…' : 'Run evaluation'}
          </button>
        </div>
      </div>
      {error && <div className="alert error">{error}</div>}

      {s && (
        <div className="stats">
          <Metric label="Retrieval hit rate" value={pct(s.hitRate)} hint="expected doc in top-k" />
          <Metric label="MRR" value={s.mrr.toFixed(2)} hint="mean reciprocal rank" />
          <Metric label="Keyword recall" value={pct(s.keywordRecall)} hint="expected facts in answer" />
          <Metric label="Citation rate" value={pct(s.citationRate)} hint="answers citing a source" />
          <Metric label="Refusal accuracy" value={pct(s.refusalAccuracy)} hint="declines when it should" />
          <Metric
            label="RBAC leaks"
            value={String(s.leakageCount)}
            hint="forbidden docs retrieved"
            bad={s.leakageCount > 0}
          />
          {s.faithfulness != null && <Metric label="Faithfulness" value={`${s.faithfulness.toFixed(2)} / 5`} />}
          <Metric label="Avg latency" value={ms(s.avgLatencyMs)} hint={`total cost ${usd(s.totalCostUsd)}`} />
        </div>
      )}

      {detail && (
        <div className="card table-wrap">
          <h2>
            Run #{detail.summary.id} · {detail.cases.filter((c) => c.passed).length}/{detail.cases.length} passed
          </h2>
          <table>
            <thead>
              <tr>
                <th>Case</th>
                <th>As</th>
                <th>Expect</th>
                <th>Retrieved</th>
                <th>Answer</th>
                <th>Result</th>
              </tr>
            </thead>
            <tbody>
              {detail.cases.map((c) => (
                <tr key={c.id}>
                  <td>
                    <div className="strong">{c.id}</div>
                    <div className="small muted">{c.question}</div>
                  </td>
                  <td>
                    <RoleBadge role={c.asRole} />
                  </td>
                  <td>{c.shouldAnswer ? 'answer' : 'refuse'}</td>
                  <td className="small">{c.retrievedDocuments.join(', ') || '—'}</td>
                  <td className="small answer-cell">{c.answer}</td>
                  <td>
                    <span className={`pill ${c.passed ? 'ok' : 'bad'}`}>{c.passed ? 'pass' : 'fail'}</span>
                    {c.leaked && <span className="pill bad">leak</span>}
                    <div className="small muted">
                      {c.shouldAnswer && `recall ${pct(c.keywordRecall)} · RR ${c.reciprocalRank.toFixed(2)}`}
                      {c.faithfulness != null && ` · faith ${c.faithfulness}`}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {runs.length > 0 && (
        <div className="card table-wrap">
          <h2>History</h2>
          <table>
            <thead>
              <tr>
                <th>Run</th>
                <th>When</th>
                <th className="num">Hit rate</th>
                <th className="num">MRR</th>
                <th className="num">Recall</th>
                <th className="num">Citations</th>
                <th className="num">Refusals</th>
                <th className="num">Leaks</th>
                <th className="num">Cost</th>
              </tr>
            </thead>
            <tbody>
              {runs.map((r) => (
                <tr key={r.id} className="clickable" onClick={() => api.evalRun(r.id).then(setDetail)}>
                  <td>#{r.id}</td>
                  <td className="small">{dateTime(r.createdAt)}</td>
                  <td className="num">{pct(r.hitRate)}</td>
                  <td className="num">{r.mrr.toFixed(2)}</td>
                  <td className="num">{pct(r.keywordRecall)}</td>
                  <td className="num">{pct(r.citationRate)}</td>
                  <td className="num">{pct(r.refusalAccuracy)}</td>
                  <td className="num">{r.leakageCount}</td>
                  <td className="num">{usd(r.totalCostUsd)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function Metric({ label, value, hint, bad }: { label: string; value: string; hint?: string; bad?: boolean }) {
  return (
    <div className={`stat card ${bad ? 'stat-bad' : ''}`}>
      <div className="stat-label">{label}</div>
      <div className="stat-value">{value}</div>
      {hint && <div className="stat-hint">{hint}</div>}
    </div>
  );
}
