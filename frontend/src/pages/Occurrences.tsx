import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiBlob, apiGet, apiPost, apiUpload } from '../api';
import { qualityAnalysisPath } from '../fuel';
import { useI18n } from '../i18n';

type Occurrence = {
  id: number;
  title: string;
  description: string;
  status: string;
  priority: string;
  kind?: string;
  fuelLabel?: string;
  stationName?: string;
  collectionDate?: string;
  filledByName?: string;
};

const OCC_FILTERS: Array<{ value: string; key: string }> = [
  { value: '', key: 'occ.filter.all' },
  { value: 'ABERTA', key: 'occ.filter.open' },
  { value: 'EM_ATENDIMENTO', key: 'occ.filter.inService' },
  { value: 'AGUARDANDO_VALIDACAO', key: 'occ.filter.awaiting' },
  { value: 'ENCERRADA', key: 'occ.filter.closed' },
  { value: 'REJEITADA', key: 'occ.filter.rejected' }
];

export default function Occurrences() {
  const navigate = useNavigate();
  const { t } = useI18n();
  const cameraRef = useRef<HTMLInputElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const [items, setItems] = useState<Occurrence[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState('MEDIA');
  const [status, setStatus] = useState('');
  const [pendingFiles, setPendingFiles] = useState<File[]>([]);

  async function reload() {
    try {
      const q = status ? `?status=${status}` : '';
      setItems(await apiGet(`/occurrences${q}`));
    } catch (e) {
      setError(e instanceof Error ? e.message : t('common.loadError'));
    }
  }

  useEffect(() => {
    reload();
  }, [status]);

  function addPending(file: File | undefined) {
    if (!file) return;
    setPendingFiles((prev) => [...prev, file]);
  }

  function removePending(index: number) {
    setPendingFiles((prev) => prev.filter((_, i) => i !== index));
  }

  async function exportCsv() {
    setError(null);
    try {
      const q = status ? `?status=${status}` : '';
      const blob = await apiBlob(`/occurrences/export.csv${q}`);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'ocorrencias.csv';
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
    } catch (e) {
      setError(e instanceof Error ? e.message : t('routines.err.export'));
    }
  }

  async function create(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      const created = await apiPost('/occurrences', { title, description, priority });
      const id = created?.id as number | undefined;
      if (id != null && pendingFiles.length > 0) {
        for (const file of pendingFiles) {
          await apiUpload(`/occurrences/${id}/attachments`, file);
        }
      }
      setTitle('');
      setDescription('');
      setPriority('MEDIA');
      setPendingFiles([]);
      await reload();
    } catch (e) {
      setError(e instanceof Error ? e.message : t('occ.err.create'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      {error && <div className="alert-error">{error}</div>}

      <section className="card">
        <h2>{t('occ.new')}</h2>
        <button type="button" className="btn-primary" onClick={() => navigate(qualityAnalysisPath())}>
          {t('occ.qualityAnalysis')}
        </button>
        <form onSubmit={create} className="stack" style={{ marginTop: 12 }}>
          <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder={t('occ.title.placeholder')} required />
          <textarea value={description} onChange={(e) => setDescription(e.target.value)} placeholder={t('occ.desc.placeholder')} required />
          <select value={priority} onChange={(e) => setPriority(e.target.value)}>
            <option value="BAIXA">{t('prio.BAIXA')}</option>
            <option value="MEDIA">{t('prio.MEDIA')}</option>
            <option value="ALTA">{t('prio.ALTA')}</option>
            <option value="CRITICA">{t('prio.CRITICA')}</option>
          </select>

          <label className="field-label">{t('occ.evidence')}</label>
          <div className="upload-row">
            <button type="button" className="btn-ghost" disabled={busy} onClick={() => cameraRef.current?.click()}>
              {t('occ.takePhoto')}
            </button>
            <button type="button" className="btn-ghost" disabled={busy} onClick={() => fileRef.current?.click()}>
              {t('occ.attachFile')}
            </button>
            <input
              ref={cameraRef}
              type="file"
              accept="image/*"
              capture="environment"
              hidden
              onChange={(e) => {
                addPending(e.target.files?.[0]);
                e.target.value = '';
              }}
            />
            <input
              ref={fileRef}
              type="file"
              accept="image/*,application/pdf"
              hidden
              onChange={(e) => {
                addPending(e.target.files?.[0]);
                e.target.value = '';
              }}
            />
          </div>
          {pendingFiles.length > 0 && (
            <ul className="pending-files">
              {pendingFiles.map((f, i) => (
                <li key={`${f.name}-${i}`}>
                  <span className="pending-name">{f.name}</span>
                  <button type="button" className="btn-ghost danger" disabled={busy} onClick={() => removePending(i)}>
                    {t('common.remove')}
                  </button>
                </li>
              ))}
            </ul>
          )}

          <button type="submit" className="btn-primary" disabled={busy}>
            {busy ? t('occ.opening') : t('occ.open')}
          </button>
        </form>
      </section>

      <section className="card">
        <div className="row-between">
          <h2>{t('occ.list')}</h2>
          <button type="button" className="btn-ghost" onClick={exportCsv}>{t('routines.exportCsv')}</button>
        </div>
        <div className="filter-row">
          {OCC_FILTERS.map((f) => (
            <button
              key={f.value}
              type="button"
              className={`filter-chip ${status === f.value ? 'active' : ''}`}
              onClick={() => setStatus(f.value)}
            >
              {t(f.key)}
            </button>
          ))}
        </div>
        {items.length === 0 ? (
          <p className="muted">{t('occ.noOccurrences')}</p>
        ) : (
          <ul className="list">
            {items.map((item) => (
              <li key={item.id} className="clickable" onClick={() => navigate(`/occurrences/${item.id}`)}>
                <div>
                  <strong>{item.title}</strong>
                  <div className="muted small">
                    {[item.stationName, item.collectionDate, item.filledByName].filter(Boolean).join(' · ') || item.description}
                  </div>
                  <div className="chips">
                    <span className={`chip status-${item.status.toLowerCase()}`}>{t('ostatus.' + item.status)}</span>
                    {item.kind === 'FUEL_QUALITY_RECEIPT' && <span className="chip">{t('occ.qualityChip')}</span>}
                    {item.fuelLabel && <span className="chip">{item.fuelLabel}</span>}
                    {item.kind !== 'FUEL_QUALITY_RECEIPT' && <span className={`chip prio-${item.priority.toLowerCase()}`}>{t('prio.' + item.priority)}</span>}
                  </div>
                </div>
                <span className="chevron">›</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
