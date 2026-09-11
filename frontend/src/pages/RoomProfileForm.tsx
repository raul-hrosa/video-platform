import { useState } from 'react';
import { ErrorNotice } from '../components/ErrorNotice';
import { ApiError, createRoomProfile, deleteRoomProfile, updateRoomProfile } from '../services/api';
import { LogEvent, logger } from '../services/logger';
import type { FriendlyError, RoomProfile, RoomType } from '../types';

const TYPE_OPTIONS: { value: RoomType; label: string }[] = [
  { value: 'LESSON', label: 'Aula' },
  { value: 'CONSULTATION', label: 'Teleconsulta' },
  { value: 'MEETING', label: 'Reuniao' },
  { value: 'INTERVIEW', label: 'Entrevista' },
  { value: 'OTHER', label: 'Outro' },
];

const MIN_MINUTES = 1;
const MAX_MINUTES = 480;

interface Props {
  existing?: RoomProfile | null;
  onSaved: (profile: RoomProfile) => void;
  onDeleted: (profileId: string) => void;
  onCancel: () => void;
}

export function RoomProfileForm({ existing, onSaved, onDeleted, onCancel }: Props) {
  const editing = Boolean(existing);
  const [name, setName] = useState(existing?.name ?? '');
  const [type, setType] = useState<RoomType>(existing?.type ?? 'LESSON');
  const [duration, setDuration] = useState(String(existing?.durationMinutes ?? 20));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<FriendlyError | null>(null);

  const durationNum = Number(duration);
  const durationValid =
    duration.trim() !== '' &&
    Number.isInteger(durationNum) &&
    durationNum >= MIN_MINUTES &&
    durationNum <= MAX_MINUTES;
  const valid = name.trim().length > 0 && durationValid;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!valid || busy) return;
    setError(null);
    setBusy(true);
    try {
      const input = { name: name.trim(), durationMinutes: durationNum, type };
      const saved = existing
        ? await updateRoomProfile(existing.id, input)
        : await createRoomProfile(input);
      logger.info({
        event: editing ? LogEvent.ROOM_PROFILE_UPDATED : LogEvent.ROOM_PROFILE_CREATED,
        roomProfileId: saved.id,
      });
      onSaved(saved);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { code: err.code, message: err.message }
          : { code: 'UNKNOWN', message: 'Nao foi possivel salvar o perfil.' },
      );
    } finally {
      setBusy(false);
    }
  }

  async function remove() {
    if (!existing || busy) return;
    setError(null);
    setBusy(true);
    try {
      await deleteRoomProfile(existing.id);
      logger.info({ event: LogEvent.ROOM_PROFILE_DELETED, roomProfileId: existing.id });
      onDeleted(existing.id);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? { code: err.code, message: err.message }
          : { code: 'UNKNOWN', message: 'Nao foi possivel excluir o perfil.' },
      );
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-5 p-6">
      <h1 className="text-center text-2xl font-semibold text-slate-100">
        {editing ? 'Editar tipo de sala' : 'Novo tipo de sala'}
      </h1>

      <form className="flex flex-col gap-4" onSubmit={submit}>
        <div className="text-sm">
          <label htmlFor="rpf-name" className="mb-1 block font-medium text-slate-300">
            Nome
          </label>
          <input
            id="rpf-name"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Ex.: Aula de Ingles"
            maxLength={120}
            required
          />
        </div>

        <div className="text-sm">
          <label htmlFor="rpf-type" className="mb-1 block font-medium text-slate-300">
            Tipo
          </label>
          <select
            id="rpf-type"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={type}
            onChange={(e) => setType(e.target.value as RoomType)}
          >
            {TYPE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </div>

        <div className="text-sm">
          <label htmlFor="rpf-duration" className="mb-1 block font-medium text-slate-300">
            Duracao
          </label>
          <div className="flex items-center gap-2">
            <input
              id="rpf-duration"
              type="number"
              inputMode="numeric"
              min={MIN_MINUTES}
              max={MAX_MINUTES}
              className="w-24 rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
              value={duration}
              onChange={(e) => setDuration(e.target.value)}
              required
            />
            <span className="text-slate-400">minutos</span>
          </div>
          {!durationValid && duration.trim() !== '' && (
            <span className="mt-1 block text-xs text-red-300">
              Entre {MIN_MINUTES} e {MAX_MINUTES} minutos.
            </span>
          )}
        </div>

        {error && <ErrorNotice error={error} />}

        <button
          type="submit"
          disabled={!valid || busy}
          className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {busy ? 'Salvando...' : 'Salvar'}
        </button>
      </form>

      <div className="flex items-center justify-between text-sm">
        <button type="button" onClick={onCancel} className="text-slate-400 hover:text-slate-200">
          Voltar
        </button>
        {editing && (
          <button
            type="button"
            onClick={remove}
            disabled={busy}
            className="text-red-300 hover:text-red-200 disabled:opacity-40"
          >
            Excluir
          </button>
        )}
      </div>
    </div>
  );
}
