import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { QualityLevel } from '../../../types/connectionQuality';
import { useStableQuality } from './useStableQuality';

describe('useStableQuality (Sprint 13 §13)', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('não troca por oscilação curta entre GOOD e UNSTABLE', () => {
    const { result, rerender } = renderHook(({ q }) => useStableQuality(q, 5000), {
      initialProps: { q: 'GOOD' } as { q: QualityLevel },
    });
    expect(result.current).toBe('GOOD');

    rerender({ q: 'UNSTABLE' });
    act(() => void vi.advanceTimersByTime(2000));
    expect(result.current).toBe('GOOD'); // ainda não

    rerender({ q: 'GOOD' } as { q: QualityLevel }); // voltou antes do dwell
    act(() => void vi.advanceTimersByTime(5000));
    expect(result.current).toBe('GOOD');
  });

  it('commita a mudança quando ela persiste pelo dwell', () => {
    const { result, rerender } = renderHook(({ q }) => useStableQuality(q, 5000), {
      initialProps: { q: 'GOOD' } as { q: QualityLevel },
    });
    rerender({ q: 'POOR' });
    act(() => void vi.advanceTimersByTime(5000));
    expect(result.current).toBe('POOR');
  });

  it('UNKNOWN passa direto nos dois sentidos (§12)', () => {
    const { result, rerender } = renderHook(({ q }) => useStableQuality(q, 5000), {
      initialProps: { q: 'GOOD' } as { q: QualityLevel },
    });
    rerender({ q: 'UNKNOWN' });
    expect(result.current).toBe('UNKNOWN'); // sem esperar

    rerender({ q: 'GOOD' } as { q: QualityLevel });
    expect(result.current).toBe('GOOD');
  });
});
