/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_LIVEKIT_URL?: string;
  readonly VITE_API_URL?: string;
  readonly VITE_MEDIA_PROVIDER?: string;
  readonly VITE_PULSERTC_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
