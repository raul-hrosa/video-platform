import { LiveKitVideoProvider } from '../video/livekit/LiveKitVideoProvider';
import type { VideoProviderProps } from '../video/VideoProvider';

/**
 * Fronteira unica entre a aplicacao e o provider de midia. A app inteira fala
 * apenas {@link VideoProviderProps} (Sprint 11 §13); aqui escolhemos a
 * implementacao ativa a partir de {@code VITE_MEDIA_PROVIDER}. LiveKit e o
 * unico provider hoje — plugar outro no futuro e adicionar um branch aqui
 * (e a config em {@code config.ts}) sem tocar no resto da app.
 */
export function CallRoom(props: VideoProviderProps) {
  return <LiveKitVideoProvider {...props} />;
}
