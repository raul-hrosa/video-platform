import { describe, expect, it, vi } from 'vitest';
import { EMPTY_METRICS } from '../../types/connectionQuality';
import { collectLocalMetrics, collectRemoteMetrics, mapLiveKitQuality } from './livekitMetrics';

describe('mapLiveKitQuality', () => {
  it('maps the LiveKit states to the platform scale', () => {
    expect(mapLiveKitQuality('excellent' as never)).toBe('EXCELLENT');
    expect(mapLiveKitQuality('good' as never)).toBe('GOOD');
    expect(mapLiveKitQuality('poor' as never)).toBe('POOR');
    expect(mapLiveKitQuality('lost' as never)).toBe('POOR');
    expect(mapLiveKitQuality('unknown' as never)).toBeNull();
  });
});

describe('collectLocalMetrics', () => {
  function fakeRoom(videoStats: Record<string, number> | null) {
    const videoTrack = videoStats
      ? { getSenderStats: vi.fn().mockResolvedValue([videoStats]), currentBitrate: 1_800_000 }
      : undefined;
    return {
      state: 'connected',
      localParticipant: {
        videoTrackPublications: new Map(videoTrack ? [['v', { track: videoTrack }]] : []),
        audioTrackPublications: new Map(),
      },
    } as never;
  }

  it('aggregates sender stats (seconds -> ms, loss %)', async () => {
    const m = await collectLocalMetrics(
      fakeRoom({
        roundTripTime: 0.072,
        jitter: 0.012,
        packetsLost: 8,
        packetsSent: 992,
        frameWidth: 1280,
        frameHeight: 720,
        framesPerSecond: 30,
      }),
    );
    expect(m.rttMs).toBe(72);
    expect(m.jitterMs).toBe(12);
    expect(m.packetLossPercent).toBe(0.8);
    expect(m.videoWidth).toBe(1280);
    expect(m.videoFps).toBe(30);
    expect(m.videoBitrate).toBe(1_800_000);
  });

  it('leaves fields null when there is no track', async () => {
    const m = await collectLocalMetrics(fakeRoom(null));
    expect(m.rttMs).toBeNull();
    expect(m.packetLossPercent).toBeNull();
    expect(m.connectionState).toBe('connected');
  });

  it('returns EMPTY_METRICS shape when there is no local participant', async () => {
    const m = await collectLocalMetrics({ state: 'connecting', localParticipant: undefined } as never);
    expect(m).toMatchObject({ ...EMPTY_METRICS, connectionState: 'connecting' });
  });
});

describe('collectRemoteMetrics', () => {
  it('reads receiver stats (jitter, loss, resolution) but no RTT', async () => {
    const videoTrack = {
      getReceiverStats: vi.fn().mockResolvedValue({
        jitter: 0.02,
        packetsLost: 5,
        packetsReceived: 995,
        frameWidth: 640,
        frameHeight: 480,
      }),
      currentBitrate: 900_000,
    };
    const participant = {
      videoTrackPublications: new Map([['v', { track: videoTrack }]]),
      audioTrackPublications: new Map(),
    } as never;

    const m = await collectRemoteMetrics(participant);
    expect(m.rttMs).toBeNull(); // nao existe RTT por participante
    expect(m.jitterMs).toBe(20);
    expect(m.packetLossPercent).toBe(0.5);
    expect(m.videoWidth).toBe(640);
    expect(m.videoBitrate).toBe(900_000);
  });
});
