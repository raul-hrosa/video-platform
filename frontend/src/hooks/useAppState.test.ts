import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../services/api';
import { useAppState } from './useAppState';

const { createRoomFromProfile, createDirectRoom, fetchRoomToken } = vi.hoisted(() => ({
  createRoomFromProfile: vi.fn(),
  createDirectRoom: vi.fn(),
  fetchRoomToken: vi.fn(),
}));

vi.mock('../services/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/api')>()),
  createRoomFromProfile: (...args: unknown[]) => createRoomFromProfile(...args),
  createDirectRoom: (...args: unknown[]) => createDirectRoom(...args),
  fetchRoomToken: (...args: unknown[]) => fetchRoomToken(...args),
}));

beforeEach(() => {
  createRoomFromProfile.mockReset();
  createDirectRoom.mockReset();
  fetchRoomToken.mockReset();
});

describe('useAppState', () => {
  it('creates a room from a profile and moves to DEVICE_SETUP', async () => {
    createRoomFromProfile.mockResolvedValue({
      roomId: 'room-abc',
      name: 'Aula de Ingles',
      expiresAt: '2026-08-28T18:20:00Z',
    });
    const { result } = renderHook(() => useAppState('wss://lk'));

    await act(async () => {
      await result.current.createRoom('profile-1');
    });

    expect(result.current.state).toBe('DEVICE_SETUP');
    expect(result.current.roomId).toBe('room-abc');
    expect(result.current.createdRoom).toEqual({
      roomId: 'room-abc',
      name: 'Aula de Ingles',
      expiresAt: '2026-08-28T18:20:00Z',
    });
  });

  it('creates a direct room (no profile, no name) and moves to DEVICE_SETUP', async () => {
    createDirectRoom.mockResolvedValue({ roomId: 'room-xyz', name: null, expiresAt: null });
    const { result } = renderHook(() => useAppState('wss://lk'));

    await act(async () => {
      await result.current.createDirectRoom();
    });

    expect(result.current.state).toBe('DEVICE_SETUP');
    expect(result.current.roomId).toBe('room-xyz');
    expect(result.current.createdRoom).toEqual({ roomId: 'room-xyz', name: null, expiresAt: null });
  });

  it('returns to INITIAL with a friendly error when creation fails', async () => {
    createRoomFromProfile.mockImplementation(() =>
      Promise.reject(new ApiError('ROOM_PROFILE_NOT_FOUND', 'x')),
    );
    const { result } = renderHook(() => useAppState('wss://lk'));

    await act(async () => {
      await result.current.createRoom('profile-1');
    });

    expect(result.current.state).toBe('INITIAL');
    expect(result.current.error?.message).toBe('Perfil nao encontrado.');
  });

  it('goes to ROOM_EXPIRED when the token request reports ROOM_EXPIRED', async () => {
    fetchRoomToken.mockImplementation(() => Promise.reject(new ApiError('ROOM_EXPIRED', 'x')));
    const { result } = renderHook(() => useAppState('wss://lk'));

    act(() => result.current.selectRoom('room-old'));
    await act(async () => {
      await result.current.join({
        roomId: 'room-old',
        cameraEnabled: true,
        microphoneEnabled: true,
      });
    });

    expect(result.current.state).toBe('ROOM_EXPIRED');
  });

  it('connects when the token request succeeds', async () => {
    fetchRoomToken.mockResolvedValue({ token: 'jwt', participantId: 'user:1' });
    const { result } = renderHook(() => useAppState('wss://lk'));

    act(() => result.current.selectRoom('room-ok'));
    await act(async () => {
      await result.current.join({
        roomId: 'room-ok',
        cameraEnabled: true,
        microphoneEnabled: true,
      });
    });

    await waitFor(() => expect(result.current.state).toBe('CONNECTED'));
    expect(result.current.session?.token).toBe('jwt');
  });
});
