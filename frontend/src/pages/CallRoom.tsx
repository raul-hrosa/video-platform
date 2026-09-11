import { config } from '../config';
import { LiveKitVideoProvider } from '../video/livekit/LiveKitVideoProvider';
import { PulseRtcVideoProvider } from '../video/pulsertc/PulseRtcVideoProvider';
import type { VideoProviderProps } from '../video/VideoProvider';

/**
 * Fronteira unica entre a aplicacao e o provider de midia. A app inteira fala
 * apenas {@link VideoProviderProps} (Sprint 11 §13); aqui escolhemos a
 * implementacao ativa a partir de {@code VITE_MEDIA_PROVIDER}.
 */
export function CallRoom(props: VideoProviderProps) {
  if (config.mediaProvider === 'pulsertc') {
    return <PulseRtcVideoProvider {...props} />;
  }
  return <LiveKitVideoProvider {...props} />;
}
