import { useState } from 'react';
import { useOrg } from '../auth/OrgContext';
import { ErrorNotice } from '../components/ErrorNotice';
import { ApiError, updateOrganizationName } from '../services/api';
import type { FriendlyError } from '../types';

interface Props {
  onBack: () => void;
}

/**
 * Configurações > Organização (Sprint 7 §38). Nome editável **somente por
 * OWNER**; slug é somente leitura nesta sprint.
 */
export function OrganizationSettings({ onBack }: Props) {
  const { organization, loading, refresh } = useOrg();
  const [name, setName] = useState('');
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<FriendlyError | null>(null);

  const isOwner = organization?.role === 'OWNER';
  const value = dirty ? name : (organization?.name ?? '');

  async function save() {
    if (!value.trim() || value.trim() === organization?.name) return;
    setSaving(true);
    setError(null);
    setSaved(false);
    try {
      await updateOrganizationName(value.trim());
      await refresh();
      setDirty(false);
      setSaved(true);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { code: err.code, message: err.message }
          : { code: 'UNKNOWN', message: 'Nao foi possivel salvar as alteracoes.' },
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col gap-5 p-6">
      <button type="button" onClick={onBack} className="self-start text-sm text-slate-400 hover:text-slate-200">
        ← Voltar
      </button>
      <h1 className="text-2xl font-semibold text-slate-100">Organizacao</h1>

      {loading && <p className="text-sm text-slate-400">Carregando...</p>}

      {organization && (
        <>
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-slate-300">Nome</span>
            <input
              className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100 disabled:opacity-60"
              value={value}
              disabled={!isOwner || saving}
              onChange={(e) => {
                setName(e.target.value);
                setDirty(true);
                setSaved(false);
              }}
            />
          </label>

          <label className="block text-sm">
            <span className="mb-1 block font-medium text-slate-300">Slug</span>
            <input
              className="w-full rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-slate-400"
              value={organization.slug}
              readOnly
            />
          </label>

          {!isOwner && (
            <p className="text-xs text-slate-500">Apenas o OWNER pode alterar o nome da Organizacao.</p>
          )}

          {error && <ErrorNotice error={error} />}
          {saved && <p className="text-sm text-emerald-400">Alteracoes salvas.</p>}

          {isOwner && (
            <button
              type="button"
              onClick={() => void save()}
              disabled={saving || !dirty || !value.trim() || value.trim() === organization.name}
              className="rounded-lg bg-indigo-600 px-4 py-2 font-semibold text-white hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {saving ? 'Salvando...' : 'Salvar'}
            </button>
          )}
        </>
      )}
    </div>
  );
}
