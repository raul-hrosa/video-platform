import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const { getCurrentOrganization } = vi.hoisted(() => ({ getCurrentOrganization: vi.fn() }));

vi.mock('../services/api', () => ({
  getCurrentOrganization: (...a: unknown[]) => getCurrentOrganization(...a),
}));

// eslint-disable-next-line import/first
import { OrgProvider, useOrg } from './OrgContext';

function Probe() {
  const { organization, loading } = useOrg();
  return <div>{loading ? 'loading' : (organization?.name ?? 'none')}</div>;
}

describe('OrgContext', () => {
  it('loads the current organization on mount', async () => {
    getCurrentOrganization.mockResolvedValue({
      id: 'o1',
      name: 'Clinica ABC',
      slug: 'clinica-abc',
      role: 'OWNER',
    });

    render(
      <OrgProvider>
        <Probe />
      </OrgProvider>,
    );

    expect(await screen.findByText('Clinica ABC')).toBeInTheDocument();
  });

  it('degrades to no organization when the request fails', async () => {
    getCurrentOrganization.mockRejectedValue(new Error('boom'));

    render(
      <OrgProvider>
        <Probe />
      </OrgProvider>,
    );

    await waitFor(() => expect(screen.getByText('none')).toBeInTheDocument());
  });
});
