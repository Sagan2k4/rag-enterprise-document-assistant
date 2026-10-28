import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api } from '../api';
import { isAdmin, useAuth } from '../auth';
import RoleBadge from '../components/RoleBadge';
import { bytes, dateTime, num, usd } from '../format';
import { ALL_ROLES, type DocumentInfo, type Role } from '../types';

const ACCEPT = '.pdf,.docx,.md,.markdown,.html,.htm,.txt,.pptx,.rtf,.odt';

export default function DocumentsPage() {
  const { user } = useAuth();
  const admin = isAdmin(user);
  const [docs, setDocs] = useState<DocumentInfo[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => api.documents().then(setDocs).catch((e) => setError(e.message)), []);

  useEffect(() => {
    load();
  }, [load]);

  // Poll while anything is still being ingested.
  const inFlight = docs.some((d) => d.status === 'PENDING' || d.status === 'PROCESSING');
  useEffect(() => {
    if (!inFlight) return;
    const t = setInterval(load, 2500);
    return () => clearInterval(t);
  }, [inFlight, load]);

  const remove = async (doc: DocumentInfo) => {
    if (!window.confirm(`Delete "${doc.title}" and all its chunks?`)) return;
    try {
      await api.deleteDocument(doc.id);
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Delete failed');
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <h1>Documents</h1>
        <p className="muted">
          {admin ? 'All documents in the knowledge base.' : `Documents visible to your roles (${user?.roles.join(', ')}).`}
        </p>
      </div>

      {admin && <IngestPanel onDone={load} onError={setError} />}
      {error && (
        <div className="alert error" onClick={() => setError(null)}>
          {error}
        </div>
      )}

      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>Title</th>
              <th>Type</th>
              <th>Access</th>
              <th>Status</th>
              <th className="num">Chunks</th>
              <th className="num">Tokens</th>
              <th className="num">Size</th>
              <th>Added</th>
              {admin && <th />}
            </tr>
          </thead>
          <tbody>
            {docs.map((d) => (
              <tr key={d.id}>
                <td>
                  <div className="strong">{d.title}</div>
                  <div className="muted small">
                    {d.sourceUrl ? (
                      <a href={d.sourceUrl} target="_blank" rel="noreferrer">
                        {d.sourceUrl}
                      </a>
                    ) : (
                      d.filename
                    )}
                  </div>
                </td>
                <td>
                  <span className="tag">{d.sourceType}</span>
                </td>
                <td>
                  <span className="roles">
                    {d.allowedRoles.map((r) => (
                      <RoleBadge key={r} role={r} />
                    ))}
                  </span>
                </td>
                <td>
                  <span className={`status status-${d.status.toLowerCase()}`} title={d.errorMessage ?? undefined}>
                    {d.status}
                  </span>
                  {d.errorMessage && <div className="small error-text">{d.errorMessage}</div>}
                </td>
                <td className="num">{num(d.chunkCount)}</td>
                <td className="num" title={`embedding cost ${usd(d.embeddingCostUsd)}`}>
                  {num(d.tokenCount)}
                </td>
                <td className="num">{bytes(d.sizeBytes)}</td>
                <td className="small">{dateTime(d.createdAt)}</td>
                {admin && (
                  <td>
                    <button className="btn ghost small danger" onClick={() => remove(d)}>
                      Delete
                    </button>
                  </td>
                )}
              </tr>
            ))}
            {docs.length === 0 && (
              <tr>
                <td colSpan={9} className="muted center">
                  No documents yet.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function IngestPanel({ onDone, onError }: { onDone: () => void; onError: (msg: string) => void }) {
  const [mode, setMode] = useState<'file' | 'wiki'>('file');
  const [file, setFile] = useState<File | null>(null);
  const [url, setUrl] = useState('');
  const [title, setTitle] = useState('');
  const [roles, setRoles] = useState<Role[]>(['EMPLOYEE']);
  const [busy, setBusy] = useState(false);

  const toggle = (r: Role) => setRoles((cur) => (cur.includes(r) ? cur.filter((x) => x !== r) : [...cur, r]));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (roles.length === 0) return onError('Select at least one role that may read this document.');
    setBusy(true);
    try {
      if (mode === 'file' && file) await api.upload(file, title, roles);
      if (mode === 'wiki' && url) await api.importWiki(url, title, roles);
      setFile(null);
      setUrl('');
      setTitle('');
      (e.target as HTMLFormElement).reset();
      onDone();
    } catch (err) {
      onError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card ingest" onSubmit={submit}>
      <div className="tabs">
        <button type="button" className={mode === 'file' ? 'active' : ''} onClick={() => setMode('file')}>
          Upload file
        </button>
        <button type="button" className={mode === 'wiki' ? 'active' : ''} onClick={() => setMode('wiki')}>
          Import wiki page
        </button>
      </div>
      <div className="ingest-grid">
        {mode === 'file' ? (
          <label>
            File (PDF, DOCX, Markdown, HTML, TXT…)
            <input type="file" accept={ACCEPT} onChange={(e) => setFile(e.target.files?.[0] ?? null)} required />
          </label>
        ) : (
          <label>
            Page URL
            <input
              type="url"
              placeholder="https://wiki.example.com/page"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              required
            />
          </label>
        )}
        <label>
          Title (optional)
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Defaults to the file name" />
        </label>
      </div>
      <fieldset className="role-picker">
        <legend>Who can read it?</legend>
        {ALL_ROLES.filter((r) => r !== 'ADMIN').map((r) => (
          <label key={r} className="check">
            <input type="checkbox" checked={roles.includes(r)} onChange={() => toggle(r)} />
            <RoleBadge role={r} />
          </label>
        ))}
        <span className="muted small">Admins can always read everything.</span>
      </fieldset>
      <button className="btn primary" disabled={busy}>
        {busy ? 'Uploading…' : 'Add to knowledge base'}
      </button>
    </form>
  );
}
