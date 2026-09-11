import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice } from '../components/ErrorNotice';
import { OrgBadge } from '../components/OrgBadge';
import { ApiError, listRoomProfiles } from '../services/api';
import { parseRoomId } from '../services/roomLink';
import type { FriendlyError, RoomProfile, RoomType } from '../types';
import { RoomProfileForm } from './RoomProfileForm';

const TYPE_ICON: Record<RoomType, string> = {
  LESSON: '📚',
  CONSULTATION: '🩺',
  MEETING: '💼',
  INTERVIEW: '🧑‍💼',
  OTHER: '🎥',
};

interface Props {
  onRoomSelected: (roomId: string) => void;
  onCreateFromProfile: (profileId: string) => void;
  /** Abre o histórico — opcionalmente já filtrado por um Profile (Sprint 6 §37). */
  onOpenHistory?: (profileId?: string) => void;
  /** Abre "Meus atendimentos" (Sprint 8 §37). */
  onOpenAppointments?: () => void;
  /** Configurações da Organização (Sprint 7 §38). */
  onOpenOrgSettings?: () => void;
  /** Membros da Organização (Sprint 7 §39). */
  onOpenMembers?: () => void;
  /** Voltar para o console de Rooms (Sprint 9). */
  onBack?: () => void;
}

type View = { kind: 'list' } | { kind: 'form'; profile: RoomProfile | null };

export function RoomEntry({
  onRoomSelected,
  onCreateFromProfile,
  onOpenHistory,
  onOpenAppointments,
  onOpenOrgSettings,
  onOpenMembers,
  onBack,
}: Props) {
  const { user, logout } = useAuth();
  const [profiles, setProfiles] = useState<RoomProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [view, setView] = useState<View>({ kind: 'list' });
  const [joinInput, setJoinInput] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setProfiles(await listRoomProfiles());
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { code: err.code, message: err.message }
          : { code: 'UNKNOWN', message: 'Nao foi possivel carregar seus tipos de sala.' },
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (view.kind === 'form') {
    return (
      <RoomProfileForm
        existing={view.profile}
        onSaved={() => {
          setView({ kind: 'list' });
          void load();
        }}
        onDeleted={() => {
          setView({ kind: 'list' });
          void load();
        }}
        onCancel={() => setView({ kind: 'list' })}
      />
    );
  }

  const canJoin = parseRoomId(joinInput).length >= 3;

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-5 p-6">
      <div className="flex items-center justify-between gap-2">
        {onBack ? (
          <button type="button" onClick={onBack} className="text-sm text-slate-400 hover:text-slate-200">
            ← Console
          </button>
        ) : (
          <OrgBadge />
        )}
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-400">{user?.name}</span>
          <button type="button" onClick={logout} className="text-sm text-slate-400 hover:text-slate-200">
            Sair
          </button>
        </div>
      </div>

      {(onOpenOrgSettings || onOpenMembers || onOpenAppointments) && (
        <div className="flex flex-wrap justify-center gap-2 text-sm">
          {onOpenAppointments && (
            <button
              type="button"
              onClick={onOpenAppointments}
              className="rounded-lg border border-slate-600 px-3 py-1 text-slate-200 hover:bg-slate-800"
            >
              Atendimentos
            </button>
          )}
          {onOpenOrgSettings && (
            <button
              type="button"
              onClick={onOpenOrgSettings}
              className="rounded-lg border border-slate-600 px-3 py-1 text-slate-200 hover:bg-slate-800"
            >
              Organizacao
            </button>
          )}
          {onOpenMembers && (
            <button
              type="button"
              onClick={onOpenMembers}
              className="rounded-lg border border-slate-600 px-3 py-1 text-slate-200 hover:bg-slate-800"
            >
              Membros
            </button>
          )}
        </div>
      )}

      <div className="flex items-center justify-center gap-3">
        <h1 className="text-center text-2xl font-semibold text-slate-100">Minhas salas</h1>
        {onOpenHistory && (
          <button
            type="button"
            onClick={() => onOpenHistory()}
            className="rounded-lg border border-slate-600 px-3 py-1 text-sm text-slate-200 hover:bg-slate-800"
          >
            Historico
          </button>
        )}
      </div>

      {loading && <p className="text-center text-sm text-slate-400">Carregando...</p>}

      {error && <ErrorNotice error={error} onRetry={load} />}

      {!loading && !error && profiles.length === 0 && (
        <p className="text-center text-sm text-slate-400">
          Voce ainda nao tem tipos de sala. Crie o primeiro abaixo.
        </p>
      )}

      {!loading &&
        profiles.map((p) => (
          <div key={p.id} className="rounded-xl border border-slate-700 bg-slate-800/60 p-4">
            <div className="flex items-start justify-between">
              <div>
                <p className="font-medium text-slate-100">
                  <span className="mr-1">{TYPE_ICON[p.type]}</span>
                  {p.name}
                </p>
                <p className="text-sm text-slate-400">{p.durationMinutes} minutos</p>
              </div>
              <button
                type="button"
                onClick={() => setView({ kind: 'form', profile: p })}
                className="text-sm text-slate-400 hover:text-slate-200"
              >
                Editar
              </button>
            </div>
            <div className="mt-3 flex gap-2">
              <button
                type="button"
                onClick={() => onCreateFromProfile(p.id)}
                className="flex-1 rounded-lg bg-indigo-600 px-4 py-2 font-semibold text-white transition hover:bg-indigo-500"
              >
                Nova sala
              </button>
              {onOpenHistory && (
                <button
                  type="button"
                  onClick={() => onOpenHistory(p.id)}
                  className="rounded-lg border border-slate-600 px-4 py-2 font-medium text-slate-200 hover:bg-slate-700"
                >
                  Historico
                </button>
              )}
            </div>
          </div>
        ))}

      <button
        type="button"
        onClick={() => setView({ kind: 'form', profile: null })}
        className="rounded-lg border border-dashed border-slate-600 px-4 py-3 text-sm font-medium text-slate-300 hover:border-slate-400 hover:text-slate-100"
      >
        + Novo tipo de sala
      </button>

      <div className="mt-2 border-t border-slate-800 pt-5">
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-300">Entrar em uma sala</span>
          <input
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={joinInput}
            onChange={(e) => setJoinInput(e.target.value)}
            placeholder="Cole o link ou o codigo (room-...)"
          />
        </label>
        <button
          type="button"
          disabled={!canJoin}
          onClick={() => onRoomSelected(parseRoomId(joinInput))}
          className="mt-3 w-full rounded-lg border border-slate-600 px-4 py-2 font-semibold text-slate-100 transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Entrar
        </button>
      </div>
    </div>
  );
}
