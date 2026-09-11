import { render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

const authState = { status: 'anonymous' as string, user: null as { name: string } | null };

vi.mock('./auth/AuthContext', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuth: () => authState,
}));
vi.mock('./pages/AuthScreen', () => ({ AuthScreen: () => <div>AUTH SCREEN</div> }));
vi.mock('./pages/RoomEntry', () => ({ RoomEntry: () => <div>ROOM ENTRY</div> }));
vi.mock('./pages/RoomsConsole', () => ({ RoomsConsole: () => <div>ROOMS CONSOLE</div> }));
vi.mock('./pages/GuestApp', () => ({ GuestApp: ({ roomId }: { roomId: string }) => <div>GUEST {roomId}</div> }));
vi.mock('./pages/CallRoom', () => ({ CallRoom: () => <div /> }));
vi.mock('./pages/DeviceSetup', () => ({ DeviceSetup: () => <div /> }));
vi.mock('./config', () => ({ config: { livekitUrl: 'wss://x', apiBaseUrl: '/api' } }));

// eslint-disable-next-line import/first
import App from './App';

afterEach(() => window.history.replaceState(null, '', '/'));

describe('App auth gate', () => {
  it('shows the auth screen when anonymous', () => {
    authState.status = 'anonymous';
    authState.user = null;
    render(<App />);
    expect(screen.getByText('AUTH SCREEN')).toBeInTheDocument();
  });

  it('shows the guest flow when anonymous with a /room/:id link', () => {
    authState.status = 'anonymous';
    authState.user = null;
    window.history.replaceState(null, '', '/room/room-abc123');
    render(<App />);
    expect(screen.getByText('GUEST room-abc123')).toBeInTheDocument();
  });

  it('shows the app when authenticated', () => {
    authState.status = 'authenticated';
    authState.user = { name: 'Joao' };
    render(<App />);
    expect(screen.getByText('ROOMS CONSOLE')).toBeInTheDocument();
  });

  it('shows a loading state while resolving auth', () => {
    authState.status = 'loading';
    authState.user = null;
    render(<App />);
    expect(screen.getByText('Carregando...')).toBeInTheDocument();
  });
});
