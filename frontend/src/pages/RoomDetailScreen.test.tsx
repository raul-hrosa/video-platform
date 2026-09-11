import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { RoomResponse } from '../types';

const {
  getRoom,
  getRoomAnalytics,
  getParticipantsSummary,
  getRoomSessions,
  getRoomQuality,
  getRoomEvents,
  getParticipantAnalytics,
  ApiError,
} = vi.hoisted(() => ({
  getRoom: vi.fn(),
  getRoomAnalytics: vi.fn(),
  getParticipantsSummary: vi.fn(),
  getRoomSessions: vi.fn(),
  getRoomQuality: vi.fn(),
  getRoomEvents: vi.fn(),
  getParticipantAnalytics: vi.fn(),
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
  getRoom: (...a: unknown[]) => getRoom(...a),
  getRoomAnalytics: (...a: unknown[]) => getRoomAnalytics(...a),
  getParticipantsSummary: (...a: unknown[]) => getParticipantsSummary(...a),
  getRoomSessions: (...a: unknown[]) => getRoomSessions(...a),
  getRoomQuality: (...a: unknown[]) => getRoomQuality(...a),
  getRoomEvents: (...a: unknown[]) => getRoomEvents(...a),
  getParticipantAnalytics: (...a: unknown[]) => getParticipantAnalytics(...a),
}));

// eslint-disable-next-line import/first
import { RoomDetailScreen } from './RoomDetailScreen';

const ended: RoomResponse = {
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
  createdAt: '2026-08-28T13:00:00Z',
  startedAt: '2026-08-28T13:20:00Z',
  endedAt: '2026-08-28T13:39:00Z',
  expiresAt: '2026-08-28T13:40:00Z',
};

const noop = () => {};

beforeEach(() => {
  vi.clearAllMocks();
  getRoomAnalytics.mockResolvedValue({
    roomId: 'room-abc',
    durationSeconds: 1140,
    participants: 2,
    peakParticipants: 2,
    totalParticipantSeconds: 2262,
    quality: { avgRttMs: 40, avgPacketLossPercent: 0.2, totalReconnects: 1, levelDistribution: {} },
  });
  getParticipantsSummary.mockResolvedValue({
    distinctParticipants: 1,
    totalSessions: 2,
    totalEntries: 2,
    totalParticipantSeconds: 2262,
    participants: [
      {
        participantId: 'user:joao',
        participantName: 'Joao',
        isGuest: false,
        sessions: 2,
        completedSessions: 2,
        reconnects: 1,
        totalDurationSeconds: 2262,
        firstJoinedAt: '2026-08-28T13:20:00Z',
        lastLeftAt: '2026-08-28T13:39:00Z',
      },
    ],
  });
  getRoomSessions.mockResolvedValue([]);
  getRoomQuality.mockResolvedValue({ hasData: false, timelineAvailable: false, participants: [] });
  getRoomEvents.mockResolvedValue([]);
  getParticipantAnalytics.mockResolvedValue({
    participantRef: 'user:joao',
    kind: 'USER',
    displayName: 'Joao',
    currentSession: null,
    history: { totalSessions: 2, totalConnectedSeconds: 2262, reconnections: 1 },
    quality: null,
    sessions: [],
  });
});

