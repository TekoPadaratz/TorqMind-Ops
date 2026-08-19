import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { apiGet, apiPost, apiPut, apiUpload } from '../api';
import { openAttachment } from '../components/AuthMedia';
import { useAuth } from '../auth';
import { FUELS, showsAehcAlcohol, showsGasolineAlcohol } from '../fuel';
import { formatCnpj, formatPersonDocument, formatPlate, todayIso } from '../masks';
import { useI18n } from '../i18n';

type Address = {
  street?: string;
  number?: string;
  complement?: string;
  neighborhood?: string;
  city?: string;
  state?: string;
  postalCode?: string;
  formatted?: string;
};

type Witness = {
  name: string;
  document: string;
  signedAt?: string | null;
  signatureAttachmentId?: number | null;
  pendingFile?: File | null;
};

type Receipt = {
  id?: number | null;
  kind?: string;
  status?: string;
  fuel?: string | null;
  fuelLabel?: string;
  stationName?: string;
  stationLegalName?: string;
  stationCnpj?: string;
  stationAddress?: Address;
  collectionDate?: string | null;
  receivedVolume?: string | null;
  distributorName?: string | null;
  distributorCnpj?: string | null;
  transporter?: string | null;
  productNfe?: string | null;
  truckPlate?: string | null;
  trailerPlate?: string | null;
  driverName?: string | null;
  driverDocument?: string | null;
  analystName?: string | null;
  appearance?: string | null;
  color?: string | null;
  specificMass20c?: string | null;
  gasolineAlcoholContent?: string | null;
  aehcAlcoholContent?: string | null;
  filledByName?: string | null;
  responsibleSignatureAttachmentId?: number | null;
  witnesses?: Witness[];
  finalizedAt?: string | null;
  documentUrl?: string | null;
  readOnly?: boolean;
};

const EMPTY: Receipt = {
  fuel: '',
  collectionDate: todayIso(),
  witnesses: [{ name: '', document: '', pendingFile: null }]
};

export default function FuelQualityOccurrencePage() {
  const [params] = useSearchParams();
  return (
    <div className="page">
      <FuelQualityForm initialFuel={params.get('fuel') || undefined} />
    </div>
  );
}

