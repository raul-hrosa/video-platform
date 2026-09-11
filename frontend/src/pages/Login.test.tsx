import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../services/api';

const { login } = vi.hoisted(() => ({ login: vi.fn() }));
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ login, register: vi.fn(), logout: vi.fn(), status: 'anonymous', user: null }),
}));

// eslint-disable-next-line import/first
import { Login } from './Login';

async function attempt(email: string, password: string) {
  await userEvent.type(screen.getByLabelText('Email'), email);
  await userEvent.type(screen.getByLabelText('Senha'), password);
  await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
}

describe('Login', () => {
  it('submits email and password', async () => {
    login.mockReset().mockResolvedValue(undefined);
    render(<Login onGoToRegister={vi.fn()} />);
    await attempt('joao@example.com', 'supersecret');
    expect(login).toHaveBeenCalledWith('joao@example.com', 'supersecret');
  });

  it('shows a friendly message on invalid credentials', async () => {
    login.mockReset().mockImplementation(() => Promise.reject(new ApiError('INVALID_CREDENTIALS', 'x')));
    render(<Login onGoToRegister={vi.fn()} />);
    await attempt('joao@example.com', 'wrong');
    expect(await screen.findByText('Email ou senha invalidos.')).toBeInTheDocument();
  });

  it('disables the button while submitting', async () => {
    let resolve: () => void = () => {};
    login.mockReset().mockReturnValue(new Promise<void>((r) => (resolve = r)));
    render(<Login onGoToRegister={vi.fn()} />);
    await attempt('joao@example.com', 'supersecret');
    expect(screen.getByRole('button', { name: 'Entrando...' })).toBeDisabled();
    resolve();
  });
});
