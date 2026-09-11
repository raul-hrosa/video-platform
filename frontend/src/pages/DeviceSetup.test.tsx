import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

vi.mock('../hooks/useMediaDevices', () => ({
  useMediaDevices: () => ({
    devices: { cameras: [], microphones: [] },
    permissionGranted: true,
    error: null,
    requestAccess: vi.fn().mockResolvedValue(true),
    refresh: vi.fn(),
  }),
}));
vi.mock('../hooks/useCameraPreview', () => ({
  useCameraPreview: () => ({ videoRef: { current: null }, error: null }),
}));

// eslint-disable-next-line import/first
import { DeviceSetup } from './DeviceSetup';

const base = {
  roomId: 'room-abc',
  userName: 'Joao',
  joining: false,
  joinError: null,
  onJoin: vi.fn(),
  onBack: vi.fn(),
};

describe('DeviceSetup', () => {
  it('shows the read-only name for an authenticated user', () => {
    render(<DeviceSetup {...base} />);
    expect(screen.queryByLabelText('Seu nome')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Entrar na chamada' })).toBeEnabled();
  });

  it('shows an editable "Seu nome" field for a guest and gates the join button', async () => {
    const onGuestNameChange = vi.fn();
    const { rerender } = render(
      <DeviceSetup {...base} guestName="" onGuestNameChange={onGuestNameChange} />,
    );

    const nameInput = screen.getByLabelText('Seu nome');
    expect(screen.getByRole('button', { name: 'Entrar na chamada' })).toBeDisabled();

    await userEvent.type(nameInput, 'M');
    expect(onGuestNameChange).toHaveBeenCalledWith('M');

    rerender(<DeviceSetup {...base} guestName="Maria" onGuestNameChange={onGuestNameChange} />);
    expect(screen.getByRole('button', { name: 'Entrar na chamada' })).toBeEnabled();
  });
});
