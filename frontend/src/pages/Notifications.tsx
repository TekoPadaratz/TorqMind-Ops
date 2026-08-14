import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
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
  const [items, setItems] = useState<Notification[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiPost('/notifications/read-all', {})
      .then(() => {
        window.dispatchEvent(new Event('torqmind:notifications-read'));
        return apiGet('/notifications');
      })
      .then(setItems)
      .catch((e) => setError(e.message));
  }, []);

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
        <h2>Notificações</h2>
        {items.length === 0 ? (
          <p className="muted">Nenhuma notificação por aqui.</p>
        ) : (
          <ul className="list">
            {items.map((n) => (
              <li
                key={n.id}
                className="run-item clickable"
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
