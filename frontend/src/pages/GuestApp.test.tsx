import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { JoinConfig } from '../types';

const { fetchGuestToken, ApiError } = vi.hoisted(() => ({
  fetchGuestToken: vi.fn(),
  ApiError: class ApiError extends Error {
    constructor(
      public code: string,
      message: string,
    ) {
      super(message);
    }
  },
}));

vi.mock('../services/api', () => ({
  ApiError,
  fetchGuestToken: (...a: unknown[]) => fetchGuestToken(...a),
}));
vi.mock('../config', () => ({
  config: { mediaProvider: 'livekit', livekitUrl: 'wss://lk', apiBaseUrl: '/api' },
  configuredServerUrl: () => 'wss://lk',
}));
vi.mock('./CallRoom', () => ({
  CallRoom: ({ token, resolveSession }: { token: string; resolveSession?: boolean }) => (
    <div>CALLROOM token={token} resolveSession={String(resolveSession)}</div>
  ),
}));
vi.mock('./DeviceSetup', () => ({
  DeviceSetup: ({
    guestName,
    onGuestNameChange,
    onJoin,
  }: {
    guestName?: string;
    onGuestNameChange?: (v: string) => void;
    onJoin: (c: JoinConfig) => void;
  }) => (
    <div>
      <input
        aria-label="guest-name"
        value={guestName ?? ''}
        onChange={(e) => onGuestNameChange?.(e.target.value)}
      />
      <button
        onClick={() =>
          onJoin({ roomId: 'room-abc', cameraEnabled: true, microphoneEnabled: true })
        }
      >
        Entrar
      </button>
    </div>
  ),
}));

// eslint-disable-next-line import/first
import { GuestApp } from './GuestApp';

beforeEach(() => fetchGuestToken.mockReset());

describe('GuestApp', () => {
  it('joins with the typed name and disables session resolution', async () => {
    fetchGuestToken.mockResolvedValue({ token: 'guest.jwt', participantId: 'guest:1' });
    render(<GuestApp roomId="room-abc" />);

    await userEvent.type(screen.getByLabelText('guest-name'), 'Maria');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(fetchGuestToken).toHaveBeenCalledWith('room-abc', 'Maria');
    expect(await screen.findByText(/CALLROOM token=guest.jwt/)).toBeInTheDocument();
    expect(screen.getByText(/resolveSession=false/)).toBeInTheDocument();
  });

  it('shows the expired notice when the room has expired', async () => {
    fetchGuestToken.mockImplementationOnce(() => Promise.reject(new ApiError('ROOM_EXPIRED', 'x')));
    render(<GuestApp roomId="room-old" />);

    await userEvent.type(screen.getByLabelText('guest-name'), 'Maria');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(await screen.findByText('Sala expirada')).toBeInTheDocument();
  });
});
