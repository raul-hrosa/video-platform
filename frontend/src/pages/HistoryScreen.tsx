import { useCallback, useEffect, useMemo, useState } from 'react';
import { ErrorNotice } from '../components/ErrorNotice';
import { RoomStatusBadge } from '../components/RoomBadges';
import { ApiError, getRoomDashboard, listRoomProfiles, listRooms } from '../services/api';
import { dashboardBounds, formatDateTime, formatDuration } from '../services/format';
import type {
  FriendlyError,
  RoomDashboard,
  RoomProfile,
  RoomResponse,
  RoomStatus,
} from '../types';

interface Props {
  initialProfileId?: string;
  onOpenDetail: (roomId: string) => void;
  onBack: () => void;
  onCreate: () => void;
}

const PERIODS = [
  { key: 'all', label: 'Todas', days: null },
  { key: '1', label: 'Hoje', days: 1 },
  { key: '7', label: '7 dias', days: 7 },
  { key: '30', label: '30 dias', days: 30 },
] as const;

const STATUSES: RoomStatus[] = ['ACTIVE', 'WAITING', 'ENDED', 'EXPIRED'];
const PAGE_SIZE = 10;

function since(days: number | null): string | undefined {
  if (days == null) return undefined;
  const d = new Date();
  d.setDate(d.getDate() - (days - 1));
  d.setHours(0, 0, 0, 0);
  return d.toISOString();
}

function toFriendly(err: unknown, fallback: string): FriendlyError {
  if (err instanceof ApiError) {
    if (err.code === 'FORBIDDEN') {
      return { code: err.code, message: 'Voce nao tem permissao para visualizar esta sala.' };
    }
    return { code: err.code, message: fallback };
  }
  return { code: 'UNKNOWN', message: fallback };
}

