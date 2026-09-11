import { describe, expect, it } from 'vitest';

/**
 * Guarda arquitetural (Sprint 15): imports do SDK PulseRTC (`@pulsertc/client`)
 * só podem aparecer dentro de `src/video/pulsertc/`. `CallRoom` e todo o resto
 * da UI dependem da interface local `VideoProvider` e dos modelos da plataforma
 * (`types/connectionQuality`), nunca do SDK.
 */
const ALLOWED_PREFIX = '/src/video/pulsertc/';

const modules = import.meta.glob('/src/**/*.{ts,tsx}', { query: '?raw', import: 'default', eager: true });
const IMPORT_RE = /from\s+['"]@pulsertc\/client['"]/;

describe('PulseRTC SDK boundary', () => {
  it('só é importado dentro de src/video/pulsertc/', () => {
    expect(Object.keys(modules).length).toBeGreaterThan(20);

    const offenders = Object.entries(modules)
      .filter(([path]) => !path.endsWith('.test.ts') && !path.endsWith('.test.tsx'))
      .filter(([, src]) => IMPORT_RE.test(src as string))
      .map(([path]) => path)
      .filter((path) => !path.startsWith(ALLOWED_PREFIX));

    expect(offenders).toEqual([]);
  });
});
