import React, { useEffect, useState } from 'react';
import { apiGet } from '../api';

type Notification = {
  id: number;
  title: string;
  body: string;
  createdAt: string;
  readAt: string | null;
};

export default function Notifications() {
  const [items, setItems] = useState<Notification[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet('/notifications')
      .then(setItems)
      .catch((e) => setError(e.message));
  }, []);

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
              <li key={n.id} className="run-item">
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
