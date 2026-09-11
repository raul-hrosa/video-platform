import { describe, expect, it } from 'vitest';
import { STATUS_LABEL, dashboardBounds, formatDuration, elapsedSince } from './format';

describe('formatDuration', () => {
  it('formats minutes and seconds', () => {
    expect(formatDuration(19 * 60 + 4)).toBe('19m 04s');
  });

  it('formats hours and minutes', () => {
    expect(formatDuration(4 * 3600 + 32 * 60)).toBe('4h 32m');
  });

  it('renders a dash for null / negative', () => {
    expect(formatDuration(null)).toBe('—');
    expect(formatDuration(-5)).toBe('—');
  });
});

describe('status labels', () => {
  it('maps every status to a readable pt-BR label', () => {
    expect(STATUS_LABEL.WAITING).toBe('Aguardando');
    expect(STATUS_LABEL.ACTIVE).toBe('Em andamento');
    expect(STATUS_LABEL.ENDED).toBe('Encerrada');
    expect(STATUS_LABEL.EXPIRED).toBe('Expirada');
  });
});

describe('elapsedSince', () => {
  it('is null when never started (expired room)', () => {
    expect(elapsedSince(null)).toBeNull();
  });
});

describe('dashboardBounds', () => {
  it('returns midnight-based ISO bounds with weekStart <= todayStart', () => {
    const { todayStart, weekStart } = dashboardBounds(new Date('2026-08-29T15:00:00'));
    expect(new Date(weekStart).getTime()).toBeLessThanOrEqual(new Date(todayStart).getTime());
  });
});
