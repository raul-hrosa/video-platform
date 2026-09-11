const APPOINTMENT_PATH = /^\/r\/([^/?#\s]+)\/?$/;

/** Link publico permanente de um atendimento: `${origin}/r/${publicAccessId}` (§3, §39). */
export function appointmentLink(publicAccessId: string): string {
  const origin = typeof window !== 'undefined' ? window.location.origin : '';
  return `${origin}/r/${publicAccessId}`;
}

/** publicAccessId se a rota atual for `/r/{id}`, senao `null`. */
export function matchAppointmentPath(pathname?: string): string | null {
  const path = pathname ?? (typeof window !== 'undefined' ? window.location.pathname : '');
  const match = path.match(APPOINTMENT_PATH);
  return match ? decodeURIComponent(match[1]) : null;
}

/** Copia o link do atendimento para a area de transferencia. Lanca se o browser recusar. */
export async function copyAppointmentLink(publicAccessId: string): Promise<void> {
  await navigator.clipboard.writeText(appointmentLink(publicAccessId));
}
