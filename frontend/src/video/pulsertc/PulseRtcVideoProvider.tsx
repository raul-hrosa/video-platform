import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ConnectionError,
  ConnectionState,
  Room,
  RoomEvent,
  TokenError,
  type Participant,
  type ParticipantQuality as SdkParticipantQuality,
  type RemoteParticipant,
  type RemotePublication,
} from '@pulsertc/client';
import { ConnectionSignal } from '../../components/ConnectionSignal';
import { CopyLinkButton } from '../../components/CopyLinkButton';
import { ParticipantMuteIndicator } from '../../components/ParticipantMuteIndicator';
import { fetchMediaQuality, fetchParticipantNames } from '../../services/api';
import { LogEvent, logger } from '../../services/logger';
import { friendlyReason, remoteQualityDescription } from '../../services/qualityText';
import type { QualityLevel } from '../../types/connectionQuality';
import type { VideoProviderProps } from '../VideoProvider';
import { useAudioLevel } from './audioLevel';
import { labelForIdentity } from './identity';
import { DiagnosticPanel } from './quality/DiagnosticPanel';
import { useStableQuality } from './quality/useStableQuality';
import {
  applyQualityEvent,
  mergeBreakdown,
  qualityFor,
  resetQualityState,
  type QualityState,
} from './quality/qualityState';
import type { ParticipantQuality } from './quality/types';

/** Rede de segurança — a fonte primária são os eventos `QualityChanged` do SDK. */
const QUALITY_CATCHUP_MS = 30_000;

/** `true` = mutado, `false` = ativo, `undefined` = ainda não sabemos (§16). */
type AudioMutedState = Record<string, boolean>;

/** `true` = câmera desligada (publication de vídeo mutada), keyed por identidade. */
type VideoOffState = Record<string, boolean>;

function stateFor<T>(state: Record<string, T>, identity: string): T | undefined {
  if (identity in state) return state[identity];
  const sub = identity.split('.')[0];
  const hit = Object.entries(state).find(([k]) => k.split('.')[0] === sub);
  return hit?.[1];
}

/**
 * Cada participante remoto tem até duas "faixas" de mídia: `cam` (câmera + mic
 * juntos num MediaStream) e `screen` (compartilhamento de tela, uma publication
 * de vídeo separada com `source === 'screen'`). A chave do stream é
 * `identidade :: faixa`.
 */
type Lane = 'cam' | 'screen';
const SEP = '::';
const laneOf = (pub: RemotePublication): Lane => (pub.source === 'screen' ? 'screen' : 'cam');
const keyOf = (identity: string, lane: Lane) => `${identity}${SEP}${lane}`;

const audioMutedFor = (state: AudioMutedState, identity: string) => stateFor(state, identity);

interface RemoteStream {
  identity: string;
  lane: Lane;
  stream: MediaStream;
}

/**
 * Implementação PulseRTC do contrato {@link VideoProviderProps} (Sprint 11 §13).
 *
 * Sprint 15: a fronteira WebSocket/WebRTC é o SDK oficial `@pulsertc/client`
 * (`Room`). O provider traduz eventos do SDK para o modelo da plataforma
 * (`QualityLevel`, indicadores de mute) e monta a UI. Reconexão, "perfect
 * negotiation" e o `quality_report` (QoE) são do SDK.
 *
 * Sprint 16: compartilhamento de tela — `startScreenShare()` /
 * `stopScreenShare()` do SDK; a tela é uma faixa de mídia à parte da câmera
 * (`publication.source === 'screen'`), renderizada num "palco" acima da grade.
 */
