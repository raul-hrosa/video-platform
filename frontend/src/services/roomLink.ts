const ROOM_PATH = /^\/room\/([^/?#\s]+)\/?$/;

/** Link compartilhavel de uma sala: `${origin}/room/${roomId}` (Sprint 5 §23). */
export function roomLink(roomId: string): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : '';
  return `${origin}/room/${roomId}`;
}

/** roomId se a rota atual for `/room/{roomId}`, senao `null`. */
export function matchRoomPath(pathname?: string): string | null {
  const path = pathname ?? (typeof window !== 'undefined' ? window.location.pathname : '');
  const match = path.match(ROOM_PATH);
  return match ? decodeURIComponent(match[1]) : null;
}

/**
 * Extrai o `roomId` do que o usuario colou: aceita uma URL completa
 * (`https://.../room/room-abc`) ou o codigo cru (`room-abc`).
 */
export function parseRoomId(input: string): string {
  const trimmed = input.trim();
  const match = trimmed.match(/\/room\/([^/?#\s]+)/);
  return match ? decodeURIComponent(match[1]) : trimmed;
}

/** Copia o link da sala para a area de transferencia. Lanca se o browser recusar. */
export async function copyRoomLink(roomId: string): Promise<void> {
  await navigator.clipboard.writeText(roomLink(roomId));
}
