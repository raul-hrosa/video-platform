import {
  LiveKitRoom,
  useConnectionQualityIndicator,
  useConnectionState,
  useLocalParticipant,
  useRemoteParticipants,
  useRoomContext,
  useTrackMutedIndicator,
  useTrackVolume,
  useTracks,
  VideoTrack,
  AudioTrack,
  type TrackReference,
} from '@livekit/components-react';
import {
  RoomEvent,
  Track,
  type Participant,
  type RemoteAudioTrack,
  type RemoteParticipant,
} from 'livekit-client';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ConnectionSignal } from '../../components/ConnectionSignal';
import { CopyLinkButton } from '../../components/CopyLinkButton';
import { ParticipantMuteIndicator } from '../../components/ParticipantMuteIndicator';
import { LogEvent, logger } from '../../services/logger';
import { remoteQualityDescription } from '../../services/qualityText';
import { EMPTY_METRICS, type ConnectionMetrics, type QualityLevel } from '../../types/connectionQuality';
import type { VideoProviderProps } from '../VideoProvider';
import { mapLiveKitQuality } from './livekitMetrics';
import { playParticipantJoinedSound, playParticipantLeftSound } from '../notificationSound';
import { QualityPanel } from './QualityPanel';
import { useConnectionQuality } from './useConnectionQuality';
import { useSessionResolver } from '../../hooks/useSessionResolver';

/**
 * Sons de entrada/saída (Sprint 18): ao contrário do PulseRTC, o LiveKit
 * documenta que `ParticipantConnected`/`ParticipantDisconnected` só disparam
 * para mudanças *depois* que o participante local já entrou — não há rajada
 * inicial para quem já estava na sala, então nenhum guard é necessário aqui.
 */
function ParticipantSoundNotifier() {
  const room = useRoomContext();

  useEffect(() => {
    const onConnected = () => playParticipantJoinedSound();
    const onDisconnected = () => playParticipantLeftSound();
    room.on(RoomEvent.ParticipantConnected, onConnected);
    room.on(RoomEvent.ParticipantDisconnected, onDisconnected);
    return () => {
      room.off(RoomEvent.ParticipantConnected, onConnected);
      room.off(RoomEvent.ParticipantDisconnected, onDisconnected);
    };
  }, [room]);

  return null;
}

function ConnectionLogger({ roomId }: { roomId: string }) {
  const room = useRoomContext();

  useEffect(() => {
    const onReconnecting = () =>
      logger.warn({ event: LogEvent.PARTICIPANT_RECONNECTING, roomId });
    const onReconnected = () =>
      logger.info({ event: LogEvent.PARTICIPANT_RECONNECTED, roomId });
    room.on('reconnecting', onReconnecting);
    room.on('reconnected', onReconnected);
    return () => {
      room.off('reconnecting', onReconnecting);
      room.off('reconnected', onReconnected);
    };
  }, [room, roomId]);

  return null;
}

/**
 * Coleta metricas tecnicas do participante local e reporta ao backend
 * (Sprint 6). Sem UI propria aqui — o painel tecnico (`QualityPanel`) e'
 * renderizado por `CustomCallUI`; este componente so' existe pelo efeito
 * colateral de `useConnectionQuality` (persistencia + reconexoes).
 */
interface LocalQualityState {
  level: QualityLevel | null;
  metrics: ConnectionMetrics;
  reconnectCount: number;
}