export function PulseRtcVideoProvider({
  serverUrl,
  token,
  joinConfig,
  onLeave,
  onError,
}: VideoProviderProps) {
  const roomRef = useRef<Room | null>(null);
  const streamsRef = useRef<Map<string, MediaStream>>(new Map());
  const qualityPollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  /** desregistra o listener 'ended' da track de tela (botão nativo do browser). */
  const screenCleanupRef = useRef<(() => void) | null>(null);
  /** publicationId -> faixa em que a track foi colocada (p/ o unsubscribe). */
  const pubLaneRef = useRef<Record<string, Lane>>({});

  const [localCamStream, setLocalCamStream] = useState<MediaStream | null>(null);
  const [localScreenStream, setLocalScreenStream] = useState<MediaStream | null>(null);
  const [remoteStreams, setRemoteStreams] = useState<RemoteStream[]>([]);
  const [names, setNames] = useState<Record<string, string>>({});
  const [quality, setQuality] = useState<QualityState>({});
  const [audioMuted, setAudioMuted] = useState<AudioMutedState>({});
  const [videoOff, setVideoOff] = useState<VideoOffState>({});
  const [selfId, setSelfId] = useState('');
  const [status, setStatus] = useState<'connecting' | 'live' | 'reconnecting'>('connecting');
  const [showDiagnostics, setShowDiagnostics] = useState(false);
  const [micOn, setMicOn] = useState(joinConfig.microphoneEnabled);
  const [camOn, setCamOn] = useState(joinConfig.cameraEnabled);
  const [screenOn, setScreenOn] = useState(false);
  const [screenBusy, setScreenBusy] = useState(false);
  /** tela em foco automático (a mais recente) quando ninguém fixou nada. */
  const [autoScreenId, setAutoScreenId] = useState<string | null>(null);
  /** tela que o usuário fixou explicitamente (📌). `null` = segue o automático. */
  const [pinnedScreenId, setPinnedScreenId] = useState<string | null>(null);
  const prevScreenIdsRef = useRef<string[]>([]);

  const teardown = useCallback(() => {
    if (qualityPollRef.current) {
      clearInterval(qualityPollRef.current);
      qualityPollRef.current = null;
    }
    void roomRef.current?.disconnect();
    roomRef.current = null;
    streamsRef.current.clear();
  }, []);

  useEffect(() => {
    let cancelled = false;
    logger.info({ event: LogEvent.PULSERTC_CONNECTING, roomId: joinConfig.roomId });

    const refreshNames = () => {
      fetchParticipantNames(joinConfig.roomId)
        .then((map) => {
          if (!cancelled) setNames(map ?? {});
        })
        .catch(() => undefined);
    };

    // Catch-up: o veredito ao vivo vem dos eventos `QualityChanged` do SDK; o GET
    // só preenche score + estado de quem entrou antes de nós (funde, não substitui).
    const refreshQuality = () => {
      fetchMediaQuality(joinConfig.roomId)
        .then((rows) => {
          if (!cancelled) setQuality((prev) => mergeBreakdown(prev, rows ?? []));
        })
        .catch(() => undefined);
    };

    const syncRemoteStreams = () => {
      setRemoteStreams(
        [...streamsRef.current.entries()].map(([key, stream]) => {
          const [identity, lane] = key.split(SEP);
          return { identity, lane: lane as Lane, stream };
        }),
      );
    };

    const streamFor = (identity: string, lane: Lane): MediaStream => {
      const key = keyOf(identity, lane);
      let s = streamsRef.current.get(key);
      if (!s) {
        s = new MediaStream();
        streamsRef.current.set(key, s);
      }
      return s;
    };

    const dropParticipant = (identity: string) => {
      for (const key of [...streamsRef.current.keys()]) {
        if (key.startsWith(`${identity}${SEP}`)) streamsRef.current.delete(key);
      }
    };

    // O SDK precisa de contexto seguro + APIs de browser. A seleção de
    // dispositivo entra pelo seam `getUserMedia`; `getDisplayMedia` (screen
    // share) fica no default do SDK.
    const room = new Room(
      { qoe: true },
      {
        getUserMedia: (constraints) =>
          navigator.mediaDevices.getUserMedia({
            audio: joinConfig.microphoneId
              ? { deviceId: { exact: joinConfig.microphoneId } }
              : constraints.audio,
            video: joinConfig.cameraId
              ? { deviceId: { exact: joinConfig.cameraId } }
              : constraints.video,
          }),
      },
    );
    roomRef.current = room;

    room.on(RoomEvent.ConnectionStateChanged, (state) => {
      if (state === ConnectionState.Connected) setStatus('live');
      else if (state === ConnectionState.Reconnecting) {
        setStatus('reconnecting');
        setQuality(resetQualityState()); // §20: sem falso positivo durante recovery
        // O browser quase nunca deixa a track do getDisplayMedia sobreviver a uma
        // nova PeerConnection — o SDK descarta o screen share no reconnect.
        setScreenOn(false);
        setLocalScreenStream(null);
        logger.warn({ event: LogEvent.PARTICIPANT_RECONNECTING, roomId: joinConfig.roomId });
      } else if (state === ConnectionState.Connecting) setStatus('connecting');
    });

    room.on(RoomEvent.Disconnected, (reason) => {
      logger.info({ event: LogEvent.PULSERTC_DISCONNECTED, roomId: joinConfig.roomId, reason });
      if (!cancelled && reason !== 'client') onLeave();
    });

    room.on(RoomEvent.Error, (err) => {
      logger.warn({ event: LogEvent.PULSERTC_CONNECTION_FAILED, roomId: joinConfig.roomId, message: err.message });
    });

    room.on(RoomEvent.ParticipantConnected, () => {
      refreshNames();
      refreshQuality();
    });

    room.on(RoomEvent.ParticipantDisconnected, (participant: RemoteParticipant) => {
      dropParticipant(participant.identity);
      syncRemoteStreams();
      const drop = (prev: Record<string, boolean>) => {
        const next = { ...prev };
        delete next[participant.identity];
        return next;
      };
      setAudioMuted(drop);
      setVideoOff(drop);
    });

    room.on(RoomEvent.TrackSubscribed, (track, publication: RemotePublication, participant: RemoteParticipant) => {
      let lane = laneOf(publication);
      // Fallback: se o servidor não marcou `source: "screen"` mas o participante
      // já tem um vídeo na faixa de câmera, esta 2ª faixa de vídeo é a tela.
      if (lane === 'cam' && track.kind === 'video') {
        const cam = streamsRef.current.get(keyOf(participant.identity, 'cam'));
        if (cam && cam.getVideoTracks().length > 0) lane = 'screen';
      }
      pubLaneRef.current[publication.id] = lane;
      const stream = streamFor(participant.identity, lane);
      stream.getTracks().filter((t) => t.kind === track.kind).forEach((t) => stream.removeTrack(t));
      stream.addTrack(track);
      syncRemoteStreams();
      refreshNames();
      // Indicadores (mic mudo / câmera off) só valem p/ a faixa de câmera — o
      // áudio/vídeo da tela compartilhada tem `source: "screen"` e é ignorado.
      if (lane === 'cam' && publication.kind === 'audio') {
        setAudioMuted((prev) => ({ ...prev, [participant.identity]: publication.muted }));
      } else if (lane === 'cam' && publication.kind === 'video') {
        setVideoOff((prev) => ({ ...prev, [participant.identity]: publication.muted }));
      }
    });

    room.on(RoomEvent.TrackUnsubscribed, (publication: RemotePublication, participant: RemoteParticipant) => {
      const lane = pubLaneRef.current[publication.id] ?? laneOf(publication);
      delete pubLaneRef.current[publication.id];
      const key = keyOf(participant.identity, lane);
      const stream = streamsRef.current.get(key);
      if (stream) {
        stream.getTracks().filter((t) => t.kind === publication.kind).forEach((t) => stream.removeTrack(t));
        if (stream.getTracks().length === 0) streamsRef.current.delete(key);
      }
      syncRemoteStreams();
    });

    room.on(RoomEvent.TrackMuted, (publication: RemotePublication, participant: RemoteParticipant) => {
      if ((pubLaneRef.current[publication.id] ?? laneOf(publication)) !== 'cam') return;
      if (publication.kind === 'audio') {
        setAudioMuted((prev) => ({ ...prev, [participant.identity]: publication.muted }));
      } else {
        setVideoOff((prev) => ({ ...prev, [participant.identity]: publication.muted }));
      }
    });

    room.on(RoomEvent.QualityChanged, (q: SdkParticipantQuality, participant: Participant) => {
      setQuality((prev) =>
        applyQualityEvent(prev, {
          type: 'quality_changed',
          participantId: participant.identity,
          direction: q.direction,
          mediaType: q.mediaType,
          from: q.from,
          status: q.status,
          reason: q.reason,
        }),
      );
      logger.info({
        event: LogEvent.QUALITY_STATE_CHANGED,
        provider: 'pulsertc',
        roomId: joinConfig.roomId,
        participantId: participant.identity,
        mediaType: q.mediaType,
        status: q.status,
        reason: q.reason,
      });
    });

    async function start() {
      try {
        await room.connect(serverUrl, token, joinConfig.roomId);
      } catch (err) {
        if (cancelled) return;
        if (err instanceof TokenError) {
          logger.error({ event: LogEvent.PULSERTC_CONNECTION_FAILED, roomId: joinConfig.roomId, code: err.code });
          onError(err);
        } else if (err instanceof ConnectionError) {
          logger.error({ event: LogEvent.PULSERTC_CONNECTION_FAILED, roomId: joinConfig.roomId });
          onError(err);
        } else {
          onError(err instanceof Error ? err : new Error('pulsertc connect failed'));
        }
        return;
      }
      if (cancelled) {
        void room.disconnect();
        return;
      }

      setSelfId(room.localParticipant.identity);
      setStatus('live');
      logger.info({ event: LogEvent.PULSERTC_ROOM_JOINED, roomId: joinConfig.roomId });
      refreshNames();
      refreshQuality();
      if (!qualityPollRef.current) {
        qualityPollRef.current = setInterval(refreshQuality, QUALITY_CATCHUP_MS);
      }

      try {
        await room.localParticipant.enableCameraAndMicrophone();
        if (cancelled) return;
        if (!joinConfig.microphoneEnabled) await room.localParticipant.setMicrophoneEnabled(false);
        if (!joinConfig.cameraEnabled) await room.localParticipant.setCameraEnabled(false);
        const camPubs = [...room.localParticipant.publications.values()].filter(
          (p) => p.source !== 'screen',
        );
        // vídeo p/ o preview + áudio p/ o medidor de volume (o `<video>` local
        // fica `muted`, então o áudio não gera eco).
        const tracks = [
          camPubs.find((p) => p.kind === 'video')?.track,
          camPubs.find((p) => p.kind === 'audio')?.track,
        ].filter((t): t is MediaStreamTrack => !!t);
        if (tracks.length) setLocalCamStream(new MediaStream(tracks));
      } catch (err) {
        if (!cancelled) onError(err instanceof Error ? err : new Error('media access denied'));
      }
    }

    void start();
    return () => {
      cancelled = true;
      teardown();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [serverUrl, token, joinConfig.roomId]);

  const toggleMic = () => {
    const next = !micOn;
    setMicOn(next);
    void roomRef.current?.localParticipant.setMicrophoneEnabled(next);
  };
  const toggleCam = () => {
    const next = !camOn;
    setCamOn(next);
    void roomRef.current?.localParticipant.setCameraEnabled(next);
  };

  const stopScreenShare = useCallback(async () => {
    const room = roomRef.current;
    screenCleanupRef.current?.();
    screenCleanupRef.current = null;
    setScreenOn(false);
    setLocalScreenStream(null);
    try {
      await room?.localParticipant.stopScreenShare();
    } catch {
      /* já parado */
    }
    logger.info({ event: LogEvent.PULSERTC_SCREEN_SHARE_STOPPED, roomId: joinConfig.roomId });
  }, [joinConfig.roomId]);

  const toggleScreen = useCallback(async () => {
    const room = roomRef.current;
    if (!room || screenBusy) return;
    if (screenOn) {
      await stopScreenShare();
      return;
    }
    setScreenBusy(true);
    try {
      // `startScreenShare` só resolve quando o SFU devolve o `publication_added`
      // da tela. Se esse eco não vier (ou vier sem `source: "screen"`), não
      // travamos o botão: seguimos com o que o SDK já tem (`isScreenSharing`).
      // Sem args: o SDK pede `{ video: true, audio: true }` — captura o áudio
      // da aba/tela quando o usuário marca "compartilhar áudio" no Chromium.
      await Promise.race([
        room.localParticipant.startScreenShare(),
        new Promise((resolve) => setTimeout(resolve, 8000)),
      ]);

      if (!room.localParticipant.isScreenSharing) {
        // usuário cancelou o prompt, ou o publish falhou de verdade
        setLocalScreenStream(null);
        setScreenOn(false);
        return;
      }

      // Track da tela: da publication (caminho feliz) ou, como fallback, o
      // sender de vídeo do PC que não é a câmera (escape hatch `room.engine`).
      const camTrack = [...room.localParticipant.publications.values()].find(
        (p) => p.kind === 'video' && p.source !== 'screen',
      )?.track;
      const track =
        [...room.localParticipant.publications.values()].find(
          (p) => p.source === 'screen' && p.kind === 'video',
        )?.track ??
        room.engine.pc
          ?.getSenders()
          .map((s) => s.track)
          .find((t): t is MediaStreamTrack => !!t && t.kind === 'video' && t !== camTrack);

      if (track) {
        setLocalScreenStream(new MediaStream([track]));
        // O usuário pode encerrar pelo botão nativo do browser — o SDK já
        // despublica sozinho (track.onended); aqui só sincronizamos o React.
        const onEnded = () => void stopScreenShare();
        track.addEventListener('ended', onEnded);
        screenCleanupRef.current = () => track.removeEventListener('ended', onEnded);
      }
      setScreenOn(true);
      logger.info({ event: LogEvent.PULSERTC_SCREEN_SHARE_STARTED, roomId: joinConfig.roomId });
    } catch (err) {
      await room.localParticipant.stopScreenShare().catch(() => undefined);
      setLocalScreenStream(null);
      setScreenOn(false);
      logger.warn({
        event: LogEvent.PULSERTC_SCREEN_SHARE_STOPPED,
        roomId: joinConfig.roomId,
        message: err instanceof Error ? err.message : 'screen share falhou',
      });
    } finally {
      setScreenBusy(false);
    }
  }, [screenOn, screenBusy, stopScreenShare, joinConfig.roomId]);

  const selfQuality = selfId ? qualityFor(quality, selfId) : undefined;
  const rawSelfLevel: QualityLevel =
    status === 'reconnecting' ? 'UNKNOWN' : selfQuality?.level ?? 'UNKNOWN';
  // §13: histerese só na apresentação — não altera a classificação oficial.
  const selfLevel = useStableQuality(rawSelfLevel);

  const nameFor = (identity: string, i: number) =>
    names[identity.split('.')[0]] ?? names[identity] ?? labelForIdentity(identity, i);

  const camStreams = remoteStreams.filter((r) => r.lane === 'cam');
  const remotes = camStreams.map(({ identity, stream }, i) => ({
    stream,
    name: nameFor(identity, i),
    quality: qualityFor(quality, identity),
    audioMuted: audioMutedFor(audioMuted, identity),
    cameraOff: stateFor(videoOff, identity) === true || stream.getVideoTracks().length === 0,
  }));

  // Todas as telas compartilhadas (própria + remotas). `id` estável p/ o foco.
  const screens: { id: string; label: string; stream: MediaStream }[] = [
    ...(localScreenStream
      ? [{ id: 'local', label: 'Sua tela', stream: localScreenStream }]
      : []),
    ...remoteStreams
      .filter((r) => r.lane === 'screen')
      .map(({ identity, stream }, i) => ({
        id: stream.id,
        label: `Tela de ${nameFor(identity, i)}`,
        stream,
      })),
  ];
  const hasStage = screens.length > 0;

  // Estilo Meet: uma tela no palco por vez. Padrão automático = a tela de OUTRO
  // participante mais recente (a própria tela fica de miniatura — ninguém quer
  // ficar se olhando compartilhar); o usuário pode fixar (📌) qualquer uma.
  // A fixação some quando aquela tela encerra.
  useEffect(() => {
    const ids = screens.map((s) => s.id);
    const remoteIds = ids.filter((id) => id !== 'local');
    const added = ids.find((id) => !prevScreenIdsRef.current.includes(id));
    prevScreenIdsRef.current = ids;
    setAutoScreenId((cur) => {
      if (added && added !== 'local') return added; // tela nova de outro rouba o palco
      if (cur && ids.includes(cur) && (cur !== 'local' || remoteIds.length === 0)) return cur;
      return remoteIds[remoteIds.length - 1] ?? ids[ids.length - 1] ?? null;
    });
    setPinnedScreenId((cur) => (cur && ids.includes(cur) ? cur : null));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [screens.map((s) => s.id).join('|')]);

  const isPinned = pinnedScreenId != null;
  const focusedScreen =
    screens.find((s) => s.id === (pinnedScreenId ?? autoScreenId)) ??
    screens[screens.length - 1];
  const otherScreens = screens.filter((s) => s.id !== focusedScreen?.id);

  const tileCount = 1 + remotes.length;
  const gridCols =
    tileCount <= 1
      ? 'grid-cols-1'
      : tileCount <= 4
        ? 'grid-cols-1 sm:grid-cols-2'
        : 'grid-cols-2 lg:grid-cols-3';

  return (
    <div className="relative flex h-full min-h-0 flex-col overflow-hidden bg-slate-950">
      <div className="flex min-h-0 flex-1 flex-col">
        {/* Palco do compartilhamento de tela (estilo Meet): ocupa a área
            principal. Sempre montado — só escondido quando ninguém compartilha —
            para a fita de pessoas abaixo não remontar. */}
        <div
          key="stage"
          className={`min-h-0 flex-1 p-2 ${hasStage ? 'block' : 'hidden'}`}
        >
          {focusedScreen && (
            <ScreenTile
              key={focusedScreen.id}
              label={focusedScreen.label}
              stream={focusedScreen.stream}
              pinned={isPinned}
              // só dá pra "fixar" quando existe mais de uma tela p/ escolher
              onClick={
                screens.length > 1 || isPinned
                  ? () => setPinnedScreenId((p) => (p ? null : focusedScreen.id))
                  : undefined
              }
            />
          )}
        </div>

        {/* Pessoas: grade quando ninguém compartilha; fita horizontal de
            miniaturas quando há um palco. As telas fora do palco entram na fita
            como miniaturas clicáveis (clicar troca o palco). */}
        <div
          key="people"
          className={
            hasStage
              ? 'flex h-24 shrink-0 items-stretch gap-2 overflow-x-auto px-2 pb-2 sm:h-32'
              : `grid min-h-0 flex-1 auto-rows-fr gap-2 p-2 ${gridCols}`
          }
        >
          {otherScreens.map((s) => (
            <ScreenTile
              key={s.id}
              label={s.label}
              stream={s.stream}
              compact
              onClick={() => setPinnedScreenId(s.id)}
            />
          ))}
          <Tile
            label="Voce"
            stream={localCamStream}
            muted
            level={selfLevel}
            name="Você"
            audioMuted={!micOn}
            cameraOff={!camOn}
            selfAudio
            compact={hasStage}
          />
          {remotes.map(({ stream, name, quality: q, audioMuted: am, cameraOff }) => (
            <Tile
              key={stream.id}
              label={name}
              name={name}
              stream={stream}
              quality={q}
              audioMuted={am}
              cameraOff={cameraOff}
              compact={hasStage}
            />
          ))}
        </div>
      </div>

      {/* Áudio da tela compartilhada (aba/sistema) — elemento dedicado sempre
          montado, independente de a tela estar no palco ou em miniatura, p/ o
          som não cortar quando o layout muda. Só telas remotas: nunca a própria
          (evita eco). */}
      {screens
        .filter((s) => s.id !== 'local' && s.stream.getAudioTracks().length > 0)
        .map((s) => (
          <ScreenAudio key={s.id} stream={s.stream} />
        ))}

      <div className="absolute left-3 top-3 z-10 flex items-center gap-2">
        <CopyLinkButton roomId={joinConfig.roomId} />
        <span className="rounded bg-slate-800/80 px-2 py-1 text-xs text-slate-300">
          {status === 'live' ? 'ao vivo' : status === 'reconnecting' ? 'Reconectando...' : 'conectando...'}
        </span>
        <button
          type="button"
          onClick={() => setShowDiagnostics((v) => !v)}
          className="rounded bg-slate-800/80 px-2 py-1 text-xs text-slate-400 hover:text-slate-200"
          title="Modo diagnóstico"
        >
          diagnóstico
        </button>
      </div>

      {showDiagnostics && (
        <DiagnosticPanel self={selfQuality} onClose={() => setShowDiagnostics(false)} />
      )}

      {/* Painel de qualidade — linguagem humana, sem score/bitrate (§4, §5, §8) */}
      <div className="border-t border-slate-800 bg-slate-900 px-4 py-2 text-sm text-slate-300">
        <div className="flex flex-wrap items-center gap-x-6 gap-y-1">
          <span className="flex items-center gap-2">
            <span className="text-slate-400">Minha conexão</span>
            <ParticipantMuteIndicator muted={!micOn} self />
            <ConnectionSignal
              quality={selfLevel}
              name="você"
              hint={friendlyReason(selfQuality?.reason)}
            />
          </span>
          {remotes.length > 0 && (
            <span className="flex flex-wrap items-center gap-x-4 gap-y-1">
              <span className="text-slate-400">Participantes</span>
              {remotes.map(({ stream, name, quality: q, audioMuted: am }) => (
                <span key={stream.id} className="flex items-center gap-1.5">
                  <ParticipantMuteIndicator muted={am} name={name} />
                  <span className="text-slate-300">{name}</span>
                  <ConnectionSignal
                    quality={q?.level ?? 'UNKNOWN'}
                    variant="compact"
                    name={name}
                    hint={q ? remoteQualityDescription(q.level) : null}
                  />
                </span>
              ))}
            </span>
          )}
        </div>
      </div>

      <div className="flex items-center justify-center gap-3 border-t border-slate-800 bg-slate-900 p-3">
        <button
          type="button"
          onClick={toggleMic}
          className={`rounded-lg px-4 py-2 text-sm font-medium ${micOn ? 'bg-slate-700 text-white' : 'bg-red-600 text-white'}`}
        >
          {micOn ? 'Mutar' : 'Ativar audio'}
        </button>
        <button
          type="button"
          onClick={toggleCam}
          className={`rounded-lg px-4 py-2 text-sm font-medium ${camOn ? 'bg-slate-700 text-white' : 'bg-red-600 text-white'}`}
        >
          {camOn ? 'Desligar video' : 'Ligar video'}
        </button>
        <button
          type="button"
          onClick={() => void toggleScreen()}
          disabled={screenBusy}
          className={`rounded-lg px-4 py-2 text-sm font-medium disabled:opacity-50 ${screenOn ? 'bg-indigo-600 text-white' : 'bg-slate-700 text-white'}`}
        >
          {screenOn ? 'Parar de compartilhar' : 'Compartilhar tela'}
        </button>
        <button
          type="button"
          onClick={() => {
            teardown();
            onLeave();
          }}
          className="rounded-lg bg-red-600 px-4 py-2 text-sm font-semibold text-white"
        >
          Sair
        </button>
      </div>
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

/** Áudio da tela compartilhada (aba/sistema). Elemento à parte do `<video>` p/
 *  o som não cortar quando a tela troca de palco↔miniatura. */
function ScreenAudio({ stream }: { stream: MediaStream }) {
  const ref = useRef<HTMLAudioElement>(null);
  useEffect(() => {
    if (ref.current) ref.current.srcObject = stream;
  }, [stream]);
  return <audio ref={ref} autoPlay className="hidden" />;
}

function ScreenTile({
  label,
  stream,
  videoRef,
  compact = false,
  onClick,
  pinned = false,
}: {
  label: string;
  stream?: MediaStream;
  videoRef?: React.RefObject<HTMLVideoElement>;
  compact?: boolean;
  /** miniatura: traz pro palco (fixa). palco: alterna fixar/soltar. */
  onClick?: () => void;
  pinned?: boolean;
}) {
  const ownRef = useRef<HTMLVideoElement>(null);
  const ref = videoRef ?? ownRef;
  useEffect(() => {
    if (ref.current && stream) ref.current.srcObject = stream;
  }, [ref, stream]);

  // medidor só no palco (não nas miniaturas) e só se a tela traz áudio
  const hasAudio = !compact && (stream?.getAudioTracks().length ?? 0) > 0;
  const audioLevel = useAudioLevel(hasAudio ? stream ?? null : null, hasAudio);

  const hint = compact ? 'Fixar no palco' : pinned ? 'Soltar' : 'Fixar';
  const cls = `group relative overflow-hidden rounded-lg border bg-black ${
    pinned ? 'border-indigo-400' : 'border-indigo-500/40'
  } ${
    compact ? 'aspect-video h-full shrink-0' : 'h-full w-full'
  } ${onClick ? 'hover:ring-2 hover:ring-indigo-400' : ''}`;
  const inner = (
    <>
      <video
        ref={ref}
        autoPlay
        playsInline
        muted
        className="absolute inset-0 h-full w-full object-contain"
      />
      <span
        className={`absolute flex items-center gap-1 rounded bg-indigo-900/70 text-white ${
          compact ? 'bottom-1 left-1 px-1.5 py-0.5 text-[10px]' : 'bottom-2 left-2 gap-1.5 px-2 py-1 text-xs'
        }`}
      >
        🖥 {label}
        {hasAudio && <AudioMeter level={audioLevel} className="ml-0.5" />}
      </span>
      {pinned && !compact && (
        <span className="absolute right-2 top-2 rounded bg-indigo-500 px-2 py-1 text-xs font-medium text-white">
          📌 Fixada
        </span>
      )}
      {onClick && (
        <span className="absolute inset-0 hidden items-center justify-center bg-black/40 text-xs font-medium text-white group-hover:flex">
          📌 {hint}
        </span>
      )}
    </>
  );
  return onClick ? (
    <button type="button" onClick={onClick} title={hint} className={cls}>
      {inner}
    </button>
  ) : (
    <div className={cls}>{inner}</div>
  );
}

function Tile({
  label,
  name,
  stream,
  videoRef,
  muted = false,
  quality,
  level: levelOverride,
  audioMuted,
  cameraOff = false,
  selfAudio = false,
  compact = false,
}: {
  label: string;
  name: string;
  stream: MediaStream | null;
  videoRef?: React.RefObject<HTMLVideoElement>;
  muted?: boolean;
  quality?: ParticipantQuality;
  level?: QualityLevel;
  audioMuted?: boolean;
  cameraOff?: boolean;
  selfAudio?: boolean;
  compact?: boolean;
}) {
  const ownRef = useRef<HTMLVideoElement>(null);
  const ref = videoRef ?? ownRef;
  useEffect(() => {
    if (ref.current && stream) ref.current.srcObject = stream;
  }, [ref, stream]);

  const stableRemote = useStableQuality(quality?.level ?? 'UNKNOWN');
  const level: QualityLevel = levelOverride ?? stableRemote;
  const problem = level === 'POOR' || level === 'UNSTABLE' ? remoteQualityDescription(level) : null;
  const displayName = name || label;
  const initial = displayName.trim().charAt(0).toUpperCase() || '?';

  const audioLevel = useAudioLevel(stream, audioMuted !== true);
  const speaking = audioLevel > 0.12;

  return (
    <div
      className={`relative overflow-hidden rounded-lg bg-black ${
        compact ? 'aspect-video h-full shrink-0' : 'h-full min-h-0 w-full'
      } ${speaking ? 'ring-2 ring-green-400' : ''}`}
    >
      <video
        ref={ref}
        autoPlay
        playsInline
        muted={muted}
        className={`absolute inset-0 h-full w-full object-contain ${cameraOff ? 'invisible' : ''}`}
      />
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
    </div>
  );
}
