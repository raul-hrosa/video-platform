/**
 * Rótulo amigável a partir da identidade PulseRTC {@code <sub>.<8hex>} (Sprint 11 §9).
 * O PulseRTC não propaga o nome dos outros participantes por nenhum canal — só o
 * próprio nome vem no `welcome`. Aqui derivamos um rótulo estável de
 * convidado/usuário + sufixo curto para diferenciar.
 */
export function labelForIdentity(identity: string, index: number): string {
  const sub = identity.split('.')[0] ?? identity;
  const suffix = identity.includes('.') ? ` ${identity.split('.').pop()?.slice(0, 4)}` : '';
  if (sub.startsWith('guest:')) return `Convidado${suffix || ` ${index + 1}`}`;
  if (sub.startsWith('user:')) return `Participante${suffix || ` ${index + 1}`}`;
  return `Participante ${index + 1}`;
}
