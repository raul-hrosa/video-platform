import { useState } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice } from '../components/ErrorNotice';
import { ApiError } from '../services/api';
import type { FriendlyError } from '../types';

const MIN_PASSWORD = 8;

export function Register({ onDone }: { onDone: () => void }) {
  const { register } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [done, setDone] = useState(false);

  function localValidation(): string | null {
    if (password.length < MIN_PASSWORD) return `A senha precisa ter ao menos ${MIN_PASSWORD} caracteres.`;
    if (password !== confirm) return 'As senhas nao conferem.';
    return null;
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    const local = localValidation();
    if (local) {
      setError({ code: 'INVALID_INPUT', message: local });
      return;
    }
    setBusy(true);
    try {
      await register(name.trim(), email.trim(), password);
      setDone(true);
    } catch (err) {
      const code = err instanceof ApiError ? err.code : 'UNKNOWN';
      setError({
        code,
        message:
          code === 'EMAIL_ALREADY_EXISTS'
            ? 'Este email ja esta cadastrado.'
            : 'Nao foi possivel criar a conta. Verifique os dados.',
      });
    } finally {
      setBusy(false);
    }
  }

  if (done) {
    return (
      <div className="mx-auto flex min-h-full max-w-sm flex-col justify-center gap-4 p-6 text-center">
        <h1 className="text-xl font-semibold text-slate-100">Conta criada!</h1>
        <p className="text-sm text-slate-400">Agora e' so entrar com seu email e senha.</p>
        <button
          type="button"
          onClick={onDone}
          className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white hover:bg-indigo-500"
        >
          Ir para o login
        </button>
      </div>
    );
  }

  return (
    <div className="mx-auto flex min-h-full max-w-sm flex-col justify-center gap-5 p-6">
      <h1 className="text-center text-2xl font-semibold text-slate-100">Criar conta</h1>

      <form className="flex flex-col gap-4" onSubmit={submit}>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-300">Nome</span>
          <input
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={name}
            onChange={(ev) => setName(ev.target.value)}
            required
            maxLength={255}
          />
        </label>
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
            autoComplete="new-password"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={password}
            onChange={(ev) => setPassword(ev.target.value)}
            required
          />
        </label>
        <label className="block text-sm">
          <span className="mb-1 block font-medium text-slate-300">Confirmar senha</span>
          <input
            type="password"
            autoComplete="new-password"
            className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100"
            value={confirm}
            onChange={(ev) => setConfirm(ev.target.value)}
            required
          />
        </label>

        {error && <ErrorNotice error={error} />}

        <button
          type="submit"
          disabled={busy || !name || !email || !password || !confirm}
          className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {busy ? 'Criando...' : 'Criar conta'}
        </button>
      </form>

      <button
        type="button"
        onClick={onDone}
        className="text-center text-sm text-slate-400 hover:text-slate-200"
      >
        Ja tenho conta
      </button>
    </div>
  );
}
