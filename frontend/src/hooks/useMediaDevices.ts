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

    // Tenta câmera + microfone juntos primeiro (caminho feliz).
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
      stream.getTracks().forEach((t) => t.stop());
      setPermissionGranted(true);
      logger.info({ event: LogEvent.VIDEO_PERMISSION_GRANTED });
      logger.info({ event: LogEvent.AUDIO_PERMISSION_GRANTED });
      await refresh();
      return true;
    } catch (combinedErr) {
      const errName = combinedErr instanceof DOMException ? combinedErr.name : '';
      // Permissão negada — inútil tentar separado.
      if (errName === 'NotAllowedError' || errName === 'SecurityError') {
        setPermissionGranted(false);
        const friendly = toFriendlyMediaError(combinedErr);
        setError(friendly);
        logger.warn({ event: LogEvent.VIDEO_PERMISSION_DENIED, code: friendly.code });
        return false;
      }

      // Dispositivo não encontrado ou em uso — tenta cada um separado.
      // Permite entrar só com áudio (sem câmera) ou só com câmera (sem mic).
      let gotAny = false;
      try {
        const vs = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
        vs.getTracks().forEach((t) => t.stop());
        gotAny = true;
        logger.info({ event: LogEvent.VIDEO_PERMISSION_GRANTED });
      } catch { /* sem câmera */ }
      try {
        const as = await navigator.mediaDevices.getUserMedia({ video: false, audio: true });
        as.getTracks().forEach((t) => t.stop());
        gotAny = true;
        logger.info({ event: LogEvent.AUDIO_PERMISSION_GRANTED });
      } catch { /* sem microfone */ }

      if (gotAny) {
        setPermissionGranted(true);
        await refresh();
        return true;
      }

      setPermissionGranted(false);
      const friendly = toFriendlyMediaError(combinedErr);
      setError(friendly);
      logger.warn({ event: LogEvent.VIDEO_PERMISSION_DENIED, code: friendly.code });
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
