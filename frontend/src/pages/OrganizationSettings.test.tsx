import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Organization } from '../types';

const { updateOrganizationName } = vi.hoisted(() => ({
  updateOrganizationName: vi.fn(),
}));

vi.mock('../services/api', () => ({
  ApiError: class ApiError extends Error {
    constructor(
      public code: string,
      message: string,
    ) {
      super(message);
    }
  },
  updateOrganizationName: (...a: unknown[]) => updateOrganizationName(...a),
}));

let orgValue: {
  organization: Organization | null;
  loading: boolean;
  refresh: () => Promise<void>;
};

vi.mock('../auth/OrgContext', () => ({
  useOrg: () => orgValue,
}));

// eslint-disable-next-line import/first
import { OrganizationSettings } from './OrganizationSettings';

describe('OrganizationSettings', () => {
  it('lets an OWNER rename the organization', async () => {
    orgValue = {
      organization: { id: 'o1', name: 'Clinica ABC', slug: 'clinica-abc', role: 'OWNER' },
      loading: false,
      refresh: vi.fn().mockResolvedValue(undefined),
    };
    updateOrganizationName.mockResolvedValue({});

    render(<OrganizationSettings onBack={vi.fn()} />);

    const input = screen.getByLabelText('Nome') as HTMLInputElement;
    await userEvent.clear(input);
    await userEvent.type(input, 'Clinica ABC Saude');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    await waitFor(() =>
      expect(updateOrganizationName).toHaveBeenCalledWith('Clinica ABC Saude'),
    );
  });

  it('disables editing for a non-owner', () => {
    orgValue = {
      organization: { id: 'o1', name: 'Clinica ABC', slug: 'clinica-abc', role: 'MEMBER' },
      loading: false,
      refresh: vi.fn(),
    };

    render(<OrganizationSettings onBack={vi.fn()} />);

    expect(screen.getByLabelText('Nome')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Salvar' })).not.toBeInTheDocument();
    expect(screen.getByText(/Apenas o OWNER/)).toBeInTheDocument();
  });

  it('shows the slug as read-only', () => {
    orgValue = {
      organization: { id: 'o1', name: 'X', slug: 'x-123', role: 'OWNER' },
      loading: false,
      refresh: vi.fn(),
    };

    render(<OrganizationSettings onBack={vi.fn()} />);

    expect(screen.getByLabelText('Slug')).toHaveAttribute('readonly');
  });
});
