import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiGet } from '../api';

type Summary = {
  routinesPending: number;
  routinesInProgress: number;
  routinesLate: number;
  occurrencesOpen: number;
  occurrencesAwaitingValidation: number;
  lateRuns: Array<{ id: number; status: string; dueAt: string | null }>;
  openOccurrences: Array<{ id: number; title: string; priority: string }>;
};

export default function Dashboard() {
  const navigate = useNavigate();
  const [data, setData] = useState<Summary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet('/dashboard/summary')
      .then(setData)
      .catch((e) => setError(e.message));
  }, []);

  if (error) return <div className="alert-error">{error}</div>;
  if (!data) return <div className="muted">Carregando...</div>;

  const hasAlerts = data.routinesLate > 0 || data.occurrencesOpen > 0 || data.lateRuns.length > 0;

  return (
    <div className="page">
      <div className={`status-banner ${hasAlerts ? 'warn' : 'ok'}`}>
        {hasAlerts
          ? 'Existem pontos que precisam da sua atenção.'
          : 'Operação em dia. Nada crítico agora.'}
      </div>

      <div className="cards-grid">
        <MetricCard label="Tarefas pendentes" value={data.routinesPending} tone="info" />
        <MetricCard label="Em andamento" value={data.routinesInProgress} tone="info" />
        <MetricCard label="Tarefas atrasadas" value={data.routinesLate} tone="danger" />
        <MetricCard label="Ocorrências abertas" value={data.occurrencesOpen} tone="warn" />
        <MetricCard label="Aguardando validação" value={data.occurrencesAwaitingValidation} tone="warn" />
      </div>

      <section className="card">
        <h2>Ocorrências abertas</h2>
        {data.openOccurrences.length === 0 ? (
          <p className="muted">Nenhuma ocorrência aberta.</p>
        ) : (
          <ul className="list">
            {data.openOccurrences.map((o) => (
              <li key={o.id} className="clickable" onClick={() => navigate(`/occurrences/${o.id}`)}>
                <span>{o.title}</span>
                <span className={`chip prio-${o.priority.toLowerCase()}`}>{o.priority}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <h2>Tarefas atrasadas</h2>
        {data.lateRuns.length === 0 ? (
          <p className="muted">Nenhuma tarefa atrasada.</p>
        ) : (
          <ul className="list">
            {data.lateRuns.map((r) => (
              <li key={r.id} className="clickable" onClick={() => navigate(`/routines/${r.id}`)}>
                <span>Tarefa #{r.id}</span>
                <span className="chevron">›</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}

function MetricCard({ label, value, tone }: { label: string; value: number; tone: string }) {
  return (
    <div className={`metric-card tone-${tone}`}>
      <span className="metric-value">{value}</span>
      <span className="metric-label">{label}</span>
    </div>
  );
}
