import { useEffect, useRef, useState } from 'react';
import type { FriendlyError } from '../types';
import { toFriendlyMediaError } from './mediaErrors';

interface PreviewOptions {
  cameraId?: string;
  microphoneId?: string;
  cameraEnabled: boolean;
  microphoneEnabled: boolean;
  active: boolean;
}

/**
 * Mantem um MediaStream local para o preview antes de entrar na chamada.
 * Reage a troca de dispositivo e ao liga/desliga de camera e microfone.
 * O preview NAO envia nada para nenhuma sala.
 */
export function useCameraPreview(opts: PreviewOptions) {
  const { cameraId, microphoneId, cameraEnabled, microphoneEnabled, active } = opts;
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [error, setError] = useState<FriendlyError | null>(null);

  useEffect(() => {
    let cancelled = false;

    function stop() {
      streamRef.current?.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
      if (videoRef.current) videoRef.current.srcObject = null;
    }

    async function start() {
      stop();
      if (!active) return;

      const constraints: MediaStreamConstraints = {
        video: cameraEnabled
          ? cameraId
            ? { deviceId: { ideal: cameraId } }
            : true
          : false,
        audio: microphoneEnabled
          ? microphoneId
            ? { deviceId: { ideal: microphoneId } }
            : true
          : false,
      };

      if (!constraints.video && !constraints.audio) {
        setError(null);
        return;
      }

      try {
        const stream = await navigator.mediaDevices.getUserMedia(constraints);
        if (cancelled) {
          stream.getTracks().forEach((t) => t.stop());
          return;
        }
        streamRef.current = stream;
        setError(null);
        if (videoRef.current) videoRef.current.srcObject = stream;
      } catch (err) {
        if (!cancelled) setError(toFriendlyMediaError(err));
      }
    }

    void start();
    return () => {
      cancelled = true;
      stop();
    };
  }, [cameraId, microphoneId, cameraEnabled, microphoneEnabled, active]);

  return { videoRef, error };
}
