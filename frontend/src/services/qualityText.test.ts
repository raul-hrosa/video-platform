import { describe, expect, it } from 'vitest';
import {
  friendlyReason,
  remoteQualityDescription,
  SIGNAL_BARS,
  SIGNAL_LABEL,
} from './qualityText';

describe('qualityText (Sprint 13 §3, §6)', () => {
  it('mapa de barras e labels bate com a tabela §3', () => {
    expect(SIGNAL_BARS.EXCELLENT).toBe('▂▄▆█');
    expect(SIGNAL_BARS.GOOD).toBe('▂▄▆_');
    expect(SIGNAL_BARS.UNSTABLE).toBe('▂▄__');
    expect(SIGNAL_BARS.POOR).toBe('▂___');
    expect(SIGNAL_BARS.UNKNOWN).toBe('____');
    expect(SIGNAL_LABEL.UNKNOWN).toBe('Verificando conexão...');
  });

  it('friendlyReason traduz códigos conhecidos e nunca vaza o código cru', () => {
    expect(friendlyReason('HIGH_JITTER')).toBe('Sua conexão está instável e pode causar atrasos.');
    expect(friendlyReason('SESSION_RECOVERY')).toBe('Reconectando à chamada...');
    const unknown = friendlyReason('SOME_NEW_CODE');
    expect(unknown).not.toContain('SOME_NEW_CODE');
    expect(unknown).toBe('Sua conexão está apresentando instabilidade.');
    expect(friendlyReason(null)).toBeNull();
  });

  it('remoteQualityDescription só descreve problema (POOR/UNSTABLE)', () => {
    expect(remoteQualityDescription('POOR')).toMatch(/interrupções/);
    expect(remoteQualityDescription('UNSTABLE')).toMatch(/atrasos/);
    expect(remoteQualityDescription('GOOD')).toBeNull();
    expect(remoteQualityDescription('EXCELLENT')).toBeNull();
  });
});
