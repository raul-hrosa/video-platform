import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ParticipantMuteIndicator } from './ParticipantMuteIndicator';

describe('ParticipantMuteIndicator (Sprint 14 §7, §14, §15, §16)', () => {
  it('mutado → 🔇 + aria-label', () => {
    render(<ParticipantMuteIndicator muted name="João" />);
    const el = screen.getByRole('img');
    expect(el).toHaveTextContent('🔇');
    expect(el).toHaveAttribute('aria-label', 'Microfone mutado');
    expect(el).toHaveAttribute('title', 'O microfone de João está mutado');
  });

  it('ativo → 🎤', () => {
    render(<ParticipantMuteIndicator muted={false} name="Maria" />);
    expect(screen.getByRole('img')).toHaveTextContent('🎤');
    expect(screen.getByRole('img')).toHaveAttribute('aria-label', 'Microfone ativado');
  });

  it('self usa 1ª pessoa no tooltip', () => {
    render(<ParticipantMuteIndicator muted self />);
    expect(screen.getByRole('img')).toHaveAttribute('title', 'Microfone mutado');
  });

  it('estado desconhecido (undefined) não renderiza nada (§16)', () => {
    const { container } = render(<ParticipantMuteIndicator muted={undefined} />);
    expect(container).toBeEmptyDOMElement();
  });
});
