import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, NavLink, Navigate, Outlet, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth';
import { apiGet, apiPost } from './api';
import { roleLabel } from './roles';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Routines from './pages/Routines';
import RoutineDetail from './pages/RoutineDetail';
import Occurrences from './pages/Occurrences';
import OccurrenceDetail from './pages/OccurrenceDetail';
import Admin from './pages/Admin';
import Account from './pages/Account';
import Notifications from './pages/Notifications';
import VoiceSheet from './components/VoiceSheet';
import FuelQualityOccurrencePage from './pages/FuelQualityOccurrence';
import { OfflineBadge } from './components/OfflineBadge';
import './styles.css';

function Shell() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [voiceOpen, setVoiceOpen] = useState(false);
  const isAdmin = session?.role === 'MASTER';
  const onNotifications = location.pathname === '/notifications';

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

  async function toggleNotifications() {
    if (onNotifications) {
      try {
        await apiPost('/notifications/mark-read', {});
      } catch {
        /* ignore */
      }
      setUnread(0);
      navigate(-1);
      return;
    }
    navigate('/notifications');
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1>TorqMind Ops</h1>
          <p className="muted small">
            {session.fullName?.toUpperCase()} · v{__BUILD_ID__}
          </p>
          <OfflineBadge />
        </div>
        <div className="header-actions">
          <button
            className={`bell ${onNotifications ? 'active' : ''}`}
            onClick={toggleNotifications}
            aria-label={onNotifications ? 'Fechar avisos' : 'Avisos'}
            aria-pressed={onNotifications}
          >
            <span>🔔</span>
            {unread > 0 && !onNotifications && <span className="badge">{unread}</span>}
          </button>
          <button
            className={`btn-ghost ${location.pathname === '/account' ? 'active' : ''}`}
            onClick={() => navigate('/account')}
          >
            Senha
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
      {!voiceOpen && (
        <button
          type="button"
          className="voice-fab"
          onClick={() => setVoiceOpen(true)}
          aria-label="Comando por voz"
        >
          🎤
        </button>
      )}
      <VoiceSheet open={voiceOpen} onClose={() => setVoiceOpen(false)} />
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
            <Route path="/occurrences/new/fuel-quality" element={<FuelQualityOccurrencePage />} />
            <Route path="/occurrences/:id" element={<OccurrenceDetail />} />
            <Route path="/admin" element={<AdminOnly><Admin /></AdminOnly>} />
            <Route path="/account" element={<Account />} />
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
