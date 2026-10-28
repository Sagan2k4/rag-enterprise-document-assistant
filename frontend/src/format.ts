export const ms = (v: number) => (v >= 1000 ? `${(v / 1000).toFixed(2)} s` : `${Math.round(v)} ms`);

export const usd = (v: number) => (v === 0 ? '$0' : v < 0.01 ? `$${v.toFixed(5)}` : `$${v.toFixed(2)}`);

export const pct = (v: number) => `${Math.round(v * 100)}%`;

export const num = (v: number) => v.toLocaleString('en-US');

export const bytes = (v: number) =>
  v < 1024 ? `${v} B` : v < 1024 * 1024 ? `${(v / 1024).toFixed(1)} KB` : `${(v / 1024 / 1024).toFixed(1)} MB`;

export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
