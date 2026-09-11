import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { RoomProfile } from '../types';

const { listRoomProfiles, ApiError } = vi.hoisted(() => ({
  listRoomProfiles: vi.fn(),
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
  listRoomProfiles: (...a: unknown[]) => listRoomProfiles(...a),
}));
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { name: 'Joao' }, logout: vi.fn() }),
}));
vi.mock('./RoomProfileForm', () => ({
  RoomProfileForm: ({ existing }: { existing?: RoomProfile | null }) => (
    <div>FORM {existing ? existing.name : 'new'}</div>
  ),
}));

// eslint-disable-next-line import/first
import { RoomEntry } from './RoomEntry';

const profile: RoomProfile = {
  id: 'p1',
  name: 'Aula de Ingles',
  durationMinutes: 20,
  type: 'LESSON',
  createdAt: '2026-08-28T10:00:00Z',
  updatedAt: '2026-08-28T10:00:00Z',
};

beforeEach(() => listRoomProfiles.mockReset());

describe('RoomEntry', () => {
  it('lists the user profiles', async () => {
    listRoomProfiles.mockResolvedValue([profile]);
    render(<RoomEntry onRoomSelected={vi.fn()} onCreateFromProfile={vi.fn()} />);

    expect(await screen.findByText('Aula de Ingles')).toBeInTheDocument();
    expect(screen.getByText('20 minutos')).toBeInTheDocument();
  });

  it('shows an error with retry, then recovers when retried', async () => {
    listRoomProfiles
      .mockImplementationOnce(() => Promise.reject(new ApiError('NETWORK_ERROR', 'boom')))
      .mockResolvedValueOnce([profile]);

    render(<RoomEntry onRoomSelected={vi.fn()} onCreateFromProfile={vi.fn()} />);

    const retry = await screen.findByRole('button', { name: 'Tentar novamente' });
    await userEvent.click(retry);

    expect(await screen.findByText('Aula de Ingles')).toBeInTheDocument();
  });

  it('fires onCreateFromProfile when "Nova sala" is clicked', async () => {
    listRoomProfiles.mockResolvedValue([profile]);
    const onCreateFromProfile = vi.fn();
    render(<RoomEntry onRoomSelected={vi.fn()} onCreateFromProfile={onCreateFromProfile} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Nova sala' }));

    expect(onCreateFromProfile).toHaveBeenCalledWith('p1');
  });

  it('opens the form to edit a profile', async () => {
    listRoomProfiles.mockResolvedValue([profile]);
    render(<RoomEntry onRoomSelected={vi.fn()} onCreateFromProfile={vi.fn()} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }));

    expect(screen.getByText('FORM Aula de Ingles')).toBeInTheDocument();
  });

  it('parses a pasted link when entering an existing room', async () => {
    listRoomProfiles.mockResolvedValue([]);
    const onRoomSelected = vi.fn();
    render(<RoomEntry onRoomSelected={onRoomSelected} onCreateFromProfile={vi.fn()} />);

    await waitFor(() => expect(screen.queryByText('Carregando...')).not.toBeInTheDocument());
    await userEvent.type(
      screen.getByPlaceholderText(/Cole o link/),
      'https://app.example.com/room/room-xyz',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    expect(onRoomSelected).toHaveBeenCalledWith('room-xyz');
  });
});