function QualityReporter({
  roomId,
  participantId,
  resolveSession,
  onChange,
}: {
  roomId: string;
  participantId: string;
  resolveSession: boolean;
  onChange: (state: LocalQualityState) => void;
}) {
  const room = useRoomContext();
  const connectionState = useConnectionState();
  const connected = connectionState === 'connected';
  const sessionId = useSessionResolver({ roomId, connected: connected && resolveSession });
  const { level, metrics, reconnectCount } = useConnectionQuality({
    room,
    roomId,
    participantId,
    sessionId,
    connected,
  });

  useEffect(() => {
    onChange({ level, metrics, reconnectCount });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [level, metrics, reconnectCount]);

  return null;
}

function findTrackRef(refs: TrackReference[], identity: string): TrackReference | undefined {
  return refs.find((r) => r.participant.identity === identity);
}

/**
 * `useTrackMutedIndicator` lança exceção quando recebe `undefined` em vez de
 * tratar como "sem track" — precisa sempre de um `TrackReference` ou de um
 * placeholder `{ participant, source }` (sem `publication`).
 */
function mutedRef(
  trackRef: TrackReference | undefined,
  participant: Participant,
  source: Track.Source,
): TrackReference | { participant: Participant; source: Track.Source } {
  return trackRef ?? { participant, source };
}

interface RemoteTileProps {
  participant: RemoteParticipant;
  camTrackRef?: TrackReference;
  micTrackRef?: TrackReference;
  compact: boolean;
  volume: number;
  onVolumeChange: (v: number) => void;
}

function RemoteTile({
  participant,
  camTrackRef,
  micTrackRef,
  compact,
  volume,
  onVolumeChange,
}: RemoteTileProps) {
  const { isMuted: camMuted } = useTrackMutedIndicator(
    mutedRef(camTrackRef, participant, Track.Source.Camera),
  );
  const { isMuted: micMuted } = useTrackMutedIndicator(
    mutedRef(micTrackRef, participant, Track.Source.Microphone),
  );
  const { quality } = useConnectionQualityIndicator({ participant });
  const audioLevel = useTrackVolume(micTrackRef);

  useEffect(() => {
    const track = micTrackRef?.publication?.track as RemoteAudioTrack | undefined;
    track?.setVolume(volume);
  }, [micTrackRef, volume]);

  const name = participant.name || participant.identity;
  const cameraOff = !camTrackRef || camMuted;
  const audioMuted = !micTrackRef || micMuted;
  const level: QualityLevel = mapLiveKitQuality(quality) ?? 'UNKNOWN';

  return (
    <Tile
      label={name}
      name={name}
      trackRef={cameraOff ? undefined : camTrackRef}
      quality={level}
      audioMuted={audioMuted}
      cameraOff={cameraOff}
      compact={compact}
      volume={volume}
      onVolumeChange={onVolumeChange}
      audioLevel={audioLevel}
    />
  );
}

interface RemoteSidebarRowProps {
  participant: RemoteParticipant;
  micTrackRef?: TrackReference;
  collapsed: boolean;
  volume: number;
  onVolumeChange: (v: number) => void;
}

function RemoteSidebarRow({
  participant,
  micTrackRef,
  collapsed,
  volume,
  onVolumeChange,
}: RemoteSidebarRowProps) {
  const { isMuted: micMuted } = useTrackMutedIndicator(
    mutedRef(micTrackRef, participant, Track.Source.Microphone),
  );
  const { quality } = useConnectionQualityIndicator({ participant });
  const audioLevel = useTrackVolume(micTrackRef);

  const name = participant.name || participant.identity;
  const audioMuted = !micTrackRef || micMuted;
  const level: QualityLevel = mapLiveKitQuality(quality) ?? 'UNKNOWN';
  const speaking = audioLevel > 0.12;
  const initial = name.trim().charAt(0).toUpperCase() || '?';

  const avatar = (
    <span
      className="relative flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-xs font-bold text-white"
      style={{ background: avatarColor(participant.identity) }}
    >
      {initial}
      {speaking && (
        <span
          className="pointer-events-none absolute -inset-[3px] animate-pulse rounded-full ring-2 ring-sky-400"
          aria-hidden="true"
        />
      )}
      {collapsed && audioMuted && (
        <span className="absolute -bottom-0.5 -right-0.5 flex h-3.5 w-3.5 items-center justify-center rounded-full border-2 border-slate-900 bg-red-500 text-[8px]">
          🔇
        </span>
      )}
    </span>
  );

  if (collapsed) {
    return (
      <div className="group relative flex justify-center py-1" tabIndex={0}>
        {avatar}
        <div className="pointer-events-none absolute left-full top-1/2 z-20 ml-2 w-48 -translate-y-1/2 rounded-lg border border-slate-700 bg-slate-800 p-2.5 opacity-0 shadow-xl transition-opacity group-hover:pointer-events-auto group-hover:opacity-100 group-focus-within:pointer-events-auto group-focus-within:opacity-100">
          <div className="mb-1.5 truncate text-xs font-semibold text-slate-100">{name}</div>
          <VolumeSlider volume={volume} onChange={onVolumeChange} />
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2.5 rounded-lg px-1.5 py-1.5 hover:bg-slate-800/60">
      {avatar}
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-1.5 truncate text-sm font-medium text-slate-100">
          <span className="truncate">{name}</span>
          <ConnectionSignal
            quality={level}
            variant="compact"
            name={name}
            hint={remoteQualityDescription(level)}
          />
        </div>
        <div className="mt-1 flex items-center gap-1.5">
          <ParticipantMuteIndicator muted={audioMuted} name={name} />
          <VolumeSlider volume={volume} onChange={onVolumeChange} />
        </div>
      </div>
    </div>
  );
}

/**
 * Implementação LiveKit do contrato {@link VideoProviderProps} (Sprint 11
 * §13). A UI (grade, sidebar de participantes, palco de compartilhamento de
 * tela, indicadores por participante) é a mesma desenhada originalmente para
 * o PulseRTC (Sprint 13-17) — só a fonte de dados muda: em vez de eventos
 * manuais do SDK, usa os hooks reativos do `@livekit/components-react`
 * (`useTracks`, `useTrackMutedIndicator`, `useConnectionQualityIndicator`,
 * `useTrackVolume`), que já resolvem reconexão, adaptive stream e dynacast.
 */
function CustomCallUI({
  roomId,
  participantId,
  resolveSession,
  onLeave,
}: {
  roomId: string;
  participantId: string;
  resolveSession: boolean;
  onLeave: () => void;
}) {
  const room = useRoomContext();
  const connectionState = useConnectionState();
  const { localParticipant, isMicrophoneEnabled, isCameraEnabled, isScreenShareEnabled } =
    useLocalParticipant();
  const remoteParticipants = useRemoteParticipants();

  const camRefs = useTracks([Track.Source.Camera], { onlySubscribed: false });
  const screenRefs = useTracks([Track.Source.ScreenShare], { onlySubscribed: false });
  const micRefs = useTracks([Track.Source.Microphone], { onlySubscribed: false });
  const screenAudioRefs = useTracks([Track.Source.ScreenShareAudio], { onlySubscribed: false });

  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [volumes, setVolumes] = useState<Record<string, number>>({});
  const [screenVolumes, setScreenVolumes] = useState<Record<string, number>>({});
  const [pinnedScreenId, setPinnedScreenId] = useState<string | null>(null);
  const [autoScreenId, setAutoScreenId] = useState<string | null>(null);
  const prevScreenIdsRef = useRef<string[]>([]);
  const [screenBusy, setScreenBusy] = useState(false);
  const [localQuality, setLocalQuality] = useState<LocalQualityState>({
    level: null,
    metrics: EMPTY_METRICS,
    reconnectCount: 0,
  });

  const localCamRef = camRefs.find((r) => r.participant.isLocal);
  const localScreenRef = screenRefs.find((r) => r.participant.isLocal);
  const localMicRef = micRefs.find((r) => r.participant.isLocal);
  const localAudioLevel = useTrackVolume(localMicRef);
  const { quality: localNativeQuality } = useConnectionQualityIndicator({
    participant: localParticipant,
  });
  const selfLevel: QualityLevel =
    connectionState === 'reconnecting'
      ? 'UNKNOWN'
      : mapLiveKitQuality(localNativeQuality) ?? localQuality.level ?? 'UNKNOWN';

  const toggleMic = useCallback(() => {
    void localParticipant.setMicrophoneEnabled(!isMicrophoneEnabled);
  }, [localParticipant, isMicrophoneEnabled]);

  const toggleCam = useCallback(() => {
    void localParticipant.setCameraEnabled(!isCameraEnabled);
  }, [localParticipant, isCameraEnabled]);

  const toggleScreen = useCallback(async () => {
    if (screenBusy) return;
    setScreenBusy(true);
    try {
      await localParticipant.setScreenShareEnabled(!isScreenShareEnabled, { audio: true });
      logger.info({
        event: isScreenShareEnabled
          ? LogEvent.SCREEN_SHARE_STOPPED
          : LogEvent.SCREEN_SHARE_STARTED,
        roomId,
      });
    } finally {
      setScreenBusy(false);
    }
  }, [localParticipant, isScreenShareEnabled, screenBusy, roomId]);

  // Telas: própria (se houver) + remotas — mesmo desenho do PulseRTC (Sprint 16).
  const screens = useMemo(
    () => [
      ...(localScreenRef ? [{ id: 'local', label: 'Sua tela', trackRef: localScreenRef }] : []),
      ...screenRefs
        .filter((r) => !r.participant.isLocal)
        .map((r) => ({
          id: `${r.participant.identity}::screen`,
          label: `Tela de ${r.participant.name || r.participant.identity}`,
          trackRef: r,
        })),
    ],
    [localScreenRef, screenRefs],
  );
  const hasStage = screens.length > 0;

  // Estilo Meet: uma tela no palco por vez, a mais recente de outro participante
  // rouba o palco; a própria fica de miniatura (Sprint 16 §.., ver histórico).
  useEffect(() => {
    const ids = screens.map((s) => s.id);
    const remoteIds = ids.filter((id) => id !== 'local');
    const added = ids.find((id) => !prevScreenIdsRef.current.includes(id));
    prevScreenIdsRef.current = ids;
    setAutoScreenId((cur) => {
      if (added && added !== 'local') return added;
      if (cur && ids.includes(cur) && (cur !== 'local' || remoteIds.length === 0)) return cur;
      return remoteIds[remoteIds.length - 1] ?? ids[ids.length - 1] ?? null;
    });
    setPinnedScreenId((cur) => (cur && ids.includes(cur) ? cur : null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screens.map((s) => s.id).join('|')]);

  const isPinned = pinnedScreenId != null;
  const focusedScreen =
    screens.find((s) => s.id === (pinnedScreenId ?? autoScreenId)) ?? screens[screens.length - 1];
  const otherScreens = screens.filter((s) => s.id !== focusedScreen?.id);

  const tileCount = 1 + remoteParticipants.length;
  const gridCols =
    tileCount <= 1
      ? 'grid-cols-1'
      : tileCount <= 4
        ? 'grid-cols-1 sm:grid-cols-2'
        : 'grid-cols-2 lg:grid-cols-3';

  const status =
    connectionState === 'connected'
      ? 'live'
      : connectionState === 'reconnecting'
        ? 'reconnecting'
        : 'connecting';

  return (
    <div className="flex h-full min-h-0 overflow-hidden bg-slate-950">
      <QualityReporter
        roomId={roomId}
        participantId={participantId}
        resolveSession={resolveSession}
        onChange={setLocalQuality}
      />

      <aside
        className={`flex shrink-0 flex-col border-r border-slate-800 bg-slate-900 transition-[width] duration-150 ${
          sidebarOpen ? 'w-64' : 'w-[72px]'
        }`}
      >
        <div
          className={`flex items-center gap-2 px-3 py-3 ${sidebarOpen ? 'justify-between' : 'justify-center'}`}
        >
          {sidebarOpen && (
            <span className="truncate text-xs font-semibold uppercase tracking-wide text-slate-400">
              Participantes — {remoteParticipants.length + 1}
            </span>
          )}
          <button
            type="button"
            onClick={() => setSidebarOpen((v) => !v)}
            title={sidebarOpen ? 'Recolher lista' : 'Expandir lista'}
            aria-label={sidebarOpen ? 'Recolher lista' : 'Expandir lista'}
            className="flex h-6 w-6 shrink-0 items-center justify-center rounded text-slate-400 hover:bg-slate-800 hover:text-slate-200"
          >
            <span className={`inline-flex transition-transform ${sidebarOpen ? '' : 'rotate-180'}`}>
              <IconChevron />
            </span>
          </button>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto overflow-x-hidden px-2 pb-2">
          {remoteParticipants.map((p) => (
            <RemoteSidebarRow
              key={p.identity}
              participant={p}
              micTrackRef={findTrackRef(micRefs, p.identity)}
              collapsed={!sidebarOpen}
              volume={volumes[p.identity] ?? 1}
              onVolumeChange={(v) => setVolumes((prev) => ({ ...prev, [p.identity]: v }))}
            />
          ))}
        </div>

        <div
          className={`flex items-center gap-2.5 border-t border-slate-800 px-3 py-3 ${sidebarOpen ? '' : 'justify-center'}`}
        >
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-sky-500 text-[11px] font-bold text-white">
            EU
          </span>
          {sidebarOpen && (
            <div className="min-w-0">
              <div className="truncate text-sm font-semibold text-slate-100">Você</div>
              <div className="flex items-center gap-1.5 text-xs text-slate-400">
                <ParticipantMuteIndicator muted={!isMicrophoneEnabled} self />
                <ConnectionSignal quality={selfLevel} variant="compact" name="você" />
              </div>
            </div>
          )}
        </div>
      </aside>

      <div className="relative flex min-h-0 flex-1 flex-col overflow-hidden">
        <div className="flex items-center gap-2 px-4 py-3">
          <CopyLinkButton roomId={roomId} />
          <span
            className={`flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-medium ${
              status === 'live'
                ? 'border-emerald-800/60 bg-emerald-950/50 text-emerald-300'
                : 'border-slate-700 bg-slate-800/80 text-slate-300'
            }`}
          >
            <span className={`h-1.5 w-1.5 rounded-full ${status === 'live' ? 'bg-emerald-400' : 'bg-slate-500'}`} />
            {status === 'live' ? 'Ao vivo' : status === 'reconnecting' ? 'Reconectando...' : 'Conectando...'}
          </span>
          <div className="flex-1" />
        </div>

        <div className="flex min-h-0 flex-1 flex-col">
          <div
            key="stage"
            className={`min-h-0 flex-1 px-4 pb-2 ${hasStage ? 'block' : 'hidden'}`}
          >
            {focusedScreen && (
              <ScreenTile
                key={focusedScreen.id}
                label={focusedScreen.label}
                trackRef={focusedScreen.trackRef}
                pinned={isPinned}
                onClick={
                  screens.length > 1 || isPinned
                    ? () => setPinnedScreenId((p) => (p ? null : focusedScreen.id))
                    : undefined
                }
                volume={focusedScreen.id !== 'local' ? (screenVolumes[focusedScreen.id] ?? 1) : undefined}
                onVolumeChange={
                  focusedScreen.id !== 'local'
                    ? (v) => setScreenVolumes((prev) => ({ ...prev, [focusedScreen.id]: v }))
                    : undefined
                }
              />
            )}
          </div>

          <div
            key="people"
            className={
              hasStage
                ? 'flex h-24 shrink-0 items-stretch gap-2 overflow-x-auto px-4 pb-4 sm:h-32'
                : `grid min-h-0 flex-1 auto-rows-fr gap-2 px-4 pb-4 ${gridCols}`
            }
          >
            {otherScreens.map((s) => (
              <ScreenTile
                key={s.id}
                label={s.label}
                trackRef={s.trackRef}
                compact
                onClick={() => setPinnedScreenId(s.id)}
              />
            ))}
            <Tile
              label="Voce"
              name="Você"
              trackRef={isCameraEnabled ? localCamRef : undefined}
              muted
              quality={selfLevel}
              audioMuted={!isMicrophoneEnabled}
              cameraOff={!isCameraEnabled}
              selfAudio
              compact={hasStage}
              audioLevel={localAudioLevel}
            />
            {remoteParticipants.map((p) => (
              <RemoteTile
                key={p.identity}
                participant={p}
                camTrackRef={findTrackRef(camRefs, p.identity)}
                micTrackRef={findTrackRef(micRefs, p.identity)}
                compact={hasStage}
                volume={volumes[p.identity] ?? 1}
                onVolumeChange={(v) => setVolumes((prev) => ({ ...prev, [p.identity]: v }))}
              />
            ))}
          </div>
        </div>

        {screenAudioRefs
          .filter((r) => !r.participant.isLocal)
          .map((r) => (
            <AudioTrack
              key={r.publication.trackSid}
              trackRef={r}
              volume={screenVolumes[`${r.participant.identity}::screen`] ?? 1}
            />
          ))}

        <QualityPanel
          localLevel={localQuality.level}
          localMetrics={localQuality.metrics}
          localReconnectCount={localQuality.reconnectCount}
        />

        <div className="flex items-center justify-center pb-4 pt-2">
          <div className="flex items-center gap-1.5 rounded-full border border-slate-700 bg-slate-900/90 p-1.5 shadow-lg shadow-black/40">
            <button
              type="button"
              onClick={toggleMic}
              title={isMicrophoneEnabled ? 'Mutar microfone' : 'Ativar microfone'}
              aria-label={isMicrophoneEnabled ? 'Mutar microfone' : 'Ativar microfone'}
              className={`flex h-10 w-10 items-center justify-center rounded-full transition-colors ${
                isMicrophoneEnabled ? 'text-slate-200 hover:bg-slate-800' : 'bg-red-600 text-white hover:bg-red-500'
              }`}
            >
              <IconMic off={!isMicrophoneEnabled} />
            </button>
            <button
              type="button"
              onClick={toggleCam}
              title={isCameraEnabled ? 'Desligar câmera' : 'Ligar câmera'}
              aria-label={isCameraEnabled ? 'Desligar câmera' : 'Ligar câmera'}
              className={`flex h-10 w-10 items-center justify-center rounded-full transition-colors ${
                isCameraEnabled ? 'text-slate-200 hover:bg-slate-800' : 'bg-red-600 text-white hover:bg-red-500'
              }`}
            >
              <IconCamera off={!isCameraEnabled} />
            </button>
            <button
              type="button"
              onClick={() => void toggleScreen()}
              disabled={screenBusy}
              title={isScreenShareEnabled ? 'Parar de compartilhar tela' : 'Compartilhar tela'}
              aria-label={isScreenShareEnabled ? 'Parar de compartilhar tela' : 'Compartilhar tela'}
              className={`flex h-10 w-10 items-center justify-center rounded-full transition-colors disabled:opacity-50 ${
                isScreenShareEnabled ? 'bg-sky-500 text-white hover:bg-sky-400' : 'text-slate-200 hover:bg-slate-800'
              }`}
            >
              <IconScreen />
            </button>
            <div className="mx-1 h-6 w-px bg-slate-700" />
            <button
              type="button"
              onClick={() => {
                void room.disconnect();
                onLeave();
              }}
              title="Sair da chamada"
              className="flex h-10 items-center gap-1.5 rounded-full bg-red-600 px-4 text-sm font-semibold text-white hover:bg-red-500"
            >
              <IconLeave />
              Sair
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export function LiveKitVideoProvider({
  serverUrl,
  token,
  participantId,
  joinConfig,
  resolveSession = true,
  onLeave,
  onError,
}: VideoProviderProps) {
  useEffect(() => {
    logger.info({ event: LogEvent.LIVEKIT_CONNECTING, roomId: joinConfig.roomId });
  }, [joinConfig.roomId]);

  return (
    <div className="h-full" data-lk-theme="default">
      <LiveKitRoom
        serverUrl={serverUrl}
        token={token}
        connect
        audio={joinConfig.microphoneEnabled}
        video={joinConfig.cameraEnabled}
        options={{
          videoCaptureDefaults: joinConfig.cameraId ? { deviceId: joinConfig.cameraId } : undefined,
          audioCaptureDefaults: joinConfig.microphoneId
            ? { deviceId: joinConfig.microphoneId }
            : undefined,
          adaptiveStream: true,
          dynacast: true,
        }}
        onConnected={() =>
          logger.info({ event: LogEvent.LIVEKIT_CONNECTED, roomId: joinConfig.roomId })
        }
        onDisconnected={() => {
          logger.info({ event: LogEvent.LIVEKIT_DISCONNECTED, roomId: joinConfig.roomId });
          onLeave();
        }}
        onError={(err) => {
          logger.error({ event: LogEvent.LIVEKIT_CONNECTION_ERROR, message: err.message });
          onError(err);
        }}
        style={{ height: '100%' }}
      >
        <ConnectionLogger roomId={joinConfig.roomId} />
        <ParticipantSoundNotifier />
        <CustomCallUI
          roomId={joinConfig.roomId}
          participantId={participantId}
          resolveSession={resolveSession}
          onLeave={onLeave}
        />
      </LiveKitRoom>
    </div>
  );
}

const AVATAR_COLORS = ['#4f6f96', '#4a8a7c', '#6a63a6', '#a15c66'];

function avatarColor(identity: string): string {
  let hash = 0;
  for (let i = 0; i < identity.length; i += 1) hash = (hash * 31 + identity.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

function IconChevron() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M15 5l-7 7 7 7" />
    </svg>
  );
}

function IconMic({ off }: { off?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      {off ? (
        <>
          <path d="M12 15a3 3 0 003-3V6a3 3 0 10-6 0v1" />
          <path d="M19 11a7 7 0 01-9.8 6.4M5 11a6.98 6.98 0 001.2 3.9M12 19v3M2 2l20 20" />
        </>
      ) : (
        <>
          <path d="M12 15a3 3 0 003-3V6a3 3 0 10-6 0v6a3 3 0 003 3z" />
          <path d="M19 11a7 7 0 01-14 0M12 19v3" />
        </>
      )}
    </svg>
  );
}

function IconCamera({ off }: { off?: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="5" width="15" height="14" rx="2" />
      <path d="M23 7l-7 5 7 5V7z" />
      {off && <path d="M2 2l20 20" />}
    </svg>
  );
}

function IconScreen() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="4" width="20" height="14" rx="2" />
      <path d="M8 21h8M12 18v3" />
    </svg>
  );
}

function IconLeave() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M16 8V6a2 2 0 00-2-2H4a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2v-2" />
      <path d="M22 12H10m0 0l4-4m-4 4l4 4" />
    </svg>
  );
}

function VolumeSlider({ volume, onChange }: { volume: number; onChange: (v: number) => void }) {
  return (
    <div
      className="flex items-center gap-1.5"
      onClick={(e) => e.stopPropagation()}
      onMouseDown={(e) => e.stopPropagation()}
      onPointerDown={(e) => e.stopPropagation()}
    >
      <span className="text-xs text-white/70">🔈</span>
      <input
        type="range"
        min={0}
        max={2}
        step={0.05}
        value={volume}
        onChange={(e) => onChange(Number(e.target.value))}
        className="h-1 w-20 cursor-pointer accent-white"
        aria-label="Volume"
      />
      <span className="w-8 text-right text-[11px] text-white/70">
        {Math.round(volume * 100)}%
      </span>
    </div>
  );
}

/** Medidor de volume: 4 barrinhas que acendem conforme o nível 0..1. */
function AudioMeter({ level, className = '' }: { level: number; className?: string }) {
  const bars = 4;
  const lit = Math.ceil(level * bars);
  return (
    <span className={`flex h-3 items-end gap-[1.5px] ${className}`} aria-hidden>
      {Array.from({ length: bars }).map((_, i) => (
        <span
          key={i}
          className={`w-[2px] rounded-full transition-colors ${i < lit ? 'bg-green-400' : 'bg-white/25'}`}
          style={{ height: `${35 + i * 22}%` }}
        />
      ))}
    </span>
  );
}

function ScreenTile({
  label,
  trackRef,
  compact = false,
  onClick,
  pinned = false,
  volume,
  onVolumeChange,
}: {
  label: string;
  trackRef?: TrackReference;
  compact?: boolean;
  /** miniatura: traz pro palco (fixa). palco: alterna fixar/soltar. */
  onClick?: () => void;
  pinned?: boolean;
  volume?: number;
  onVolumeChange?: (v: number) => void;
}) {
  const hint = compact ? 'Fixar no palco' : pinned ? 'Soltar' : 'Fixar';
  const cls = `group relative overflow-hidden rounded-lg border bg-black ${
    pinned ? 'border-sky-400' : 'border-sky-500/40'
  } ${
    compact ? 'aspect-video h-full shrink-0' : 'h-full w-full'
  } ${onClick ? 'hover:ring-2 hover:ring-sky-400' : ''}`;
  const inner = (
    <>
      {trackRef && (
        <VideoTrack
          trackRef={trackRef}
          className="absolute inset-0 h-full w-full object-contain"
        />
      )}
      <span
        className={`absolute flex items-center gap-1 rounded bg-slate-900/75 text-white ${
          compact ? 'bottom-1 left-1 px-1.5 py-0.5 text-[10px]' : 'bottom-2 left-2 gap-1.5 px-2 py-1 text-xs'
        }`}
      >
        🖥 {label}
      </span>
      {pinned && !compact && (
        <span className="absolute right-2 top-2 rounded bg-sky-500 px-2 py-1 text-xs font-medium text-white">
          📌 Fixada
        </span>
      )}
      {onClick && (
        <span className="absolute inset-0 hidden items-center justify-center bg-black/40 text-xs font-medium text-white group-hover:flex">
          📌 {hint}
        </span>
      )}
      {!compact && onVolumeChange && volume !== undefined && (
        <div className="absolute bottom-10 right-2 z-10">
          <div className="rounded bg-black/75 px-2 py-1">
            <VolumeSlider volume={volume} onChange={onVolumeChange} />
          </div>
        </div>
      )}
    </>
  );
  return (
    <div
      className={cls}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      title={onClick ? hint : undefined}
      onClick={onClick}
      onKeyDown={
        onClick
          ? (e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault();
                onClick();
              }
            }
          : undefined
      }
    >
      {inner}
    </div>
  );
}

function Tile({
  label,
  name,
  trackRef,
  muted = false,
  quality = 'UNKNOWN',
  audioMuted,
  cameraOff = false,
  selfAudio = false,
  compact = false,
  volume = 1,
  onVolumeChange,
  audioLevel = 0,
}: {
  label: string;
  name: string;
  trackRef?: TrackReference;
  muted?: boolean;
  quality?: QualityLevel;
  audioMuted?: boolean;
  cameraOff?: boolean;
  selfAudio?: boolean;
  compact?: boolean;
  volume?: number;
  onVolumeChange?: (v: number) => void;
  audioLevel?: number;
}) {
  const level = quality;
  const problem = level === 'POOR' || level === 'UNSTABLE' ? remoteQualityDescription(level) : null;
  const displayName = name || label;
  const initial = displayName.trim().charAt(0).toUpperCase() || '?';
  const speaking = audioLevel > 0.12;

  return (
    <div
      className={`group relative overflow-hidden rounded-lg bg-black ${
        compact ? 'aspect-video h-full shrink-0' : 'h-full min-h-0 w-full'
      } ${speaking ? 'ring-2 ring-green-400' : ''}`}
    >
      {trackRef && !cameraOff && (
        <VideoTrack
          trackRef={trackRef}
          muted={muted}
          className={'absolute inset-0 h-full w-full object-contain'}
        />
      )}
      {cameraOff && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-slate-800 px-2 text-center">
          <span
            className={`flex items-center justify-center rounded-full bg-slate-600 font-semibold text-white ${
              compact ? 'h-8 w-8 text-sm' : 'h-16 w-16 text-2xl'
            }`}
          >
            {initial}
          </span>
          {!compact && (
            <span className="max-w-full truncate text-sm font-medium text-slate-200">{displayName}</span>
          )}
        </div>
      )}
      <span
        className={`absolute bottom-1 left-1 flex items-center gap-1 rounded bg-black/60 px-1.5 py-0.5 text-white ${
          compact ? 'text-[10px]' : 'bottom-2 left-2 gap-1.5 px-2 py-1 text-xs'
        }`}
      >
        <ParticipantMuteIndicator muted={audioMuted} name={name} self={selfAudio} />
        {audioMuted !== true && <AudioMeter level={audioLevel} />}
        {compact ? name : label}
      </span>
      <span
        className={`absolute rounded bg-black/60 ${compact ? 'right-1 top-1 px-1 py-0.5' : 'bottom-2 right-2 px-2 py-1'}`}
      >
        <ConnectionSignal quality={level} variant="compact" name={name} hint={problem} />
      </span>
      {problem && !compact && (
        <span className="absolute left-2 top-2 max-w-[75%] rounded bg-black/70 px-2 py-1 text-[11px] text-amber-200">
          {level === 'POOR' ? 'Conexão ruim' : 'Conexão instável'}
        </span>
      )}
      {!compact && !muted && onVolumeChange && (
        <div className="absolute bottom-10 right-2 z-10 opacity-0 transition-opacity group-hover:opacity-100">
          <div className="rounded bg-black/75 px-2 py-1">
            <VolumeSlider volume={volume} onChange={onVolumeChange} />
          </div>
        </div>
      )}
    </div>
  );
}
