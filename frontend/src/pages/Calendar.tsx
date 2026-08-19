import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiGet } from '../api';
import { useI18n } from '../i18n';

type CalRun = {
  id: number;
  title: string;
  status: string;
  scheduledFor: string;
  dueAt: string | null;
  assignee: string | null;
};

function ymd(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

export default function Calendar() {
  const navigate = useNavigate();
  const { t, lang } = useI18n();
  const locale = lang === 'en' ? 'en-US' : 'pt-BR';
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
      .catch((e) => setError(e instanceof Error ? e.message : t('common.loadError')));
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
      <button className="btn-ghost back" onClick={() => navigate(-1)}>{t('common.back')}</button>
      <section className="card">
        <div className="row-between">
          <button className="btn-ghost" type="button" onClick={() => shift(-1)} aria-label={t('cal.prevMonth')}>‹</button>
          <h2>{cursor.toLocaleDateString(locale, { month: 'long' })} {cursor.getFullYear()}</h2>
          <button className="btn-ghost" type="button" onClick={() => shift(1)} aria-label={t('cal.nextMonth')}>›</button>
        </div>
        {error && <div className="alert-error">{error}</div>}
        {byDay.length === 0 ? (
          <p className="muted">{t('cal.empty')}</p>
        ) : (
          byDay.map(([day, list]) => {
            const d = new Date(day + 'T00:00:00');
            return (
              <div key={day} className="cal-day">
                <h3 className="muted small">
                  {d.toLocaleDateString(locale, { weekday: 'short', day: '2-digit', month: '2-digit' })}
                </h3>
                <ul className="list">
                  {list.map((r) => (
                    <li key={r.id} className="clickable" onClick={() => navigate(`/routines/${r.id}`)}>
                      <div>
                        <strong>{r.title}</strong>
                        <div className="muted small">
                          {r.dueAt
                            ? new Date(r.dueAt).toLocaleTimeString(locale, { hour: '2-digit', minute: '2-digit' })
                            : '—'}
                          {r.assignee ? ` · ${r.assignee}` : ''}
                        </div>
                      </div>
                      <span className={`chip status-${r.status.toLowerCase()}`}>
                        {t('status.' + r.status)}
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
