import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { EMPTY_METRICS, type QualityLevel } from '../../types/connectionQuality';
import { ConnectionQualityIndicator } from './ConnectionQualityIndicator';

function filledBars(): number {
  const rects = document.querySelectorAll('svg rect');
  const empty = '#475569';
  return [...rects].filter((r) => r.getAttribute('fill') !== empty).length;
}

describe('ConnectionQualityIndicator', () => {
  const cases: Array<[QualityLevel, number, string]> = [
    ['EXCELLENT', 5, 'excelente'],
    ['GOOD', 4, 'boa'],
    ['UNSTABLE', 2, 'instavel'],
    ['POOR', 1, 'ruim'],
    ['UNKNOWN', 0, 'desconhecida'],
  ];

  it.each(cases)('%s fills %d bars and labels "%s"', (level, bars, label) => {
    render(<ConnectionQualityIndicator level={level} />);
    expect(screen.getByRole('img')).toHaveAttribute(
      'aria-label',
      `Qualidade da conexao: ${label}`,
    );
    expect(filledBars()).toBe(bars);
  });

  it('shows a measuring state when level is null', () => {
    render(<ConnectionQualityIndicator level={null} />);
    expect(screen.getByRole('img')).toHaveAttribute('aria-label', 'Qualidade da conexao: medindo');
    expect(filledBars()).toBe(0);
  });

  it('opens the tooltip on focus and shows metrics, missing ones as dash', async () => {
    render(
      <ConnectionQualityIndicator
        level="GOOD"
        metrics={{ ...EMPTY_METRICS, rttMs: 72, packetLossPercent: 0.8 }}
        reconnectCount={1}
      />,
    );
    await userEvent.tab();
    const tip = await screen.findByRole('tooltip');
    expect(tip).toHaveTextContent('72 ms');
    expect(tip).toHaveTextContent('0.8%');
    expect(tip).toHaveTextContent('Jitter');
    expect(tip).toHaveTextContent('—'); // jitter ausente
    expect(tip).toHaveTextContent('1'); // reconexoes
  });

  it('marks metrics measured from the received stream', async () => {
    render(
      <ConnectionQualityIndicator
        level="POOR"
        metrics={{ ...EMPTY_METRICS, jitterMs: 40, packetLossPercent: 6 }}
        metricsSource="recepcao"
      />,
    );
    await userEvent.tab();
    const tip = await screen.findByRole('tooltip');
    expect(tip).toHaveTextContent('40 ms');
    expect(tip).toHaveTextContent('medido na sua recepcao');
    // RTT por participante nao existe na recepcao
    expect(tip).toHaveTextContent('Latencia');
  });

  it('shows a fallback when there are no metrics', async () => {
    render(<ConnectionQualityIndicator level="GOOD" />);
    await userEvent.tab();
    const tip = await screen.findByRole('tooltip');
    expect(tip).toHaveTextContent('Sem metricas detalhadas.');
  });
});
