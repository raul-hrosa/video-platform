import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../services/api';

const { register } = vi.hoisted(() => ({ register: vi.fn() }));
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ register, login: vi.fn(), logout: vi.fn(), status: 'anonymous', user: null }),
}));

// eslint-disable-next-line import/first
import { Register } from './Register';

async function fill(name: string, email: string, pass: string, confirm: string) {
  await userEvent.type(screen.getByLabelText('Nome'), name);
  await userEvent.type(screen.getByLabelText('Email'), email);
  await userEvent.type(screen.getByLabelText('Senha'), pass);
  await userEvent.type(screen.getByLabelText('Confirmar senha'), confirm);
  await userEvent.click(screen.getByRole('button', { name: 'Criar conta' }));
}

describe('Register', () => {
  it('rejects short passwords before calling the API', async () => {
    register.mockReset();
    render(<Register onDone={vi.fn()} />);
    await fill('Joao', 'joao@example.com', 'short', 'short');

    expect(await screen.findByText(/ao menos 8 caracteres/)).toBeInTheDocument();
    expect(register).not.toHaveBeenCalled();
  });

  it('rejects mismatched passwords', async () => {
    register.mockReset();
    render(<Register onDone={vi.fn()} />);
    await fill('Joao', 'joao@example.com', 'supersecret', 'different1');

    expect(await screen.findByText('As senhas nao conferem.')).toBeInTheDocument();
    expect(register).not.toHaveBeenCalled();
  });

  it('shows a message when the email already exists', async () => {
    register.mockReset().mockImplementation(() =>
      Promise.reject(new ApiError('EMAIL_ALREADY_EXISTS', 'x')),
    );
    render(<Register onDone={vi.fn()} />);
    await fill('Joao', 'joao@example.com', 'supersecret', 'supersecret');

    expect(await screen.findByText('Este email ja esta cadastrado.')).toBeInTheDocument();
  });

  it('shows a success screen after registering', async () => {
    register.mockReset().mockResolvedValue({ id: '1', name: 'Joao', email: 'joao@example.com' });
    render(<Register onDone={vi.fn()} />);
    await fill('Joao', 'joao@example.com', 'supersecret', 'supersecret');

    expect(await screen.findByText('Conta criada!')).toBeInTheDocument();
  });
});
