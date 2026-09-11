import { useCallback, useEffect, useState } from 'react';
import { ErrorNotice } from '../components/ErrorNotice';
import { ApiError, listOrgMembers } from '../services/api';
import type { FriendlyError, OrgMember, OrgRole } from '../types';

interface Props {
  onBack: () => void;
}

const ROLE_LABEL: Record<OrgRole, string> = {
  OWNER: 'Proprietario',
  ADMIN: 'Administrador',
  MEMBER: 'Membro',
};

/**
 * Membros da Organização (Sprint 7 §39). Lista somente leitura — sem convite
 * nesta sprint.
 */
export function MembersScreen({ onBack }: Props) {
  const [members, setMembers] = useState<OrgMember[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<FriendlyError | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setMembers(await listOrgMembers());
    } catch (err) {
      setError(
        err instanceof ApiError
          ? {
              code: err.code,
              message:
                err.code === 'INSUFFICIENT_ROLE'
                  ? 'Voce nao tem permissao para ver os membros.'
                  : 'Nao foi possivel carregar os membros.',
            }
          : { code: 'UNKNOWN', message: 'Nao foi possivel carregar os membros.' },
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col gap-5 p-6">
      <button type="button" onClick={onBack} className="self-start text-sm text-slate-400 hover:text-slate-200">
        ← Voltar
      </button>
      <h1 className="text-2xl font-semibold text-slate-100">Membros</h1>

      {loading && <p className="text-sm text-slate-400">Carregando...</p>}
      {error && <ErrorNotice error={error} onRetry={load} />}

      {!loading &&
        !error &&
        members.map((m) => (
          <div
            key={m.userId}
            className="flex items-center justify-between rounded-xl border border-slate-700 bg-slate-800/60 p-4"
          >
            <div>
              <p className="font-medium text-slate-100">{m.name}</p>
              <p className="text-sm text-slate-400">{m.email}</p>
            </div>
            <span className="rounded-lg border border-slate-600 px-2.5 py-1 text-xs text-slate-300">
              {ROLE_LABEL[m.role]}
            </span>
          </div>
        ))}
    </div>
  );
}
