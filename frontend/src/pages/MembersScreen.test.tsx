import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const { listOrgMembers } = vi.hoisted(() => ({ listOrgMembers: vi.fn() }));

vi.mock('../services/api', () => ({
  ApiError: class ApiError extends Error {
    constructor(
      public code: string,
      message: string,
    ) {
      super(message);
    }
  },
  listOrgMembers: (...a: unknown[]) => listOrgMembers(...a),
}));

// eslint-disable-next-line import/first
import { MembersScreen } from './MembersScreen';

describe('MembersScreen', () => {
  it('renders the member list with roles', async () => {
    listOrgMembers.mockResolvedValue([
      { userId: 'u1', name: 'Joao', email: 'joao@x.com', role: 'OWNER', memberSince: '2026-01-01T00:00:00Z' },
      { userId: 'u2', name: 'Maria', email: 'maria@x.com', role: 'MEMBER', memberSince: '2026-02-01T00:00:00Z' },
    ]);

    render(<MembersScreen onBack={vi.fn()} />);

    expect(await screen.findByText('Joao')).toBeInTheDocument();
    expect(screen.getByText('Proprietario')).toBeInTheDocument();
    expect(screen.getByText('Maria')).toBeInTheDocument();
    expect(screen.getByText('Membro')).toBeInTheDocument();
  });
});
