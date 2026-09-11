import { useState } from 'react';
import { Login } from './Login';
import { Register } from './Register';

/** Alterna entre login e cadastro enquanto o usuario nao esta autenticado. */
export function AuthScreen() {
  const [mode, setMode] = useState<'login' | 'register'>('login');

  return mode === 'login' ? (
    <Login onGoToRegister={() => setMode('register')} />
  ) : (
    <Register onDone={() => setMode('login')} />
  );
}
