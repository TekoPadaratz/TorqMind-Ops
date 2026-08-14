import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { apiGet, apiPost } from '../api';

type Notification = {
  id: number;
  title: string;
  body: string;
  createdAt: string;
  readAt: string | null;
};

export default function Notifications() {
  const navigate = useNavigate();
  const location = useLocation();
  const [items, setItems] = useState<Notification[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        const list = await apiGet('/notifications');
        if (!active) return;
        setItems(list);
        // Marca como lidas ao abrir (não apaga); limpa o badge do sino
        await apiPost('/notifications/mark-read', {});
        window.dispatchEvent(new Event('torqmind:notifications-read'));
      } catch (e) {
        if (active) setError(e instanceof Error ? e.message : 'Erro ao carregar');
      }
    })();
    return () => {
      active = false;
    };
  }, [location.key]);

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
              <li key={n.id} className={`run-item ${n.readAt ? '' : 'notif-unread'}`}>
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
