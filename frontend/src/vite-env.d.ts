/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_LIVEKIT_URL?: string;
  readonly VITE_API_URL?: string;
  readonly VITE_MEDIA_PROVIDER?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
