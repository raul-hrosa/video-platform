import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ConnectionSignal } from './ConnectionSignal';

describe('ConnectionSignal (Sprint 13 §3, §10, §15)', () => {
  it.each([
    ['EXCELLENT', 'Excelente'],
    ['GOOD', 'Boa'],
    ['UNSTABLE', 'Instável'],
    ['POOR', 'Ruim'],
    ['UNKNOWN', 'Verificando conexão...'],
  ] as const)('%s → "%s" no modo completo', (quality, text) => {
    render(<ConnectionSignal quality={quality} />);
    expect(screen.getByText(text)).toBeInTheDocument();
  });

  it('expõe role=status e aria-label textual (não só cor)', () => {
    render(<ConnectionSignal quality="UNSTABLE" name="Pedro" />);
    const el = screen.getByRole('status');
    expect(el).toHaveAttribute('aria-label', 'Conexão de Pedro: Instável');
  });

  it('modo compacto não mostra o label textual', () => {
    render(<ConnectionSignal quality="EXCELLENT" variant="compact" />);
    expect(screen.queryByText('Excelente')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', 'Conexão: Excelente');
  });

  it('usa o hint (mensagem amigável do reason) no tooltip', () => {
    render(<ConnectionSignal quality="POOR" hint="Sua conexão está apresentando instabilidade." />);
    expect(screen.getByRole('status').getAttribute('title')).toContain(
      'Sua conexão está apresentando instabilidade.',
    );
  });
});
