import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { apiGet, apiPost } from '../api';

type Notification = {
  id: number;
  title: string;
  body: string;
  createdAt: string;
  readAt: string | null;
  entityType: string;
  entityId: number;
};

export default function Notifications() {
  const navigate = useNavigate();
  const location = useLocation();
  const [items, setItems] = useState<Notification[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const list = await apiGet('/notifications');
        if (!active) return;
        setItems(list);
        await apiPost('/notifications/mark-read', {});
        window.dispatchEvent(new Event('torqmind:notifications-read'));
      } catch (e) {
        if (active) setError(e instanceof Error ? e.message : 'Erro ao carregar');
      }
    };
    load();
    const onRealtime = () => load();
    window.addEventListener('torqmind:realtime-notification', onRealtime);
    return () => {
      active = false;
      window.removeEventListener('torqmind:realtime-notification', onRealtime);
    };
  }, [location.key]);

  function openNotification(notification: Notification) {
    if (notification.entityType === 'ROUTINE_RUN') {
      navigate(`/routines/${notification.entityId}`);
    } else if (notification.entityType === 'OCCURRENCE') {
      navigate(`/occurrences/${notification.entityId}`);
    }
  }

  return (
    <div className="page">
      {error && <div className="alert-error">{error}</div>}
      <section className="card">
        <div className="card-head">
          <h2>Avisos</h2>
          <button type="button" className="btn-ghost" onClick={() => navigate(-1)}>
            Fechar
          </button>
        </div>
        {items.length === 0 ? (
          <p className="muted">Nenhum aviso por aqui.</p>
        ) : (
          <ul className="list">
            {items.map((n) => (
              <li
                key={n.id}
                className={`run-item clickable ${n.readAt ? '' : 'notif-unread'}`}
                onClick={() => openNotification(n)}
              >
                <div>
                  <strong>{n.title}</strong>
                  <div className="muted small">{n.body}</div>
                  <div className="muted small">{new Date(n.createdAt).toLocaleString()}</div>
                </div>
                {!n.readAt && <span className="chip status-aberta">novo</span>}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
