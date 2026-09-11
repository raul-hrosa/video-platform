import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const meMock = vi.fn();
const loginMock = vi.fn();
let unauthorizedCb: (() => void) | null = null;

vi.mock('../services/api', () => ({
  ApiError: class ApiError extends Error {
    constructor(
      public code: string,
      message: string,
    ) {
      super(message);
    }
  },
  me: (...a: unknown[]) => meMock(...a),
  login: (...a: unknown[]) => loginMock(...a),
  register: vi.fn(),
  onUnauthorized: (cb: () => void) => {
    unauthorizedCb = cb;
  },
}));

const store: Record<string, string> = {};
vi.mock('../services/authStorage', () => ({
  getToken: () => store.t ?? null,
  setToken: (t: string) => {
    store.t = t;
  },
  clearToken: () => {
    delete store.t;
  },
}));

vi.mock('../services/logger', () => ({
  LogEvent: new Proxy({}, { get: (_t, k) => String(k) }),
  logger: { info: vi.fn(), warn: vi.fn(), error: vi.fn(), debug: vi.fn() },
}));

// eslint-disable-next-line import/first
import { AuthProvider, useAuth } from './AuthContext';

function Probe() {
  const { status, user, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="user">{user?.email ?? '-'}</span>
      <button onClick={() => login('a@b.com', 'pw')}>login</button>
      <button onClick={logout}>logout</button>
    </div>
  );
}

function renderProbe() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  );
}

describe('AuthContext', () => {
  beforeEach(() => {
    meMock.mockReset();
    loginMock.mockReset();
    unauthorizedCb = null;
    delete store.t;
  });
  afterEach(() => vi.clearAllMocks());

  it('is anonymous when there is no token', async () => {
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
    expect(meMock).not.toHaveBeenCalled();
  });

  it('hydrates the user from /me when a token exists', async () => {
    store.t = 'tok';
    meMock.mockResolvedValue({ id: '1', name: 'Joao', email: 'joao@x.com' });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(screen.getByTestId('user')).toHaveTextContent('joao@x.com');
  });

  it('login stores the token and loads the user', async () => {
    loginMock.mockResolvedValue({ accessToken: 'new-tok' });
    meMock.mockResolvedValue({ id: '1', name: 'Joao', email: 'joao@x.com' });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));

    await userEvent.click(screen.getByText('login'));
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));
    expect(store.t).toBe('new-tok');
  });

  it('logout clears the session', async () => {
    store.t = 'tok';
    meMock.mockResolvedValue({ id: '1', name: 'Joao', email: 'joao@x.com' });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    await userEvent.click(screen.getByText('logout'));
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(store.t).toBeUndefined();
  });

  it('a 401 from the api logs the user out', async () => {
    store.t = 'tok';
    meMock.mockResolvedValue({ id: '1', name: 'Joao', email: 'joao@x.com' });
    renderProbe();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('authenticated'));

    unauthorizedCb?.();
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'));
  });
});
