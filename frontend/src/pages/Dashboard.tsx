import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiGet } from '../api';
import { openAttachment } from '../components/AuthMedia';

type Summary = {
  routinesPending: number;
  routinesInProgress: number;
  routinesLate: number;
  occurrencesOpen: number;
  occurrencesAwaitingValidation: number;
  lateRuns: Array<{ id: number; status: string; dueAt: string | null }>;
  openOccurrences: Array<{ id: number; title: string; priority: string }>;
};

type Metrics = {
  completedCount: number;
  onTimeCount: number;
  onTimeRate: number;
  aging: { upTo1d: number; upTo3d: number; upTo7d: number; over7d: number };
  branchRanking: Array<{ branchId: number | null; branchName: string; openCount: number; lateCount: number }>;
};

function pad(n: number): string {
  return String(n).padStart(2, '0');
}
function firstOfMonthIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`;
}
function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export default function Dashboard() {
  const navigate = useNavigate();
  const [data, setData] = useState<Summary | null>(null);
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [from, setFrom] = useState(firstOfMonthIso());
  const [to, setTo] = useState(todayIso());

  useEffect(() => {
    apiGet('/dashboard/summary')
      .then(setData)
      .catch((e) => setError(e.message));
    apiGet('/dashboard/metrics')
      .then(setMetrics)
      .catch(() => undefined);
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

      {metrics && (
        <section className="card">
          <h2>Indicadores</h2>
          <div className="cards-grid">
            <MetricCard
              label="Conclusão no prazo"
              value={`${metrics.onTimeRate}%`}
              tone={metrics.onTimeRate >= 90 ? 'ok' : metrics.onTimeRate >= 70 ? 'warn' : 'danger'}
            />
            <MetricCard label="Concluídas (total)" value={metrics.completedCount} tone="info" />
          </div>
          <h3 className="muted small">Atrasos por tempo</h3>
          <div className="chips">
            <span className="chip">até 1d: {metrics.aging.upTo1d}</span>
            <span className="chip">1–3d: {metrics.aging.upTo3d}</span>
            <span className="chip">3–7d: {metrics.aging.upTo7d}</span>
            <span className="chip status-atrasada">+7d: {metrics.aging.over7d}</span>
          </div>
          {metrics.branchRanking.length > 0 && (
            <>
              <h3 className="muted small">Ranking por filial (atrasos)</h3>
              <ul className="list">
                {metrics.branchRanking.map((b) => (
                  <li key={String(b.branchId)}>
                    <span>{b.branchName}</span>
                    <span className="chip status-atrasada">{b.lateCount} atrasadas · {b.openCount} abertas</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}

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

      <section className="card">
        <h2>Relatório do período (PDF)</h2>
        <div className="time-row">
          <div className="field-block">
            <label className="field-label">De</label>
            <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="field-block">
            <label className="field-label">Até</label>
            <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        <button
          type="button"
          className="btn-ghost"
          disabled={!from || !to || from > to}
          onClick={() => openAttachment(`/dashboard/report.pdf?from=${from}&to=${to}`)}
        >
          Baixar relatório (PDF)
        </button>
        <p className="muted small">Rotinas agendadas e ocorrências abertas no período, com indicadores e atrasos.</p>
      </section>
    </div>
  );
}

function MetricCard({ label, value, tone }: { label: string; value: number | string; tone: string }) {
  return (
    <div className={`metric-card tone-${tone}`}>
      <span className="metric-value">{value}</span>
      <span className="metric-label">{label}</span>
    </div>
  );
}
