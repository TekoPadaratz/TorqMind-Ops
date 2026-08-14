import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, NavLink, Navigate, Outlet, Route, Routes, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth';
import { apiGet } from './api';
import { roleLabel } from './roles';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Routines from './pages/Routines';
import RoutineDetail from './pages/RoutineDetail';
import Occurrences from './pages/Occurrences';
import OccurrenceDetail from './pages/OccurrenceDetail';
import Admin from './pages/Admin';
import Notifications from './pages/Notifications';
import './styles.css';

function Shell() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const [unread, setUnread] = useState(0);
  const isAdmin = session?.role === 'MASTER';

  useEffect(() => {
    if (!session) return;
    let active = true;
    const load = () =>
      apiGet('/notifications/unread-count')
        .then((r) => active && setUnread(r.count ?? 0))
        .catch(() => undefined);
    load();
    const timer = setInterval(load, 30000);
    const onRead = () => setUnread(0);
    window.addEventListener('torqmind:notifications-read', onRead);
    return () => {
      active = false;
      clearInterval(timer);
      window.removeEventListener('torqmind:notifications-read', onRead);
    };
  }, [session]);

  if (!session) return <Navigate to="/login" replace />;

  const navCount = isAdmin ? 4 : 3;

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>TorqMind Ops</h1>
          <p className="muted small">
            {session.fullName} · {session.roleLabel || roleLabel(session.role)} · v{__BUILD_ID__}
          </p>
        </div>
        <div className="header-actions">
          <button className="bell" onClick={() => navigate('/notifications')} aria-label="Notificações">
            <span>🔔</span>
            {unread > 0 && <span className="badge">{unread}</span>}
          </button>
          <button
            className="btn-ghost"
            onClick={() => {
              logout();
              navigate('/login');
            }}
          >
            Sair
          </button>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <nav className="bottom-nav" style={{ gridTemplateColumns: `repeat(${navCount}, 1fr)` }}>
        <NavLink to="/" end>Radar</NavLink>
        <NavLink to="/routines">Rotinas</NavLink>
        <NavLink to="/occurrences">Ocorrências</NavLink>
        {isAdmin && <NavLink to="/admin">Gestão</NavLink>}
      </nav>
    </div>
  );
}

function AdminOnly({ children }: { children: React.ReactNode }) {
  const { session } = useAuth();
  if (session?.role !== 'MASTER') return <Navigate to="/" replace />;
  return <>{children}</>;
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route element={<Shell />}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/routines" element={<Routines />} />
            <Route path="/routines/:id" element={<RoutineDetail />} />
            <Route path="/occurrences" element={<Occurrences />} />
            <Route path="/occurrences/:id" element={<OccurrenceDetail />} />
            <Route path="/admin" element={<AdminOnly><Admin /></AdminOnly>} />
            <Route path="/notifications" element={<Notifications />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