describe('RoomDetailScreen', () => {
  it('renders room info, analytics and participant summary', async () => {
    getRoom.mockResolvedValue(ended);
    render(<RoomDetailScreen roomId="room-abc" onBack={noop} onRejoin={noop} />);

    expect(await screen.findByRole('heading', { name: 'Aula de Ingles' })).toBeInTheDocument();
    expect(screen.getByText('Encerrada')).toBeInTheDocument();
    expect(screen.getAllByText('19m 00s').length).toBeGreaterThan(0); // duração
    expect(screen.getByText(/1 participante\(s\) distinto\(s\)/)).toBeInTheDocument();
    expect(screen.getByText(/1 reconexao/)).toBeInTheDocument();
  });

  it('shows "Nunca iniciada" for an expired room and no invented quality', async () => {
    getRoom.mockResolvedValue({
      ...ended,
      status: 'EXPIRED',
      displayStatus: 'ENDED',
      startedAt: null,
      endedAt: null,
    });
    getRoomAnalytics.mockResolvedValue({
      roomId: 'room-abc',
      durationSeconds: null,
      participants: 0,
      peakParticipants: 0,
      totalParticipantSeconds: 0,
      quality: null,
    });
    getParticipantsSummary.mockResolvedValue({
      distinctParticipants: 0,
      totalSessions: 0,
      totalEntries: 0,
      totalParticipantSeconds: 0,
      participants: [],
    });
    render(<RoomDetailScreen roomId="room-abc" onBack={noop} onRejoin={noop} />);

    expect(await screen.findByText('Nunca iniciada')).toBeInTheDocument();
    expect(screen.getByText('Sem dados de qualidade para esta sala.')).toBeInTheDocument();
  });

  it('lists a guest with no quality measurement instead of hiding them', async () => {
    getRoom.mockResolvedValue(ended);
    getRoomQuality.mockResolvedValue({
      hasData: true,
      timelineAvailable: false,
      participants: [
        {
          participantId: 'user:joao',
          participantName: 'Joao',
          isGuest: false,
          latestLevel: 'GOOD',
          rttMs: 60,
          packetLossPercent: 0.1,
          jitterMs: 8,
          lastRecordedAt: '2026-08-28T13:30:00Z',
          snapshots: 3,
        },
        {
          participantId: 'guest:xyz',
          participantName: 'Maria',
          isGuest: true,
          latestLevel: null,
          rttMs: null,
          packetLossPercent: null,
          jitterMs: null,
          lastRecordedAt: null,
          snapshots: 0,
        },
      ],
    });
    render(<RoomDetailScreen roomId="room-abc" onBack={noop} onRejoin={noop} />);

    expect(await screen.findByText('Maria')).toBeInTheDocument();
    expect(screen.getByText('Convidado — sem medicao de qualidade')).toBeInTheDocument();
  });

  it('offers to rejoin an active room', async () => {
    getRoom.mockResolvedValue({ ...ended, status: 'ACTIVE', displayStatus: 'LIVE', endedAt: null });
    const onRejoin = vi.fn();
    render(<RoomDetailScreen roomId="room-abc" onBack={noop} onRejoin={onRejoin} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Voltar para a chamada' }));
    expect(onRejoin).toHaveBeenCalledWith('room-abc');
  });

  it('shows the access link and the events timeline', async () => {
    getRoom.mockResolvedValue(ended);
    getRoomEvents.mockResolvedValue([
      { type: 'ROOM_CREATED', at: '2026-08-28T13:00:00Z', participantRef: null, detail: null },
      {
        type: 'PARTICIPANT_SESSION_STARTED',
        at: '2026-08-28T13:20:00Z',
        participantRef: 'guest:xyz',
        detail: null,
      },
    ]);
    render(<RoomDetailScreen roomId="room-abc" onBack={noop} onRejoin={noop} />);

    expect(await screen.findByText('Access link')).toBeInTheDocument();
    expect(screen.getByText(/\/room\/room-abc$/)).toBeInTheDocument();
    expect(screen.getByText('ROOM_CREATED')).toBeInTheDocument();
    expect(screen.getByText('PARTICIPANT_SESSION_STARTED')).toBeInTheDocument();
  });

  it('opens participant analytics on click', async () => {
    getRoom.mockResolvedValue(ended);
    render(<RoomDetailScreen roomId="room-abc" onBack={noop} onRejoin={noop} />);

    await userEvent.click(await screen.findByText('Joao'));
    expect(await screen.findByText('user:joao')).toBeInTheDocument();
    expect(getParticipantAnalytics).toHaveBeenCalledWith('room-abc', 'user:joao');
  });

  it('shows a permission error for another owner room', async () => {
    getRoom.mockRejectedValue(new ApiError('FORBIDDEN', 'x'));
    render(<RoomDetailScreen roomId="room-abc" onBack={noop} onRejoin={noop} />);

    expect(
      await screen.findByText('Voce nao tem permissao para visualizar esta sala.'),
    ).toBeInTheDocument();
  });
});
