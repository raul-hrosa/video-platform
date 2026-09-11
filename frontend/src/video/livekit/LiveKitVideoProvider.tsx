import {
  LiveKitRoom,
  useConnectionState,
  useRoomContext,
  VideoConference,
} from '@livekit/components-react';
import type { RoomOptions } from 'livekit-client';
import { useEffect, useMemo } from 'react';
import { CopyLinkButton } from '../../components/CopyLinkButton';
import { QualityPanel } from './QualityPanel';
import { useConnectionQuality } from './useConnectionQuality';
import { useSessionResolver } from '../../hooks/useSessionResolver';
import { LogEvent, logger } from '../../services/logger';
import type { VideoProviderProps } from '../VideoProvider';

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

function QualityMonitor({
  roomId,
  participantId,
  resolveSession,
}: {
  roomId: string;
  participantId: string;
  resolveSession: boolean;
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

  return (
    <QualityPanel
      localLevel={level}
      localMetrics={metrics}
      localReconnectCount={reconnectCount}
    />
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
  const roomOptions = useMemo<RoomOptions>(
    () => ({
      videoCaptureDefaults: joinConfig.cameraId ? { deviceId: joinConfig.cameraId } : undefined,
      audioCaptureDefaults: joinConfig.microphoneId
        ? { deviceId: joinConfig.microphoneId }
        : undefined,
      adaptiveStream: true,
      dynacast: true,
    }),
    [joinConfig.cameraId, joinConfig.microphoneId],
  );

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
        options={roomOptions}
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
        <div className="relative h-full">
          <VideoConference />
          <QualityMonitor
            roomId={joinConfig.roomId}
            participantId={participantId}
            resolveSession={resolveSession}
          />
          <div className="absolute left-3 top-3 z-10">
            <CopyLinkButton roomId={joinConfig.roomId} />
          </div>
        </div>
      </LiveKitRoom>
    </div>
  );
}
