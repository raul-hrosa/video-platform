import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const { listRooms } = vi.hoisted(() => ({ listRooms: vi.fn() }));

vi.mock('../services/api', () => ({
  ApiError: class ApiError extends Error {
    constructor(
      public code: string,
      message: string,
    ) {
      super(message);
    }
  },
  listRooms: (...a: unknown[]) => listRooms(...a),
}));
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ user: { name: 'Joao' }, logout: vi.fn() }) }));
vi.mock('../components/OrgBadge', () => ({ OrgBadge: () => <div>ORG</div> }));

// eslint-disable-next-line import/first
import { RoomsConsole } from './RoomsConsole';

const handlers = {
  onOpenRoom: vi.fn(),
  onJoinRoom: vi.fn(),
  onCreateRoom: vi.fn(),
  onOpenProfiles: vi.fn(),
  onOpenHistory: vi.fn(),
  onOpenAppointments: vi.fn(),
  onOpenOrgSettings: vi.fn(),
  onOpenMembers: vi.fn(),
};

beforeEach(() => {
  vi.clearAllMocks();
  listRooms.mockResolvedValue({
    content: [
      {
        id: 'r1',
        roomId: 'room-abc',
        name: null,
        status: 'ACTIVE',
        displayStatus: 'LIVE',
        creationMode: 'DIRECT',
        roomProfileId: null,
        roomProfileName: null,
        roomProfileType: null,
        ownerId: 'u1',
        ownerName: 'Joao',
        durationMinutes: null,
        createdAt: '2026-08-31T14:00:00Z',
        startedAt: '2026-08-31T14:01:00Z',
        endedAt: null,
        expiresAt: null,
      },
    ],
    page: 0,
    size: 25,
    totalElements: 1,
    totalPages: 1,
  });
});

describe('RoomsConsole', () => {
  it('creates a room with one click', async () => {
    render(<RoomsConsole {...handlers} />);
    await userEvent.click(screen.getByRole('button', { name: 'Create room' }));
    expect(handlers.onCreateRoom).toHaveBeenCalled();
  });

  it('lists rooms with the LIVE/IDLE/ENDED badge and opens one', async () => {
    render(<RoomsConsole {...handlers} />);
    const row = await screen.findByText('room-abc');
    expect(screen.getByText('LIVE')).toBeInTheDocument();
    await userEvent.click(row);
    expect(handlers.onOpenRoom).toHaveBeenCalledWith('room-abc');
  });
});
