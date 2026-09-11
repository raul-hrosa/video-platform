import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice } from '../components/ErrorNotice';
import { ApiError } from '../services/api';
import type { FriendlyError } from '../types';

export function Login({ onGoToRegister }: { onGoToRegister: () => void }) {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<FriendlyError | null>(null);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await login(email.trim(), password);
    } catch (err) {
      const code = err instanceof ApiError ? err.code : 'UNKNOWN';
      setError({
        code,
        message:
          code === 'INVALID_CREDENTIALS'
            ? 'Email ou senha invalidos.'
            : 'Nao foi possivel entrar. Tente novamente.',
      });
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto flex min-h-full max-w-sm flex-col justify-center gap-5 p-6">
      <h1 className="text-center text-2xl font-semibold text-slate-100">Entrar</h1>

      <form className="flex flex-col gap-4" onSubmit={submit}>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-300">Email</span>
          <input
            type="email"
            autoComplete="email"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={email}
            onChange={(ev) => setEmail(ev.target.value)}
            required
          />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-300">Senha</span>
          <input
            type="password"
            autoComplete="current-password"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={password}
            onChange={(ev) => setPassword(ev.target.value)}
            required
          />
        </label>

        {error && <ErrorNotice error={error} />}

        <button
          type="submit"
          disabled={busy || !email || !password}
          className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {busy ? 'Entrando...' : 'Entrar'}
        </button>
      </form>

      <button
        type="button"
        onClick={onGoToRegister}
        className="text-center text-sm text-slate-400 hover:text-slate-200"
      >
        Criar conta
      </button>
    </div>
  );
}
