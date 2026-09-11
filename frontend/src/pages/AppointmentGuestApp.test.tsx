import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { JoinConfig, PublicAppointment } from '../types';

const { getPublicAppointment, fetchAppointmentToken, ApiError } = vi.hoisted(() => ({
  getPublicAppointment: vi.fn(),
  fetchAppointmentToken: vi.fn(),
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
  getPublicAppointment: (...a: unknown[]) => getPublicAppointment(...a),
  fetchAppointmentToken: (...a: unknown[]) => fetchAppointmentToken(...a),
}));
vi.mock('../config', () => ({
  config: { mediaProvider: 'livekit', livekitUrl: 'wss://lk', pulsertcUrl: '', apiBaseUrl: '/api' },
  configuredServerUrl: () => 'wss://lk',
}));
vi.mock('./CallRoom', () => ({
  CallRoom: ({ token }: { token: string }) => <div>CALLROOM token={token}</div>,
}));
vi.mock('./DeviceSetup', () => ({
  DeviceSetup: ({ onJoin }: { onJoin: (c: JoinConfig) => void }) => (
    <button
      onClick={() =>
        onJoin({ roomId: 'room-1', cameraEnabled: true, microphoneEnabled: true })
      }
    >
      entrar-setup
    </button>
  ),
}));

import { AppointmentGuestApp } from './AppointmentGuestApp';

function info(over: Partial<PublicAppointment>): PublicAppointment {
  return {
    title: 'Aula de Ingles',
    participantName: 'Raul',
    state: 'JOINABLE',
    scheduledStart: '2020-01-01T00:00:00Z',
    scheduledEnd: '2020-01-01T01:00:00Z',
    joinWindowOpensAt: '2019-12-31T23:45:00Z',
    roomId: 'room-1',
    nextOccurrenceStart: null,
    ...over,
  };
}

beforeEach(() => {
  getPublicAppointment.mockReset();
  fetchAppointmentToken.mockReset();
});

describe('AppointmentGuestApp', () => {
  it('shows the cancelled message', async () => {
    getPublicAppointment.mockResolvedValue(info({ state: 'CANCELLED' }));
    render(<AppointmentGuestApp publicAccessId="abc" />);
    expect(await screen.findByText('Este atendimento foi cancelado.')).toBeInTheDocument();
  });

  it('shows the ended message with the next occurrence', async () => {
    getPublicAppointment.mockResolvedValue(
      info({ state: 'ENDED', nextOccurrenceStart: '2026-09-08T18:00:00Z' }),
    );
    render(<AppointmentGuestApp publicAccessId="abc" />);
    expect(await screen.findByText('Este atendimento ja foi encerrado.')).toBeInTheDocument();
    expect(screen.getByText(/Proximo atendimento/)).toBeInTheDocument();
  });

  it('shows "poderá entrar" before the join window', async () => {
    getPublicAppointment.mockResolvedValue(
      info({ state: 'BEFORE_WINDOW', roomId: null, joinWindowOpensAt: '2099-01-01T14:45:00Z' }),
    );
    render(<AppointmentGuestApp publicAccessId="abc" />);
    expect(await screen.findByText(/Voce podera entrar a partir das/)).toBeInTheDocument();
  });

  it('connects to the call when joinable (occurrence already started)', async () => {
    getPublicAppointment.mockResolvedValue(info({ state: 'JOINABLE' }));
    fetchAppointmentToken.mockResolvedValue({ token: 'guest.jwt', participantId: 'guest:x' });
    render(<AppointmentGuestApp publicAccessId="abc" />);

    await userEvent.click(await screen.findByText('entrar-setup'));

    await waitFor(() => expect(screen.getByText(/CALLROOM token=guest.jwt/)).toBeInTheDocument());
  });
});
