import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ConnectionMetrics } from '../../types/connectionQuality';
import { EMPTY_METRICS } from '../../types/connectionQuality';

const collectLocalMetrics = vi.fn();
const postQualitySnapshot = vi.fn();
const loggerInfo = vi.fn();
const loggerWarn = vi.fn();

vi.mock('./livekitMetrics', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./livekitMetrics')>();
  return { ...actual, collectLocalMetrics: (...a: unknown[]) => collectLocalMetrics(...a) };
});
vi.mock('../../services/api', () => ({
  postQualitySnapshot: (...a: unknown[]) => postQualitySnapshot(...a),
}));
vi.mock('../../services/logger', () => ({
  LogEvent: new Proxy({}, { get: (_t, k) => String(k) }),
  logger: {
    info: (...a: unknown[]) => loggerInfo(...a),
    warn: (...a: unknown[]) => loggerWarn(...a),
    error: vi.fn(),
    debug: vi.fn(),
  },
}));

// eslint-disable-next-line import/first
import { useConnectionQuality } from './useConnectionQuality';

function fakeRoom() {
  return {
    state: 'connected',
    on: vi.fn(),
    off: vi.fn(),
    localParticipant: {},
  } as never;
}

function metrics(rttMs: number): ConnectionMetrics {
  return { ...EMPTY_METRICS, rttMs };
}

describe('useConnectionQuality', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    collectLocalMetrics.mockReset().mockResolvedValue(metrics(50)); // EXCELLENT
    postQualitySnapshot.mockReset().mockResolvedValue({ id: 'x', qualityLevel: 'EXCELLENT' });
    loggerInfo.mockReset();
    loggerWarn.mockReset();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  const params = {
    roomId: 'room-1',
    participantId: 'user-1',
    sessionId: 'sess-1',
    connected: true,
  };

  it('starts the monitor and posts a snapshot', async () => {
    const room = fakeRoom();
    renderHook(() => useConnectionQuality({ room, ...params }));

    expect(loggerInfo).toHaveBeenCalledWith(
      expect.objectContaining({ event: 'CONNECTION_MONITOR_STARTED' }),
    );

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(collectLocalMetrics).toHaveBeenCalled();
    expect(postQualitySnapshot).toHaveBeenCalledWith(
      'room-1',
      'sess-1',
      expect.objectContaining({ participantId: 'user-1', qualityLevel: 'EXCELLENT' }),
    );
  });

  it('measures but does not POST until the session is resolved', async () => {
    const room = fakeRoom();
    const { result } = renderHook(() =>
      useConnectionQuality({ room, ...params, sessionId: null }),
    );
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.level).toBe('EXCELLENT'); // indicador funciona
    expect(postQualitySnapshot).not.toHaveBeenCalled(); // mas nao persiste
  });

  it('applies hysteresis: needs two consecutive divergent readings to change', async () => {
    collectLocalMetrics
      .mockResolvedValueOnce(metrics(50)) // EXCELLENT (inicial)
      .mockResolvedValue(metrics(300)); // POOR sustentado

    const room = fakeRoom();
    const { result } = renderHook(() => useConnectionQuality({ room, ...params }));

    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    expect(result.current.level).toBe('EXCELLENT');

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(result.current.level).toBe('EXCELLENT'); // 1a leitura POOR: nao muda

    await act(async () => {
      await vi.advanceTimersByTimeAsync(10_000);
    });
    expect(result.current.level).toBe('POOR'); // 2a leitura POOR: confirma
  });

  it('stops the monitor on unmount', async () => {
    const room = fakeRoom();
    const { unmount } = renderHook(() => useConnectionQuality({ room, ...params }));
    await act(async () => {
      await vi.advanceTimersByTimeAsync(0);
    });
    unmount();
    expect(loggerInfo).toHaveBeenCalledWith(
      expect.objectContaining({ event: 'CONNECTION_MONITOR_STOPPED' }),
    );
  });
});
