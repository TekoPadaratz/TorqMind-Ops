import React, { useEffect, useState } from 'react';
import { apiGet, apiPut } from '../api';

type Company = { id: number; name: string };

type Settings = {
  companyId: number;
  requirePhotoOnComplete: boolean;
  requireCommentOnComplete: boolean;
  defaultReminderMinutes: number;
  checklistsEnabled: boolean;
};

type Props = {
  companies: Company[];
  selectedCompany: number | '';
  onCompanyChange: (id: number) => void;
  onOk: (msg: string) => void;
  onError: (e: unknown) => void;
};

export default function AdminSettings({ companies, selectedCompany, onCompanyChange, onOk, onError }: Props) {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (typeof selectedCompany !== 'number') return;
    setSettings(null);
    apiGet(`/admin/companies/${selectedCompany}/settings`)
      .then(setSettings)
      .catch(onError);
  }, [selectedCompany]);

  async function save() {
    if (!settings || typeof selectedCompany !== 'number') return;
    setSaving(true);
    try {
      const next = await apiPut(`/admin/companies/${selectedCompany}/settings`, {
        requirePhotoOnComplete: settings.requirePhotoOnComplete,
        requireCommentOnComplete: settings.requireCommentOnComplete,
        defaultReminderMinutes: settings.defaultReminderMinutes,
        checklistsEnabled: settings.checklistsEnabled
      });
      setSettings(next);
      onOk('Configurações salvas.');
    } catch (e) {
      onError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card">
      <h2>Configurações da operação</h2>
      <p className="muted small">Padrões usados quando não ditos no comando de voz. Só o administrador altera.</p>
      <label className="field-label">Empresa
        <select
          value={selectedCompany === '' ? '' : selectedCompany}
          onChange={(e) => onCompanyChange(Number(e.target.value))}
        >
          {companies.map((c) => (
            <option key={c.id} value={c.id}>{c.name}</option>
          ))}
        </select>
      </label>
      {settings && (
        <div className="stack">
          <label className="check">
            <input
              type="checkbox"
              checked={settings.requirePhotoOnComplete}
              onChange={(e) => setSettings({ ...settings, requirePhotoOnComplete: e.target.checked })}
            />
            Exigir foto para concluir
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={settings.requireCommentOnComplete}
              onChange={(e) => setSettings({ ...settings, requireCommentOnComplete: e.target.checked })}
            />
            Exigir comentário para concluir
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={settings.checklistsEnabled}
              onChange={(e) => setSettings({ ...settings, checklistsEnabled: e.target.checked })}
            />
            Habilitar checklists nas tarefas
          </label>
          <label className="field-label">Lembrete padrão (minutos antes)
            <input
              type="number"
              min={0}
              max={1440}
              value={settings.defaultReminderMinutes}
              onChange={(e) => setSettings({ ...settings, defaultReminderMinutes: Number(e.target.value) })}
            />
          </label>
          <button className="btn-primary" type="button" onClick={save} disabled={saving}>
            {saving ? 'Salvando...' : 'Salvar configurações'}
          </button>
        </div>
      )}
    </section>
  );
}
