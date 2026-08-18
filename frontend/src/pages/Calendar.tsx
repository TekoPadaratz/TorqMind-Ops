import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiGet } from '../api';

type CalRun = {
  id: number;
  title: string;
  status: string;
  scheduledFor: string;
  dueAt: string | null;
  assignee: string | null;
};

const STATUS_LABEL: Record<string, string> = {
  PENDENTE: 'Pendente',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDA: 'Concluída',
  ATRASADA: 'Atrasada',
  REJEITADA: 'Rejeitada'
};

const MONTHS = [
  'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
  'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
];

function ymd(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export default function Calendar() {
  const navigate = useNavigate();
  const [cursor, setCursor] = useState(() => {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1);
  });
  const [runs, setRuns] = useState<CalRun[]>([]);
  const [error, setError] = useState<string | null>(null);

  const monthStart = useMemo(() => new Date(cursor.getFullYear(), cursor.getMonth(), 1), [cursor]);
  const monthEnd = useMemo(() => new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0), [cursor]);

  useEffect(() => {
    setError(null);
    apiGet(`/routines/runs/calendar?from=${ymd(monthStart)}&to=${ymd(monthEnd)}`)
      .then(setRuns)
      .catch((e) => setError(e instanceof Error ? e.message : 'Erro ao carregar'));
  }, [monthStart, monthEnd]);

  const byDay = useMemo(() => {
    const map = new Map<string, CalRun[]>();
    for (const r of runs) {
      const key = ymd(new Date(r.scheduledFor));
      if (!map.has(key)) map.set(key, []);
      map.get(key)!.push(r);
    }
    return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0]));
  }, [runs]);

  function shift(delta: number) {
    setCursor((c) => new Date(c.getFullYear(), c.getMonth() + delta, 1));
  }

  return (
    <div className="page">
      <button className="btn-ghost back" onClick={() => navigate(-1)}>← Voltar</button>
      <section className="card">
        <div className="row-between">
          <button className="btn-ghost" type="button" onClick={() => shift(-1)} aria-label="Mês anterior">‹</button>
          <h2>{MONTHS[cursor.getMonth()]} {cursor.getFullYear()}</h2>
          <button className="btn-ghost" type="button" onClick={() => shift(1)} aria-label="Próximo mês">›</button>
        </div>
        {error && <div className="alert-error">{error}</div>}
        {byDay.length === 0 ? (
          <p className="muted">Nenhuma tarefa agendada neste mês.</p>
        ) : (
          byDay.map(([day, list]) => {
            const d = new Date(day + 'T00:00:00');
            return (
              <div key={day} className="cal-day">
                <h3 className="muted small">
                  {d.toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' })}
                </h3>
                <ul className="list">
                  {list.map((r) => (
                    <li key={r.id} className="clickable" onClick={() => navigate(`/routines/${r.id}`)}>
                      <div>
                        <strong>{r.title}</strong>
                        <div className="muted small">
                          {r.dueAt
                            ? new Date(r.dueAt).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })
                            : '—'}
                          {r.assignee ? ` · ${r.assignee}` : ''}
                        </div>
                      </div>
                      <span className={`chip status-${r.status.toLowerCase()}`}>
                        {STATUS_LABEL[r.status] ?? r.status}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            );
          })
        )}
      </section>
    </div>
  );
}
