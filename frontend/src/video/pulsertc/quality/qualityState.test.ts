import { describe, expect, it } from 'vitest';
import { applyBreakdown, applyQualityEvent, mergeBreakdown, qualityFor, resetQualityState } from './qualityState';
import type { QualityEvent } from './types';

describe('applyBreakdown (§16)', () => {
  it('mapeia a escala do backend e associa ao participante certo (§19)', () => {
    const state = applyBreakdown([
      { participantRef: 'user:1.aaaa', level: 'GOOD', score: 88 },
      {
        participantRef: 'guest:2.bbbb',
        level: 'POOR',
        score: 40,
        reason: 'HIGH_PACKET_LOSS',
        audio: { level: 'GOOD' },
        video: { level: 'POOR', metrics: { packetLossPct: 4.2 } },
      },
    ]);
    expect(state['user:1.aaaa'].level).toBe('GOOD');
    expect(state['guest:2.bbbb']).toMatchObject({
      level: 'POOR',
      reason: 'HIGH_PACKET_LOSS',
      audio: { level: 'GOOD' },
      video: { level: 'POOR', metrics: { packetLossPct: 4.2 } },
    });
  });
});

describe('applyQualityEvent (eventos push do PulseRTC, §19)', () => {
  it('aplica ao participante do evento e não mexe nos outros', () => {
    const prev = applyBreakdown([{ participantRef: 'user:remote.x', level: 'GOOD' }]);
    const evt: QualityEvent = {
      type: 'quality_degraded',
      participantId: 'user:me.y',
      mediaType: 'video',
      status: 'POOR',
      reason: 'HIGH_JITTER',
    };
    const next = applyQualityEvent(prev, evt);
    expect(next['user:remote.x'].level).toBe('GOOD');
    expect(next['user:me.y'].video).toMatchObject({ level: 'POOR', reason: 'HIGH_JITTER' });
    // sem evento de connection ainda → overall = pior entre áudio/vídeo
    expect(next['user:me.y'].level).toBe('POOR');
  });

  it('mediaType connection define o nível overall', () => {
    let s = applyQualityEvent({}, {
      type: 'quality_degraded',
      participantId: 'p1',
      mediaType: 'video',
      status: 'POOR',
    });
    s = applyQualityEvent(s, {
      type: 'quality_changed',
      participantId: 'p1',
      mediaType: 'connection',
      status: 'WARNING',
      reason: 'HIGH_RTT',
    });
    expect(s['p1'].level).toBe('UNSTABLE'); // = connection, não o vídeo POOR
    expect(s['p1'].reason).toBe('HIGH_RTT');
  });

  it('evento sem participantId é ignorado', () => {
    const prev = {};
    expect(applyQualityEvent(prev, { type: 'quality_changed', status: 'POOR' })).toBe(prev);
  });
});

describe('mergeBreakdown (catch-up)', () => {
  it('funde score do GET sem apagar o veredito vivo dos eventos', () => {
    const live = applyQualityEvent({}, {
      type: 'quality_degraded',
      participantId: 'p1',
      mediaType: 'connection',
      status: 'POOR',
      reason: 'HIGH_PACKET_LOSS',
    });
    const merged = mergeBreakdown(live, [{ participantRef: 'p1', level: 'GOOD', score: 55 }]);
    expect(merged['p1'].score).toBe(55);
    expect(merged['p1'].level).toBe('POOR'); // evento vivo vence
    expect(merged['p1'].reason).toBe('HIGH_PACKET_LOSS');
  });

  it('adiciona participante que só aparece no GET', () => {
    const merged = mergeBreakdown({}, [{ participantRef: 'novo', level: 'GOOD', score: 90 }]);
    expect(merged['novo']).toMatchObject({ level: 'GOOD', score: 90 });
  });
});

describe('qualityFor', () => {
  it('casa pela identidade exata ou pelo prefixo <sub>', () => {
    const state = applyBreakdown([{ participantRef: 'user:1.aaaa', level: 'GOOD' }]);
    expect(qualityFor(state, 'user:1.aaaa')?.level).toBe('GOOD');
    expect(qualityFor(state, 'user:1.zzzz')?.level).toBe('GOOD'); // mesmo sub, hex diferente
    expect(qualityFor(state, 'user:9.aaaa')).toBeUndefined();
  });
});

describe('resetQualityState (§20)', () => {
  it('zera o estado numa nova PeerConnection', () => {
    expect(resetQualityState()).toEqual({});
  });
});
