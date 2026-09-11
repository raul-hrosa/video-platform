import { describe, expect, it } from 'vitest';

/**
 * Guarda arquitetural (Sprint 10 §40.6, endurecida na Sprint 10.5 §4):
 * imports do SDK LiveKit (`@livekit/*` ou `livekit-client`) só podem aparecer
 * dentro de `src/video/livekit/`. `CallRoom` e todo o resto da UI dependem da
 * interface local `VideoProvider` e dos modelos da plataforma
 * (`types/connectionQuality`), nunca do SDK.
 *
 * A coleta de métricas WebRTC específicas do LiveKit vive em
 * `src/video/livekit/livekitMetrics.ts` e converte para `ConnectionMetrics` /
 * `QualityLevel` na borda.
 */
const ALLOWED_PREFIX = '/src/video/livekit/';

const modules = import.meta.glob('/src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true });
const IMPORT_RE = /from\s+['"](@livekit\/[^'"]+|livekit-client)['"]/;

describe('LiveKit SDK boundary', () => {
  it('só é importado dentro de src/video/livekit/', () => {
    // sanidade: o glob precisa ter varrido a árvore de fato
    expect(Object.keys(modules).length).toBeGreaterThan(20);

    const offenders = Object.entries(modules)
      .filter(([path]) => !path.endsWith('.test.ts') && !path.endsWith('.test.tsx'))
      .filter(([, src]) => IMPORT_RE.test(src as string))
      .map(([path]) => path)
      .filter((path) => !path.startsWith(ALLOWED_PREFIX));

    expect(offenders).toEqual([]);
  });
});
