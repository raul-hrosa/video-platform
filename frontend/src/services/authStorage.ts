/**
 * Armazenamento do Platform JWT no frontend.
 *
 * Estrategia: cache em memoria + `sessionStorage`. Trade-off (documentado no
 * README): sobrevive a refresh, some ao fechar a aba; mais simples que httpOnly
 * cookie + CSRF; menos persistente que localStorage. O token nunca e' logado.
 */
const KEY = 'vp.authToken';

let inMemory: string | null = null;

export function getToken(): string | null {
  if (inMemory) return inMemory;
  try {
    inMemory = sessionStorage.getItem(KEY);
  } catch {
    inMemory = null;
  }
  return inMemory;
}

export function setToken(token: string): void {
  inMemory = token;
  try {
    sessionStorage.setItem(KEY, token);
  } catch {
    // sessionStorage indisponivel (modo privado etc.) — segue so em memoria
  }
}

export function clearToken(): void {
  inMemory = null;
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // ignore
  }
}
