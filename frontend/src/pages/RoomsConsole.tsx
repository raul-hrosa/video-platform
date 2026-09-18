import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice } from '../components/ErrorNotice';
import { OrgBadge } from '../components/OrgBadge';
import { RoomConsoleBadge } from '../components/RoomBadges';
import { ApiError, listRooms } from '../services/api';
import { elapsedSince, formatDuration } from '../services/format';
import { parseRoomId } from '../services/roomLink';
import type { FriendlyError, RoomResponse } from '../types';

interface Props {
  /** Abre a sala no Room Console. */
  onOpenRoom: (roomId: string) => void;
  /** Entra direto numa sala (colando link/código). */
  onJoinRoom: (roomId: string) => void;
  /** Cria uma Room de infraestrutura e vai direto para o Device Setup (§6). */
  onCreateRoom: () => void;
  /** Navegação secundária. */
  onOpenProfiles: () => void;
  onOpenHistory: () => void;
  onOpenAppointments: () => void;
  onOpenOrgSettings: () => void;
  onOpenMembers: () => void;
}

/**
 * Console de Rooms (Sprint 9 §5): a tela principal da plataforma. Um botão para
 * criar uma sala, a lista de salas com Room ID / status / participantes / duração,
 * e navegação secundária de-enfatizada para o resto (perfis, histórico, etc.).
 */
export function RoomsConsole({
  onOpenRoom,
  onJoinRoom,
  onCreateRoom,
  onOpenProfiles,
  onOpenHistory,
  onOpenAppointments,
  onOpenOrgSettings,
  onOpenMembers,
}: Props) {
  const { user, logout } = useAuth();
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [joinInput, setJoinInput] = useState('');
  const [moreOpen, setMoreOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await listRooms({ size: 25 });
      setRooms(page.content);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { code: err.code, message: err.message }
          : { code: 'UNKNOWN', message: 'Nao foi possivel carregar as salas.' },
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const canJoin = parseRoomId(joinInput).length >= 3;

  return (
    <div className="mx-auto flex min-h-full max-w-3xl flex-col gap-6 p-4 sm:p-6">
      <div className="flex items-center justify-between gap-2">
        <OrgBadge />
        <div className="flex items-center gap-3">
          <span className="text-sm text-slate-400">{user?.name}</span>
          <button type="button" onClick={logout} className="text-sm text-slate-400 hover:text-slate-200">
            Sair
          </button>
        </div>
      </div>

      <div className="rounded-2xl border border-slate-700 bg-slate-800/60 p-6 text-center">
        <h1 className="text-lg font-semibold text-slate-100">Create room</h1>
        <p className="mt-1 text-sm text-slate-400">A new room will receive a unique ID.</p>
        <button
          type="button"
          onClick={onCreateRoom}
          className="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white transition hover:bg-indigo-500"
        >
          Create room
        </button>
      </div>

      <div className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-400">Rooms</h2>
          <button type="button" onClick={() => void load()} className="text-xs text-slate-400 hover:text-slate-200">
            Atualizar
          </button>
        </div>

        {loading && <p className="text-sm text-slate-400">Carregando...</p>}
        {error && <ErrorNotice error={error} onRetry={load} />}
        {!loading && !error && rooms.length === 0 && (
          <p className="text-sm text-slate-400">Nenhuma sala ainda. Crie a primeira acima.</p>
        )}

        {!loading &&
          rooms.map((room) => (
            <button
              key={room.id}
              type="button"
              onClick={() => onOpenRoom(room.roomId)}
              className="flex items-center justify-between gap-3 rounded-xl border border-slate-700 bg-slate-800/40 px-4 py-3 text-left transition hover:border-slate-500"
            >
              <div className="min-w-0">
                <p className="truncate font-mono text-sm text-slate-100">{room.roomId}</p>
                {room.name && <p className="truncate text-xs text-slate-400">{room.name}</p>}
              </div>
              <div className="flex shrink-0 items-center gap-3 text-xs text-slate-400">
                {room.connectedCount > 0 && (
                  <span title="Pessoas conectadas agora">
                    👤 {room.connectedCount}
                  </span>
                )}
                {room.displayStatus === 'LIVE' && room.startedAt && (
                  <span>{formatDuration(elapsedSince(room.startedAt))}</span>
                )}
                <RoomConsoleBadge status={room.displayStatus} />
              </div>
            </button>
          ))}
      </div>

      <div className="border-t border-slate-800 pt-4">
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
          onClick={() => onJoinRoom(parseRoomId(joinInput))}
          className="mt-3 w-full rounded-lg border border-slate-600 px-4 py-2 font-semibold text-slate-100 transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-40"
        >
          Entrar
        </button>
      </div>

      <div className="text-sm">
        <button
          type="button"
          onClick={() => setMoreOpen((v) => !v)}
          className="text-slate-400 hover:text-slate-200"
        >
          {moreOpen ? '▾' : '▸'} Mais
        </button>
        {moreOpen && (
          <div className="mt-2 flex flex-wrap gap-2">
            {[
              ['Tipos de sala', onOpenProfiles],
              ['Histórico', onOpenHistory],
              ['Atendimentos', onOpenAppointments],
              ['Organização', onOpenOrgSettings],
              ['Membros', onOpenMembers],
            ].map(([label, fn]) => (
              <button
                key={label as string}
                type="button"
                onClick={fn as () => void}
                className="rounded-lg border border-slate-600 px-3 py-1 text-slate-200 hover:bg-slate-800"
              >
                {label as string}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
