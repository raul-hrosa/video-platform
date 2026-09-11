import { useCallback, useEffect, useState } from 'react';
import { LogEvent, logger } from '../services/logger';
import type { DeviceOption, FriendlyError, MediaDevicesState } from '../types';
import { toFriendlyMediaError } from './mediaErrors';

function mapDevices(devices: MediaDeviceInfo[], kind: MediaDeviceKind): DeviceOption[] {
  return devices
    .filter((d) => d.kind === kind && d.deviceId)
    .map((d, i) => ({
      deviceId: d.deviceId,
      label: d.label || `${labelPrefix(kind)} ${i + 1}`,
    }));
}

function labelPrefix(kind: MediaDeviceKind): string {
  return kind === 'videoinput' ? 'Camera' : 'Microfone';
}

interface UseMediaDevices {
  devices: MediaDevicesState;
  permissionGranted: boolean;
  error: FriendlyError | null;
  requestAccess: () => Promise<boolean>;
  refresh: () => Promise<void>;
}

/**
 * Solicita permissao de camera/microfone e lista os dispositivos disponiveis.
 * Trata os erros comuns com mensagens amigaveis.
 */
export function useMediaDevices(): UseMediaDevices {
  const [devices, setDevices] = useState<MediaDevicesState>({ cameras: [], microphones: [] });
  const [permissionGranted, setPermissionGranted] = useState(false);
  const [error, setError] = useState<FriendlyError | null>(null);

  const refresh = useCallback(async () => {
    const list = await navigator.mediaDevices.enumerateDevices();
    setDevices({
      cameras: mapDevices(list, 'videoinput'),
      microphones: mapDevices(list, 'audioinput'),
    });
  }, []);

  const requestAccess = useCallback(async (): Promise<boolean> => {
    setError(null);
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      stream.getTracks().forEach((t) => t.stop());
      setPermissionGranted(true);
      logger.info({ event: LogEvent.VIDEO_PERMISSION_GRANTED });
      logger.info({ event: LogEvent.AUDIO_PERMISSION_GRANTED });
      await refresh();
      return true;
    } catch (err) {
      setPermissionGranted(false);
      const friendly = toFriendlyMediaError(err);
      setError(friendly);
      logger.warn({ event: LogEvent.VIDEO_PERMISSION_DENIED, code: friendly.code });
      logger.warn({ event: LogEvent.AUDIO_PERMISSION_DENIED, code: friendly.code });
      return false;
    }
  }, [refresh]);

  useEffect(() => {
    const handler = () => {
      void refresh();
    };
    navigator.mediaDevices.addEventListener('devicechange', handler);
    return () => navigator.mediaDevices.removeEventListener('devicechange', handler);
  }, [refresh]);

  return { devices, permissionGranted, error, requestAccess, refresh };
}