export function FuelQualityForm({
  occurrenceId,
  initialFuel
}: {
  occurrenceId?: number;
  initialFuel?: string;
}) {
  const { session } = useAuth();
  const navigate = useNavigate();
  const { t } = useI18n();
  const [form, setForm] = useState<Receipt>({ ...EMPTY, fuel: initialFuel || '' });
  const [branchId, setBranchId] = useState<number | ''>(session?.branchId ?? '');
  const [branches, setBranches] = useState<Array<{ id: number; name: string }>>([]);
  const [finalizeOnSave, setFinalizeOnSave] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [pendingEvidence, setPendingEvidence] = useState<File[]>([]);
  const [pendingSignature, setPendingSignature] = useState<File | null>(null);
  const cameraRef = useRef<HTMLInputElement>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const lockedBranch = session?.role === 'MANAGER' || session?.role === 'OPERATOR';
  const readOnly = Boolean(form.readOnly);

  useEffect(() => {
    apiGet('/catalog/branches')
      .then((list: Array<{ id: number; name: string }>) => {
        setBranches(list);
        if (!lockedBranch && branchId === '' && list.length === 1) {
          setBranchId(list[0].id);
        }
      })
      .catch((e) => setError(e instanceof Error ? e.message : t('fq.errLoadStations')));
  }, []);

  useEffect(() => {
    if (occurrenceId) {
      apiGet(`/occurrences/${occurrenceId}/quality-receipt`)
        .then((data: Receipt) => {
          setForm({
            ...EMPTY,
            ...data,
            witnesses: data.witnesses && data.witnesses.length ? data.witnesses : EMPTY.witnesses
          });
        })
        .catch((e) => setError(e instanceof Error ? e.message : t('fq.errLoadAnalysis')));
      return;
    }
    if (typeof branchId !== 'number') return;
    const q = `?branchId=${branchId}`;
    apiGet(`/occurrences/quality-receipts/defaults${q}`)
      .then((data: Receipt) => {
        setForm((prev) => ({
          ...EMPTY,
          ...data,
          fuel: prev.fuel || initialFuel || data.fuel || '',
          collectionDate: data.collectionDate || todayIso(),
          witnesses: [{ name: '', document: '', pendingFile: null }]
        }));
      })
      .catch((e) => setError(e instanceof Error ? e.message : t('fq.errFindStation')));
  }, [occurrenceId, branchId]);

  function patch<K extends keyof Receipt>(key: K, value: Receipt[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function payload(extra: Partial<{ responsibleSignatureAttachmentId: number | null; witnesses: Witness[]; finalizeOnSave: boolean }>) {
    return {
      branchId: typeof branchId === 'number' ? branchId : session?.branchId,
      fuel: form.fuel,
      finalizeOnSave: extra.finalizeOnSave,
      collectionDate: form.collectionDate,
      receivedVolume: form.receivedVolume,
      distributorName: form.distributorName,
      distributorCnpj: form.distributorCnpj,
      transporter: form.transporter,
      productNfe: form.productNfe,
      truckPlate: form.truckPlate,
      trailerPlate: form.trailerPlate,
      driverName: form.driverName,
      driverDocument: form.driverDocument,
      analystName: form.analystName,
      appearance: form.appearance,
      color: form.color,
      specificMass20c: form.specificMass20c,
      gasolineAlcoholContent: form.gasolineAlcoholContent,
      aehcAlcoholContent: form.aehcAlcoholContent,
      filledByName: form.filledByName,
      responsibleSignatureAttachmentId: extra.responsibleSignatureAttachmentId ?? form.responsibleSignatureAttachmentId,
      witnesses: (extra.witnesses ?? form.witnesses ?? []).map((w) => ({
        name: w.name,
        document: w.document,
        signedAt: w.signedAt || new Date().toISOString(),
        signatureAttachmentId: w.signatureAttachmentId
      }))
    };
  }

  async function persist(body: ReturnType<typeof payload>): Promise<Receipt> {
    if (form.id) {
      return apiPut(`/occurrences/${form.id}/quality-receipt`, body);
    }
    return apiPost('/occurrences/quality-receipts', body);
  }

  async function onSave(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!form.fuel) {
      setError(t('fq.errFuelRequired'));
      return;
    }
    setBusy(true);
    try {
      const hasPending = pendingSignature || pendingEvidence.length || (form.witnesses ?? []).some((w) => w.pendingFile);
      let saved = await persist(payload({ finalizeOnSave: hasPending ? false : finalizeOnSave }));
      const id = saved.id as number;
      let signatureId = saved.responsibleSignatureAttachmentId ?? null;
      if (pendingSignature) {
        const up = await apiUpload(`/occurrences/${id}/attachments`, pendingSignature);
        signatureId = up.id;
      }
      const witnesses: Witness[] = [];
      for (const w of saved.witnesses?.length ? mergeWitness(form.witnesses, saved.witnesses) : form.witnesses ?? []) {
        let attachmentId = w.signatureAttachmentId ?? null;
        if (w.pendingFile) {
          const up = await apiUpload(`/occurrences/${id}/attachments`, w.pendingFile);
          attachmentId = up.id;
        }
        witnesses.push({
          name: w.name,
          document: w.document,
          signedAt: w.signedAt || new Date().toISOString(),
          signatureAttachmentId: attachmentId
        });
      }
      for (const file of pendingEvidence) {
        await apiUpload(`/occurrences/${id}/attachments`, file);
      }
      if (hasPending || finalizeOnSave) {
        saved = await persist(payload({
          finalizeOnSave,
          responsibleSignatureAttachmentId: signatureId,
          witnesses
        }));
      }
      setPendingEvidence([]);
      setPendingSignature(null);
      setForm({ ...EMPTY, ...saved, witnesses: saved.witnesses?.length ? saved.witnesses : EMPTY.witnesses });
      if (finalizeOnSave && saved.id) {
        navigate(`/occurrences/${saved.id}`, { replace: true });
      } else if (!occurrenceId && saved.id) {
        navigate(`/occurrences/${saved.id}`, { replace: true });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : t('fq.errSave'));
    } finally {
      setBusy(false);
    }
  }

  const dieselAlcoholHidden = form.fuel && !showsGasolineAlcohol(form.fuel) && !showsAehcAlcohol(form.fuel);
  const addr = form.stationAddress;

  return (
    <form className="stack quality-form" onSubmit={onSave}>
      <div className="card-head">
        <h2>{t('fq.title')}</h2>
      </div>
      {error && <div className="alert-error">{error}</div>}
      {form.documentUrl && (
        <button type="button" className="btn-ghost" onClick={() => openAttachment(form.documentUrl!)}>
          {t('odetail.openPdf')}
        </button>
      )}

      <section className="card form-section">
        <h3>{t('fq.stationSection')}</h3>
        {!lockedBranch && !occurrenceId && (
          <label className="field-label">{t('fq.station')}
            <select value={branchId} onChange={(e) => setBranchId(e.target.value ? Number(e.target.value) : '')} required disabled={readOnly}>
              <option value="">{t('fq.selectStation')}</option>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          </label>
        )}
        <div className="form-grid two">
          <ReadField label={t('admin.legal.legalName')} value={form.stationLegalName} />
          <ReadField label={t('admin.legal.cnpj')} value={form.stationCnpj} />
        </div>
        <ReadField label={t('fq.stationName')} value={form.stationName} />
        <ReadField label={t('fq.address')} value={addr?.formatted || [addr?.street, addr?.number, addr?.neighborhood, addr?.city, addr?.state, addr?.postalCode].filter(Boolean).join(' — ')} />
      </section>

      <section className="card form-section">
        <h3>{t('fq.fuelSection')}</h3>
        <label className="field-label">{t('fq.product')}
          <select value={form.fuel ?? ''} onChange={(e) => patch('fuel', e.target.value)} required disabled={readOnly}>
            <option value="">{t('admin.select')}</option>
            {FUELS.map((f) => <option key={f.value} value={f.value}>{f.label}</option>)}
          </select>
        </label>
      </section>

      <section className="card form-section">
        <h3>{t('fq.receivingSection')}</h3>
        <div className="form-grid two">
          <label className="field-label">{t('fq.collectionDate')}
            <input type="date" value={form.collectionDate ?? ''} onChange={(e) => patch('collectionDate', e.target.value)} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.receivedVolume')}
            <input value={form.receivedVolume ?? ''} onChange={(e) => patch('receivedVolume', e.target.value)} disabled={readOnly} inputMode="decimal" />
          </label>
          <label className="field-label">{t('fq.distributor')}
            <input value={form.distributorName ?? ''} onChange={(e) => patch('distributorName', e.target.value)} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.distributorCnpj')}
            <input value={form.distributorCnpj ?? ''} onChange={(e) => patch('distributorCnpj', formatCnpj(e.target.value))} disabled={readOnly} inputMode="numeric" />
          </label>
          <label className="field-label">{t('fq.transporter')}
            <input value={form.transporter ?? ''} onChange={(e) => patch('transporter', e.target.value)} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.productNfe')}
            <input value={form.productNfe ?? ''} onChange={(e) => patch('productNfe', e.target.value)} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.truckPlate')}
            <input value={form.truckPlate ?? ''} onChange={(e) => patch('truckPlate', formatPlate(e.target.value))} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.trailerPlate')}
            <input value={form.trailerPlate ?? ''} onChange={(e) => patch('trailerPlate', formatPlate(e.target.value))} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.driverName')}
            <input value={form.driverName ?? ''} onChange={(e) => patch('driverName', e.target.value)} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.driverDocument')}
            <input value={form.driverDocument ?? ''} onChange={(e) => patch('driverDocument', formatPersonDocument(e.target.value))} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.analyst')}
            <input value={form.analystName ?? ''} onChange={(e) => patch('analystName', e.target.value)} disabled={readOnly} />
          </label>
        </div>
      </section>

      <section className="card form-section">
        <h3>{t('fq.resultsSection')}</h3>
        <div className="form-grid two">
          <label className="field-label">{t('fq.appearance')}
            <input value={form.appearance ?? ''} onChange={(e) => patch('appearance', e.target.value)} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.color')}
            <input value={form.color ?? ''} onChange={(e) => patch('color', e.target.value)} disabled={readOnly} />
          </label>
          <label className="field-label">{t('fq.specificMass')}
            <input value={form.specificMass20c ?? ''} onChange={(e) => patch('specificMass20c', e.target.value)} disabled={readOnly} inputMode="decimal" />
          </label>
          {showsGasolineAlcohol(form.fuel) && (
            <label className="field-label">{t('fq.gasolineAlcohol')}
              <input value={form.gasolineAlcoholContent ?? ''} onChange={(e) => patch('gasolineAlcoholContent', e.target.value)} disabled={readOnly} />
            </label>
          )}
          {showsAehcAlcohol(form.fuel) && (
            <label className="field-label">{t('fq.aehcAlcohol')}
              <input value={form.aehcAlcoholContent ?? ''} onChange={(e) => patch('aehcAlcoholContent', e.target.value)} disabled={readOnly} />
            </label>
          )}
        </div>
        {dieselAlcoholHidden && <p className="na-note">{t('fq.dieselNoAlcohol')}</p>}
      </section>

      <section className="card form-section">
        <h3>{t('fq.responsibleSection')}</h3>
        <label className="field-label">{t('fq.filledBy')}
          <input value={form.filledByName ?? ''} onChange={(e) => patch('filledByName', e.target.value)} disabled={readOnly} />
        </label>
        {!readOnly && (
          <SignaturePad label={t('fq.responsibleSignature')} onCapture={setPendingSignature} />
        )}
        {(form.witnesses ?? []).map((w, i) => (
          <div key={i} className="witness-block">
            <div className="form-grid two">
              <label className="field-label">{t('fq.witness', { n: i + 1 })}
                <input value={w.name} disabled={readOnly} onChange={(e) => {
                  const next = [...(form.witnesses ?? [])];
                  next[i] = { ...w, name: e.target.value };
                  patch('witnesses', next);
                }} />
              </label>
              <label className="field-label">{t('fq.document')}
                <input value={w.document} disabled={readOnly} onChange={(e) => {
                  const next = [...(form.witnesses ?? [])];
                  next[i] = { ...w, document: formatPersonDocument(e.target.value) };
                  patch('witnesses', next);
                }} />
              </label>
            </div>
            {!readOnly && (
              <SignaturePad
                label={t('fq.witnessSignature')}
                onCapture={(file) => {
                  const next = [...(form.witnesses ?? [])];
                  next[i] = { ...w, pendingFile: file };
                  patch('witnesses', next);
                }}
              />
            )}
            {!readOnly && (form.witnesses ?? []).length > 1 && (
              <button type="button" className="btn-ghost danger" onClick={() => patch('witnesses', (form.witnesses ?? []).filter((_, idx) => idx !== i))}>
                {t('fq.removeWitness')}
              </button>
            )}
          </div>
        ))}
        {!readOnly && (
          <button type="button" className="btn-ghost" onClick={() => patch('witnesses', [...(form.witnesses ?? []), { name: '', document: '', pendingFile: null }])}>
            {t('fq.addWitness')}
          </button>
        )}

        <label className="field-label">{t('thread.attachments')}</label>
        {!readOnly && (
          <div className="upload-row">
            <button type="button" className="btn-ghost" onClick={() => cameraRef.current?.click()}>{t('occ.takePhoto')}</button>
            <button type="button" className="btn-ghost" onClick={() => fileRef.current?.click()}>{t('occ.attachFile')}</button>
            <input ref={cameraRef} type="file" accept="image/*" capture="environment" hidden onChange={(e) => { if (e.target.files?.[0]) setPendingEvidence((p) => [...p, e.target.files![0]]); e.target.value = ''; }} />
            <input ref={fileRef} type="file" accept="image/*,application/pdf" hidden onChange={(e) => { if (e.target.files?.[0]) setPendingEvidence((p) => [...p, e.target.files![0]]); e.target.value = ''; }} />
          </div>
        )}
        {pendingEvidence.length > 0 && (
          <ul className="pending-files">
            {pendingEvidence.map((f, i) => (
              <li key={`${f.name}-${i}`}>
                <span className="pending-name">{f.name}</span>
                <button type="button" className="btn-ghost danger" onClick={() => setPendingEvidence((p) => p.filter((_, idx) => idx !== i))}>{t('common.remove')}</button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card form-section">
        <h3>{t('fq.finalizeSection')}</h3>
        <label className="check">
          <input type="checkbox" checked={finalizeOnSave} disabled={readOnly} onChange={(e) => setFinalizeOnSave(e.target.checked)} />
          {t('fq.finalizeOnSave')}
        </label>
        <p className="muted small">
          {finalizeOnSave
            ? t('fq.finalizeHint')
            : t('fq.draftHint')}
        </p>
        {!readOnly && (
          <button type="submit" className="btn-primary" disabled={busy}>
            {busy ? t('fq.saving') : finalizeOnSave ? t('fq.saveFinalize') : t('fq.saveDraft')}
          </button>
        )}
      </section>
    </form>
  );
}

function ReadField({ label, value }: { label: string; value?: string | null }) {
  return (
    <label className="field-label">{label}
      <input value={value ?? ''} readOnly />
    </label>
  );
}

function SignaturePad({ label, onCapture }: { label: string; onCapture: (file: File) => void }) {
  const { t } = useI18n();
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);

  function pos(e: React.PointerEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current!;
    const rect = canvas.getBoundingClientRect();
    return { x: (e.clientX - rect.left) * (canvas.width / rect.width), y: (e.clientY - rect.top) * (canvas.height / rect.height) };
  }

  function exportPng() {
    const canvas = canvasRef.current;
    if (!canvas) return;
    canvas.toBlob((blob) => {
      if (blob) onCapture(new File([blob], `${label.replace(/\s+/g, '-').toLowerCase()}.png`, { type: 'image/png' }));
    }, 'image/png');
  }

  return (
    <div className="sig-wrap">
      <span className="field-label">{label}</span>
      <canvas
        ref={canvasRef}
        className="sig-pad"
        width={640}
        height={180}
        onPointerDown={(e) => {
          drawing.current = true;
          const ctx = canvasRef.current?.getContext('2d');
          const p = pos(e);
          if (ctx) { ctx.beginPath(); ctx.moveTo(p.x, p.y); }
          (e.target as HTMLCanvasElement).setPointerCapture(e.pointerId);
        }}
        onPointerMove={(e) => {
          if (!drawing.current) return;
          const ctx = canvasRef.current?.getContext('2d');
          const p = pos(e);
          if (ctx) { ctx.lineWidth = 2; ctx.lineCap = 'round'; ctx.strokeStyle = '#17211c'; ctx.lineTo(p.x, p.y); ctx.stroke(); }
        }}
        onPointerUp={() => { drawing.current = false; exportPng(); }}
      />
      <button
        type="button"
        className="btn-ghost"
        onClick={() => {
          const canvas = canvasRef.current;
          const ctx = canvas?.getContext('2d');
          if (canvas && ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
        }}
      >
        {t('fq.clearSignature')}
      </button>
    </div>
  );
}

function mergeWitness(current?: Witness[], saved?: Witness[]): Witness[] {
  const base = current && current.length ? current : saved ?? [];
  return base;
}
