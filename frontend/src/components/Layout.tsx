import type { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { isAdmin, useAuth } from '../auth';
import RoleBadge from './RoleBadge';

export default function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth();
  const admin = isAdmin(user);

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark" aria-hidden>
            ◆
          </span>
          Document Assistant
        </div>
        <nav className="nav">
          <NavLink to="/" end>
            Ask
          </NavLink>
          <NavLink to="/documents">Documents</NavLink>
          {admin && <NavLink to="/insights">Insights</NavLink>}
          {admin && <NavLink to="/evaluation">Evaluation</NavLink>}
        </nav>
        <div className="user">
          <div className="user-meta">
            <span className="user-name">{user?.fullName ?? user?.username}</span>
            <span className="user-roles">
              {user?.roles.map((r) => <RoleBadge key={r} role={r} />)}
            </span>
          </div>
          <button className="btn ghost small" onClick={logout}>
            Sign out
          </button>
        </div>
      </header>
      <main className="content">{children}</main>
    </div>
  );
}
