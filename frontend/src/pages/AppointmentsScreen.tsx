import { type ReactNode, useCallback, useEffect, useState } from 'react';
import { CopyLinkButton } from '../components/CopyLinkButton';
import { ErrorNotice } from '../components/ErrorNotice';
import {
  ApiError,
  cancelAppointment,
  createAppointment,
  listAppointmentOccurrences,
  listAppointments,
  listRoomProfiles,
  updateAppointment,
} from '../services/api';
import { appointmentLink } from '../services/appointmentLink';
import { formatDateTime } from '../services/format';
import type {
  Appointment,
  AppointmentInput,
  AppointmentOccurrence,
  FriendlyError,
  RoomProfile,
} from '../types';

interface Props {
  onBack: () => void;
  /** Abre o detalhe de uma Room (reaproveita a tela de histórico da Sprint 6). */
  onOpenRoom: (roomId: string) => void;
}

type View =
  | { name: 'list' }
  | { name: 'form'; appointment: Appointment | null }
  | { name: 'detail'; appointment: Appointment };

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const DAY_LABEL: Record<string, string> = {
  MONDAY: 'Segunda',
  TUESDAY: 'Terça',
  WEDNESDAY: 'Quarta',
  THURSDAY: 'Quinta',
  FRIDAY: 'Sexta',
  SATURDAY: 'Sábado',
  SUNDAY: 'Domingo',
};

function friendly(err: unknown, fallback: string): FriendlyError {
  return err instanceof ApiError ? { code: err.code, message: err.message } : { code: 'UNKNOWN', message: fallback };
}

function recurrenceText(a: Appointment): string {
  if (a.recurrenceType === 'WEEKLY') {
    const day = a.recurrenceDayOfWeek ? DAY_LABEL[a.recurrenceDayOfWeek] ?? a.recurrenceDayOfWeek : '';
    return `Toda ${day.toLowerCase()} às ${formatTimeOnly(a.startsAt)}`;
  }
  return `${formatDateTime(a.startsAt)}`;
}

function formatTimeOnly(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}

/** "Meus atendimentos" (§37-§39). Sem router: lista ⇄ formulário ⇄ detalhe. */
export function AppointmentsScreen({ onBack, onOpenRoom }: Props) {
  const [view, setView] = useState<View>({ name: 'list' });
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<FriendlyError | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setAppointments(await listAppointments());
    } catch (err) {
      setError(friendly(err, 'Nao foi possivel carregar seus atendimentos.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  if (view.name === 'form') {
    return (
      <AppointmentForm
        existing={view.appointment}
        onCancel={() => setView({ name: 'list' })}
        onSaved={() => {
          setView({ name: 'list' });
          void load();
        }}
      />
    );
  }

  if (view.name === 'detail') {
    return (
      <AppointmentDetail
        appointment={view.appointment}
        onBack={() => {
          setView({ name: 'list' });
          void load();
        }}
        onEdit={() => setView({ name: 'form', appointment: view.appointment })}
        onOpenRoom={onOpenRoom}
        onCancelled={() => {
          setView({ name: 'list' });
          void load();
        }}
      />
    );
  }

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col gap-4 p-6">
      <button type="button" onClick={onBack} className="self-start text-sm text-slate-400 hover:text-slate-200">
        ← Voltar
      </button>
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold text-slate-100">Meus atendimentos</h1>
        <button
          type="button"
          onClick={() => setView({ name: 'form', appointment: null })}
          className="rounded-lg bg-indigo-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-indigo-500"
        >
          Novo
        </button>
      </div>

      {loading && <p className="text-center text-sm text-slate-400">Carregando...</p>}
      {error && <ErrorNotice error={error} onRetry={load} />}

      {!loading && !error && appointments.length === 0 && (
        <p className="text-center text-sm text-slate-400">Voce ainda nao criou nenhum atendimento.</p>
      )}

      {appointments.map((a) => (
        <div key={a.id} className="rounded-xl border border-slate-700 bg-slate-800/60 p-4">
          <div className="flex items-start justify-between gap-2">
            <div>
              <p className="font-medium text-slate-100">
                {a.status === 'CANCELLED' && <span className="mr-1 text-slate-500">✕</span>}
                {a.title}
              </p>
              <p className="text-sm text-slate-400">{recurrenceText(a)}</p>
              <p className="text-sm text-slate-400">{a.durationMinutes} minutos</p>
              {a.nextOccurrence && a.status === 'ACTIVE' && (
                <p className="mt-1 text-sm text-emerald-300">Proximo: {formatDateTime(a.nextOccurrence)}</p>
              )}
              {a.status === 'CANCELLED' && <p className="mt-1 text-sm text-slate-500">Cancelado</p>}
            </div>
          </div>
          <div className="mt-3 flex flex-wrap gap-2">
            <button
              type="button"
              onClick={() => setView({ name: 'detail', appointment: a })}
              className="rounded-lg border border-slate-600 px-3 py-1.5 text-sm text-slate-200 hover:bg-slate-700"
            >
              Abrir
            </button>
            <CopyLinkButton link={appointmentLink(a.publicAccessId)} />
          </div>
        </div>
      ))}
    </div>
  );
}

