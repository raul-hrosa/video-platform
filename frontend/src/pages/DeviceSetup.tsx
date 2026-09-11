import { useEffect, useState } from 'react';
import { CopyLinkButton } from '../components/CopyLinkButton';
import { DeviceSelect } from '../components/DeviceSelect';
import { ErrorNotice } from '../components/ErrorNotice';
import { Toggle } from '../components/Toggle';
import { useCameraPreview } from '../hooks/useCameraPreview';
import { useMediaDevices } from '../hooks/useMediaDevices';
import { LogEvent, logger } from '../services/logger';
import type { FriendlyError, JoinConfig } from '../types';

interface CreatedRoom {
  roomId: string;
  name: string | null;
  expiresAt: string | null;
}

interface Props {
  roomId: string;
  userName: string;
  createdRoom?: CreatedRoom | null;
  /** Quando presente, mostra um campo "Seu nome" editavel (entrada de visitante). */
  guestName?: string;
  onGuestNameChange?: (value: string) => void;
  joining: boolean;
  joinError: FriendlyError | null;
  onJoin: (config: JoinConfig) => void;
  onBack: () => void;
}

function minutesUntil(iso: string): number {
  return Math.max(0, Math.round((new Date(iso).getTime() - Date.now()) / 60000));
}

export function DeviceSetup({
  roomId,
  userName,
  createdRoom,
  guestName,
  onGuestNameChange,
  joining,
  joinError,
  onJoin,
  onBack,
}: Props) {
  const isGuest = typeof onGuestNameChange === 'function';
  const guestNameOk = !isGuest || (guestName?.trim().length ?? 0) >= 1;
  const { devices, permissionGranted, error: deviceError, requestAccess } = useMediaDevices();

  const [cameraId, setCameraId] = useState<string | undefined>();
  const [microphoneId, setMicrophoneId] = useState<string | undefined>();
  const [cameraEnabled, setCameraEnabled] = useState(true);
  const [microphoneEnabled, setMicrophoneEnabled] = useState(true);

  useEffect(() => {
    void requestAccess();
  }, [requestAccess]);

  useEffect(() => {
    if (!cameraId && devices.cameras[0]) setCameraId(devices.cameras[0].deviceId);
  }, [devices.cameras, cameraId]);

  useEffect(() => {
    if (!microphoneId && devices.microphones[0]) setMicrophoneId(devices.microphones[0].deviceId);
  }, [devices.microphones, microphoneId]);

  const { videoRef, error: previewError } = useCameraPreview({
    cameraId,
    microphoneId,
    cameraEnabled,
    microphoneEnabled,
    active: permissionGranted,
  });

  const error = joinError ?? deviceError ?? previewError;

  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-5 p-6">
      <div className="text-center">
        <h1 className="text-2xl font-semibold text-slate-100">Video Platform</h1>
        <p className="mt-1 text-sm text-slate-400">
          {!isGuest && <>{userName} &middot; </>}Sala{' '}
          <span className="font-mono text-slate-200">{roomId}</span>
        </p>
      </div>

      {isGuest && (
        <div className="text-sm">
          <label htmlFor="guest-name" className="mb-1 block font-medium text-slate-300">
            Seu nome
          </label>
          <input
            id="guest-name"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={guestName ?? ''}
            onChange={(e) => onGuestNameChange?.(e.target.value)}
            placeholder="Como voce aparece na chamada"
            maxLength={60}
            required
          />
        </div>
      )}

      {createdRoom && (
        <div className="rounded-xl border border-emerald-500/40 bg-emerald-500/10 px-4 py-3 text-sm text-emerald-100">
          <p className="font-medium">Sala criada: {createdRoom.name ?? createdRoom.roomId}</p>
          {createdRoom.expiresAt && (
            <p className="mt-0.5 text-emerald-200/80">
              Expira em ~{minutesUntil(createdRoom.expiresAt)} min
            </p>
          )}
          <CopyLinkButton
            roomId={createdRoom.roomId}
            className="mt-2 rounded-md bg-emerald-500/20 px-3 py-1.5 font-medium text-emerald-50 hover:bg-emerald-500/30"
          />
        </div>
      )}

      <div className="relative aspect-video overflow-hidden rounded-xl bg-black">
        {cameraEnabled ? (
          <video ref={videoRef} autoPlay playsInline muted className="h-full w-full object-cover" />
        ) : (
          <div className="flex h-full items-center justify-center text-slate-500">Camera desligada</div>
        )}
      </div>

      <div className="flex justify-center gap-3">
        <Toggle
          active={microphoneEnabled}
          onToggle={() => setMicrophoneEnabled((v) => !v)}
          labelOn="Microfone ligado"
          labelOff="Microfone desligado"
        />
        <Toggle
          active={cameraEnabled}
          onToggle={() => setCameraEnabled((v) => !v)}
          labelOn="Camera ligada"
          labelOff="Camera desligada"
        />
      </div>

      <DeviceSelect
        label="Camera"
        devices={devices.cameras}
        value={cameraId}
        onChange={(id) => {
          setCameraId(id);
          logger.info({ event: LogEvent.DEVICE_SELECTED, kind: 'videoinput', deviceId: id });
        }}
        disabled={!permissionGranted}
      />
      <DeviceSelect
        label="Microfone"
        devices={devices.microphones}
        value={microphoneId}
        onChange={(id) => {
          setMicrophoneId(id);
          logger.info({ event: LogEvent.DEVICE_SELECTED, kind: 'audioinput', deviceId: id });
        }}
        disabled={!permissionGranted}
      />

      {error && <ErrorNotice error={error} onRetry={!permissionGranted ? requestAccess : undefined} />}

      <button
        type="button"
        disabled={joining || !guestNameOk}
        onClick={() =>
          onJoin({
            roomId,
            cameraId,
            microphoneId,
            cameraEnabled,
            microphoneEnabled,
          })
        }
        className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
      >
        {joining ? 'Entrando...' : 'Entrar na chamada'}
      </button>

      <button
        type="button"
        onClick={onBack}
        className="text-sm text-slate-400 hover:text-slate-200"
      >
        Voltar
      </button>
    </div>
  );
}
