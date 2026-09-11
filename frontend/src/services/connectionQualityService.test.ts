import { describe, expect, it } from 'vitest';
import { EMPTY_METRICS } from '../types/connectionQuality';
import { classify } from './connectionQualityService';

describe('classify', () => {
  it('classifies each level from RTT alone', () => {
    expect(classify({ ...EMPTY_METRICS, rttMs: 50 })).toBe('EXCELLENT');
    expect(classify({ ...EMPTY_METRICS, rttMs: 120 })).toBe('GOOD');
    expect(classify({ ...EMPTY_METRICS, rttMs: 200 })).toBe('UNSTABLE');
    expect(classify({ ...EMPTY_METRICS, rttMs: 350 })).toBe('POOR');
    expect(classify({ ...EMPTY_METRICS, rttMs: 500 })).toBe('POOR');
  });

  it('uses the worst metric', () => {
    expect(classify({ ...EMPTY_METRICS, rttMs: 40, packetLossPercent: 20 })).toBe('POOR');
  });

  it('defaults to UNSTABLE when no metrics are available', () => {
    expect(classify(EMPTY_METRICS)).toBe('UNSTABLE');
  });

  it('does not let jitter alone drop below UNSTABLE', () => {
    expect(classify({ ...EMPTY_METRICS, jitterMs: 500 })).toBe('UNSTABLE');
  });
});