// ---- formulário ----

function toLocalInputValue(iso: string): { date: string; time: string } {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return {
    date: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
    time: `${pad(d.getHours())}:${pad(d.getMinutes())}`,
  };
}

function AppointmentForm({
  existing,
  onSaved,
  onCancel,
}: {
  existing: Appointment | null;
  onSaved: () => void;
  onCancel: () => void;
}) {
  const [profiles, setProfiles] = useState<RoomProfile[]>([]);
  const [title, setTitle] = useState(existing?.title ?? '');
  const [profileId, setProfileId] = useState(existing?.roomProfileId ?? '');
  const [participant, setParticipant] = useState(existing?.participantName ?? '');
  const initial = existing ? toLocalInputValue(existing.startsAt) : { date: '', time: '' };
  const [date, setDate] = useState(initial.date);
  const [time, setTime] = useState(initial.time);
  const [repeat, setRepeat] = useState(existing?.recurrenceType === 'WEEKLY');
  const [day, setDay] = useState(existing?.recurrenceDayOfWeek ?? 'TUESDAY');
  const [until, setUntil] = useState(existing?.recurrenceUntil ?? '');
  const [error, setError] = useState<FriendlyError | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void listRoomProfiles().then(setProfiles).catch(() => setProfiles([]));
  }, []);

  const selectedProfile = profiles.find((p) => p.id === profileId);
  const canSave = title.trim() && profileId && date && time && !saving;

  async function submit() {
    setSaving(true);
    setError(null);
    try {
      const startsAt = new Date(`${date}T${time}`).toISOString();
      const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      const recurrence = repeat
        ? { type: 'WEEKLY' as const, dayOfWeek: day, until: until || null }
        : { type: 'NONE' as const };
      const base = { title: title.trim(), participantName: participant.trim() || null, startsAt, timezone, recurrence };
      if (existing) {
        await updateAppointment(existing.id, base);
      } else {
        await createAppointment({ ...base, roomProfileId: profileId } as AppointmentInput);
      }
      onSaved();
    } catch (err) {
      setError(friendly(err, 'Nao foi possivel salvar o atendimento.'));
      setSaving(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col gap-4 p-6">
      <button type="button" onClick={onCancel} className="self-start text-sm text-slate-400 hover:text-slate-200">
        ← Cancelar
      </button>
      <h1 className="text-2xl font-semibold text-slate-100">
        {existing ? 'Editar atendimento' : 'Novo atendimento'}
      </h1>

      <Field label="Titulo">
        <input className={inputCls} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} />
      </Field>

      <Field label="Perfil">
        <select
          className={inputCls}
          value={profileId}
          onChange={(e) => setProfileId(e.target.value)}
          disabled={!!existing}
        >
          <option value="">Selecione...</option>
          {profiles.map((p) => (
            <option key={p.id} value={p.id}>
              {p.name} ({p.durationMinutes} min)
            </option>
          ))}
        </select>
      </Field>

      <Field label="Participante (opcional)">
        <input className={inputCls} value={participant} onChange={(e) => setParticipant(e.target.value)} maxLength={120} />
      </Field>

      <div className="flex gap-3">
        <Field label="Data">
          <input type="date" className={inputCls} value={date} onChange={(e) => setDate(e.target.value)} />
        </Field>
        <Field label="Horario">
          <input type="time" className={inputCls} value={time} onChange={(e) => setTime(e.target.value)} />
        </Field>
      </div>

      <label className="flex items-center gap-2 text-sm text-slate-200">
        <input type="checkbox" checked={repeat} onChange={(e) => setRepeat(e.target.checked)} />
        Repetir toda semana
      </label>

      {repeat && (
        <div className="flex gap-3">
          <Field label="Dia">
            <select className={inputCls} value={day} onChange={(e) => setDay(e.target.value)}>
              {DAYS.map((d) => (
                <option key={d} value={d}>
                  {DAY_LABEL[d]}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Ate (opcional)">
            <input type="date" className={inputCls} value={until} onChange={(e) => setUntil(e.target.value)} />
          </Field>
        </div>
      )}

      <p className="text-sm text-slate-400">
        Duracao: {selectedProfile ? `${selectedProfile.durationMinutes} minutos (do perfil)` : '—'}
      </p>

      {error && <ErrorNotice error={error} />}

      <button
        type="button"
        disabled={!canSave}
        onClick={submit}
        className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white hover:bg-indigo-500 disabled:opacity-40"
      >
        {existing ? 'Salvar' : 'Criar atendimento'}
      </button>
    </div>
  );
}

// ---- detalhe ----

function AppointmentDetail({
  appointment,
  onBack,
  onEdit,
  onOpenRoom,
  onCancelled,
}: {
  appointment: Appointment;
  onBack: () => void;
  onEdit: () => void;
  onOpenRoom: (roomId: string) => void;
  onCancelled: () => void;
}) {
  const [occurrences, setOccurrences] = useState<AppointmentOccurrence[]>([]);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void listAppointmentOccurrences(appointment.id)
      .then(setOccurrences)
      .catch((err) => setError(friendly(err, 'Nao foi possivel carregar o historico.')));
  }, [appointment.id]);

  async function doCancel() {
    if (!window.confirm('Cancelar este atendimento? Ocorrencias passadas continuam no historico.')) return;
    setBusy(true);
    try {
      await cancelAppointment(appointment.id);
      onCancelled();
    } catch (err) {
      setError(friendly(err, 'Nao foi possivel cancelar.'));
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col gap-4 p-6">
      <button type="button" onClick={onBack} className="self-start text-sm text-slate-400 hover:text-slate-200">
        ← Voltar
      </button>
      <h1 className="text-2xl font-semibold text-slate-100">{appointment.title}</h1>

      <dl className="space-y-1 text-sm text-slate-300">
        <Row k="Participante" v={appointment.participantName ?? '—'} />
        <Row k="Duracao" v={`${appointment.durationMinutes} minutos`} />
        <Row k="Timezone" v={appointment.timezone} />
        <Row k="Recorrencia" v={recurrenceText(appointment)} />
        <Row k="Proxima" v={appointment.nextOccurrence ? formatDateTime(appointment.nextOccurrence) : '—'} />
        <Row k="Status" v={appointment.status === 'ACTIVE' ? 'Ativo' : 'Cancelado'} />
      </dl>

      <div className="rounded-lg border border-slate-700 bg-slate-800/60 p-3 text-sm">
        <p className="mb-1 font-medium text-slate-300">Link do atendimento</p>
        <p className="mb-2 break-all font-mono text-xs text-slate-400">{appointmentLink(appointment.publicAccessId)}</p>
        <CopyLinkButton link={appointmentLink(appointment.publicAccessId)} />
      </div>

      {appointment.status === 'ACTIVE' && (
        <div className="flex gap-2">
          <button
            type="button"
            onClick={onEdit}
            className="rounded-lg border border-slate-600 px-3 py-1.5 text-sm text-slate-200 hover:bg-slate-700"
          >
            Editar
          </button>
          <button
            type="button"
            disabled={busy}
            onClick={doCancel}
            className="rounded-lg border border-red-500/40 px-3 py-1.5 text-sm text-red-200 hover:bg-red-500/10 disabled:opacity-40"
          >
            Cancelar atendimento
          </button>
        </div>
      )}

      {error && <ErrorNotice error={error} />}

      <div>
        <h2 className="mb-2 text-lg font-semibold text-slate-100">Historico por ocorrencia</h2>
        {occurrences.length === 0 && <p className="text-sm text-slate-400">Nenhuma ocorrencia ainda.</p>}
        <ul className="space-y-2">
          {occurrences.map((o) => (
            <li
              key={o.id}
              className="flex items-center justify-between rounded-lg border border-slate-700 bg-slate-800/40 px-3 py-2 text-sm"
            >
              <div>
                <p className="text-slate-200">{formatDateTime(o.scheduledStart)}</p>
                <p className="text-xs text-slate-500">{o.status}</p>
              </div>
              {o.roomId && (
                <button
                  type="button"
                  onClick={() => onOpenRoom(o.roomId as string)}
                  className="text-indigo-300 hover:text-indigo-200"
                >
                  Ver detalhes
                </button>
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

const inputCls = 'w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100';

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block flex-1 text-sm">
      <span className="mb-1 block font-medium text-slate-300">{label}</span>
      {children}
    </label>
  );
}

function Row({ k, v }: { k: string; v: string }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-slate-500">{k}</dt>
      <dd className="text-right text-slate-200">{v}</dd>
    </div>
  );
}
