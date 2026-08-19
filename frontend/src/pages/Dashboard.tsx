import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiGet } from '../api';
import { openAttachment } from '../components/AuthMedia';
import { useI18n } from '../i18n';

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
  const { t } = useI18n();
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
  if (!data) return <div className="muted">{t('dashboard.loading')}</div>;

  const hasAlerts = data.routinesLate > 0 || data.occurrencesOpen > 0 || data.lateRuns.length > 0;

  return (
    <div className="page">
      <div className={`status-banner ${hasAlerts ? 'warn' : 'ok'}`}>
        {hasAlerts ? t('dashboard.needsAttention') : t('dashboard.allGood')}
      </div>

      <div className="cards-grid">
        <MetricCard label={t('dashboard.pending')} value={data.routinesPending} tone="info" />
        <MetricCard label={t('dashboard.inProgress')} value={data.routinesInProgress} tone="info" />
        <MetricCard label={t('dashboard.late')} value={data.routinesLate} tone="danger" />
        <MetricCard label={t('dashboard.occOpen')} value={data.occurrencesOpen} tone="warn" />
        <MetricCard label={t('dashboard.awaiting')} value={data.occurrencesAwaitingValidation} tone="warn" />
      </div>

      {metrics && (
        <section className="card">
          <h2>{t('dashboard.indicators')}</h2>
          <div className="cards-grid">
            <MetricCard
              label={t('dashboard.onTimeRate')}
              value={`${metrics.onTimeRate}%`}
              tone={metrics.onTimeRate >= 90 ? 'ok' : metrics.onTimeRate >= 70 ? 'warn' : 'danger'}
            />
            <MetricCard label={t('dashboard.completedTotal')} value={metrics.completedCount} tone="info" />
          </div>
          <h3 className="muted small">{t('dashboard.agingTitle')}</h3>
          <div className="chips">
            <span className="chip">≤1d: {metrics.aging.upTo1d}</span>
            <span className="chip">1–3d: {metrics.aging.upTo3d}</span>
            <span className="chip">3–7d: {metrics.aging.upTo7d}</span>
            <span className="chip status-atrasada">+7d: {metrics.aging.over7d}</span>
          </div>
          {metrics.branchRanking.length > 0 && (
            <>
              <h3 className="muted small">{t('dashboard.branchRanking')}</h3>
              <ul className="list">
                {metrics.branchRanking.map((b) => (
                  <li key={String(b.branchId)}>
                    <span>{b.branchName}</span>
                    <span className="chip status-atrasada">{b.lateCount} {t('dashboard.lateAbbr')} · {b.openCount} {t('dashboard.openAbbr')}</span>
                  </li>
                ))}
              </ul>
            </>
          )}
        </section>
      )}

      <section className="card">
        <h2>{t('dashboard.openOccurrences')}</h2>
        {data.openOccurrences.length === 0 ? (
          <p className="muted">{t('dashboard.noOpenOcc')}</p>
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
        <h2>{t('dashboard.lateTasks')}</h2>
        {data.lateRuns.length === 0 ? (
          <p className="muted">{t('dashboard.noLateTasks')}</p>
        ) : (
          <ul className="list">
            {data.lateRuns.map((r) => (
              <li key={r.id} className="clickable" onClick={() => navigate(`/routines/${r.id}`)}>
                <span>{t('dashboard.task')} #{r.id}</span>
                <span className="chevron">›</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <h2>{t('dashboard.reportTitle')}</h2>
        <div className="time-row">
          <div className="field-block">
            <label className="field-label">{t('dashboard.from')}</label>
            <input type="date" value={from} max={to} onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="field-block">
            <label className="field-label">{t('dashboard.to')}</label>
            <input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        <button
          type="button"
          className="btn-ghost"
          disabled={!from || !to || from > to}
          onClick={() => openAttachment(`/dashboard/report.pdf?from=${from}&to=${to}`)}
        >
          {t('dashboard.downloadReport')}
        </button>
        <p className="muted small">{t('dashboard.reportHint')}</p>
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
