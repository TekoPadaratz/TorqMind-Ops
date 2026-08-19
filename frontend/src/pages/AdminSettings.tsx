import React, { useEffect, useState } from 'react';
import { apiGet, apiPut } from '../api';
import { useI18n } from '../i18n';

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
  const { t } = useI18n();
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
      onOk(t('aset.saved'));
    } catch (e) {
      onError(e);
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="card">
      <h2>{t('aset.title')}</h2>
      <p className="muted small">{t('aset.desc')}</p>
      <label className="field-label">{t('admin.company')}
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
            {t('aset.requirePhoto')}
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={settings.requireCommentOnComplete}
              onChange={(e) => setSettings({ ...settings, requireCommentOnComplete: e.target.checked })}
            />
            {t('aset.requireComment')}
          </label>
          <label className="check">
            <input
              type="checkbox"
              checked={settings.checklistsEnabled}
              onChange={(e) => setSettings({ ...settings, checklistsEnabled: e.target.checked })}
            />
            {t('aset.enableChecklists')}
          </label>
          <label className="field-label">{t('aset.defaultReminder')}
            <input
              type="number"
              min={0}
              max={1440}
              value={settings.defaultReminderMinutes}
              onChange={(e) => setSettings({ ...settings, defaultReminderMinutes: Number(e.target.value) })}
            />
          </label>
          <button className="btn-primary" type="button" onClick={save} disabled={saving}>
            {saving ? t('account.saving') : t('aset.save')}
          </button>
        </div>
      )}
    </section>
  );
}
