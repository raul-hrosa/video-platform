import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { PageResponse, RoomResponse } from '../types';

const { listRooms, listRoomProfiles, getRoomDashboard, ApiError } = vi.hoisted(() => ({
  listRooms: vi.fn(),
  listRoomProfiles: vi.fn(),
  getRoomDashboard: vi.fn(),
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
  listRooms: (...a: unknown[]) => listRooms(...a),
  listRoomProfiles: (...a: unknown[]) => listRoomProfiles(...a),
  getRoomDashboard: (...a: unknown[]) => getRoomDashboard(...a),
}));

// eslint-disable-next-line import/first
import { HistoryScreen } from './HistoryScreen';

function page(content: RoomResponse[], totalPages = 1): PageResponse<RoomResponse> {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages };
}

const room: RoomResponse = {
  id: 'r1',
  roomId: 'room-abc',
  name: 'Aula de Ingles',
  status: 'ENDED',
  displayStatus: 'ENDED',
  creationMode: 'PROFILE',
  roomProfileId: 'p1',
  roomProfileName: 'Aula de Ingles',
  roomProfileType: 'LESSON',
  ownerId: 'u1',
  ownerName: 'Joao',
  durationMinutes: 20,
  createdAt: '2026-08-28T13:20:00Z',
  startedAt: '2026-08-28T13:20:00Z',
  endedAt: '2026-08-28T13:39:00Z',
  expiresAt: '2026-08-28T13:40:00Z',
};

const noop = () => {};

beforeEach(() => {
  vi.clearAllMocks();
  listRoomProfiles.mockResolvedValue([]);
  getRoomDashboard.mockResolvedValue({
    roomsToday: 3,
    roomsThisWeek: 12,
    totalRooms: 40,
    totalCallSeconds: 16320,
    distinctParticipants: 21,
  });
});

describe('HistoryScreen', () => {
  it('lists rooms with the profile name and a details button', async () => {
    listRooms.mockResolvedValue(page([room]));
    render(
      <HistoryScreen onBack={noop} onCreate={noop} onOpenDetail={noop} />,
    );

    expect(await screen.findByText('Aula de Ingles')).toBeInTheDocument();
    expect(screen.getByText(/Perfil: Aula de Ingles/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver detalhes' })).toBeInTheDocument();
  });

  it('shows the empty state with no rooms and no filters', async () => {
    listRooms.mockResolvedValue(page([]));
    render(<HistoryScreen onBack={noop} onCreate={noop} onOpenDetail={noop} />);

    expect(
      await screen.findByText('Voce ainda nao realizou nenhuma chamada.'),
    ).toBeInTheDocument();
  });

  it('shows a friendly error and retries', async () => {
    listRooms.mockRejectedValueOnce(new ApiError('SERVER', 'x'));
    render(<HistoryScreen onBack={noop} onCreate={noop} onOpenDetail={noop} />);

    expect(
      await screen.findByText('Nao foi possivel carregar seu historico.'),
    ).toBeInTheDocument();

    listRooms.mockResolvedValue(page([room]));
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));
    expect(await screen.findByText('Aula de Ingles')).toBeInTheDocument();
  });

  it('passes the status filter to the API', async () => {
    listRooms.mockResolvedValue(page([room]));
    render(<HistoryScreen onBack={noop} onCreate={noop} onOpenDetail={noop} />);
    await screen.findByText('Aula de Ingles');

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'ENDED');

    await waitFor(() =>
      expect(listRooms).toHaveBeenLastCalledWith(
        expect.objectContaining({ status: 'ENDED', page: 0 }),
      ),
    );
  });

  it('opens the detail of a room', async () => {
    listRooms.mockResolvedValue(page([room]));
    const onOpenDetail = vi.fn();
    render(<HistoryScreen onBack={noop} onCreate={noop} onOpenDetail={onOpenDetail} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Ver detalhes' }));
    expect(onOpenDetail).toHaveBeenCalledWith('room-abc');
  });
});