export function HistoryScreen({ initialProfileId, onOpenDetail, onBack, onCreate }: Props) {
  const [profiles, setProfiles] = useState<RoomProfile[]>([]);
  const [status, setStatus] = useState<RoomStatus | ''>('');
  const [profileId, setProfileId] = useState<string>(initialProfileId ?? '');
  const [period, setPeriod] = useState<(typeof PERIODS)[number]['key']>('all');
  const [page, setPage] = useState(0);

  const [data, setData] = useState<RoomResponse[]>([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [dashboard, setDashboard] = useState<RoomDashboard | null>(null);

  const createdFrom = useMemo(
    () => since(PERIODS.find((p) => p.key === period)?.days ?? null),
    [period],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await listRooms({
        status: status || undefined,
        roomProfileId: profileId || undefined,
        createdFrom,
        page,
        size: PAGE_SIZE,
      });
      setData(res.content);
      setTotalPages(res.totalPages);
      setTotalElements(res.totalElements);
    } catch (err) {
      setError(toFriendly(err, 'Nao foi possivel carregar seu historico.'));
    } finally {
      setLoading(false);
    }
  }, [status, profileId, createdFrom, page]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    listRoomProfiles()
      .then(setProfiles)
      .catch(() => setProfiles([]));
  }, []);

  useEffect(() => {
    const { todayStart, weekStart } = dashboardBounds();
    getRoomDashboard(todayStart, weekStart)
      .then(setDashboard)
      .catch(() => setDashboard(null));
  }, []);

  // Qualquer mudança de filtro volta para a primeira página.
  useEffect(() => setPage(0), [status, profileId, period]);

  const isEmpty = !loading && !error && data.length === 0;
  const noFilters = !status && !profileId && period === 'all';

  return (
    <div className="mx-auto flex min-h-full max-w-2xl flex-col gap-5 p-4 sm:p-6">
      <div className="flex items-center justify-between">
        <button type="button" onClick={onBack} className="text-sm text-slate-400 hover:text-slate-200">
          ← Minhas salas
        </button>
        <button type="button" onClick={() => void load()} className="text-sm text-slate-400 hover:text-slate-200">
          Atualizar
        </button>
      </div>

      <h1 className="text-2xl font-semibold text-slate-100">Historico de salas</h1>

      {dashboard && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {[
            { label: 'Hoje', value: `${dashboard.roomsToday}` },
            { label: 'Esta semana', value: `${dashboard.roomsThisWeek}` },
            { label: 'Tempo em chamadas', value: formatDuration(dashboard.totalCallSeconds) },
            { label: 'Participantes', value: `${dashboard.distinctParticipants}` },
          ].map((s) => (
            <div key={s.label} className="rounded-xl border border-slate-700 bg-slate-800/60 p-3">
              <p className="text-xs text-slate-400">{s.label}</p>
              <p className="mt-1 text-lg font-semibold text-slate-100">{s.value}</p>
            </div>
          ))}
        </div>
      )}

      <div className="flex flex-wrap gap-2">
        <select
          aria-label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as RoomStatus | '')}
          className="rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-sm text-slate-100"
        >
          <option value="">Todos os status</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <select
          aria-label="Tipo de sala"
          value={profileId}
          onChange={(e) => setProfileId(e.target.value)}
          className="rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-sm text-slate-100"
        >
          <option value="">Todos os tipos</option>
          {profiles.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
        <select
          aria-label="Periodo"
          value={period}
          onChange={(e) => setPeriod(e.target.value as (typeof PERIODS)[number]['key'])}
          className="rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-sm text-slate-100"
        >
          {PERIODS.map((p) => (
            <option key={p.key} value={p.key}>
              {p.label}
            </option>
          ))}
        </select>
      </div>

      {loading && (
        <div className="space-y-3">
          {[0, 1, 2].map((i) => (
            <div key={i} className="h-28 animate-pulse rounded-xl border border-slate-800 bg-slate-800/40" />
          ))}
        </div>
      )}

      {error && <ErrorNotice error={error} onRetry={load} />}

      {isEmpty && noFilters && (
        <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-6 text-center">
          <p className="text-slate-300">Voce ainda nao realizou nenhuma chamada.</p>
          <p className="mt-1 text-sm text-slate-400">Crie uma sala a partir de um dos seus tipos de sala.</p>
          <button
            type="button"
            onClick={onCreate}
            className="mt-4 rounded-lg bg-indigo-600 px-4 py-2 font-semibold text-white hover:bg-indigo-500"
          >
            Criar nova sala
          </button>
        </div>
      )}

      {isEmpty && !noFilters && (
        <p className="text-center text-sm text-slate-400">Nenhuma sala para os filtros selecionados.</p>
      )}

      {!loading &&
        !error &&
        data.map((room) => (
          <RoomCard key={room.id} room={room} onOpenDetail={() => onOpenDetail(room.roomId)} />
        ))}

      {!loading && !error && totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-400">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="rounded-md border border-slate-600 px-3 py-1 disabled:opacity-40"
          >
            Anterior
          </button>
          <span>
            Pagina {page + 1} de {totalPages} · {totalElements} salas
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
            className="rounded-md border border-slate-600 px-3 py-1 disabled:opacity-40"
          >
            Proxima
          </button>
        </div>
      )}
    </div>
  );
}

function RoomCard({ room, onOpenDetail }: { room: RoomResponse; onOpenDetail: () => void }) {
  const isActive = room.status === 'ACTIVE';
  const durationText =
    room.startedAt && room.endedAt
      ? formatDuration((new Date(room.endedAt).getTime() - new Date(room.startedAt).getTime()) / 1000)
      : isActive
        ? 'em andamento'
        : room.status === 'EXPIRED'
          ? 'nunca iniciada'
          : '—';

  return (
    <div className="rounded-xl border border-slate-700 bg-slate-800/60 p-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-medium text-slate-100">{room.name}</p>
          {room.roomProfileName && (
            <p className="text-xs text-slate-500">Perfil: {room.roomProfileName}</p>
          )}
          {room.ownerName && (
            <p className="text-xs text-slate-500">Criada por {room.ownerName}</p>
          )}
        </div>
        <RoomStatusBadge status={room.status} />
      </div>
      <p className="mt-2 text-sm text-slate-400">{formatDateTime(room.createdAt)}</p>
      <p className="text-sm text-slate-400">Duracao: {durationText}</p>
      <div className="mt-3 flex justify-end">
        <button
          type="button"
          onClick={onOpenDetail}
          className="rounded-lg border border-slate-600 px-3 py-1.5 text-sm font-medium text-slate-100 hover:bg-slate-700"
        >
          Ver detalhes
        </button>
      </div>
    </div>
  );
}
