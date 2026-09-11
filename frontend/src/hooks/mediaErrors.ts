import type { FriendlyError } from '../types';

/**
 * Converte erros tecnicos de getUserMedia em mensagens amigaveis (secao 8/19).
 */
export function toFriendlyMediaError(err: unknown): FriendlyError {
  const name = err instanceof DOMException ? err.name : '';

  switch (name) {
    case 'NotAllowedError':
    case 'SecurityError':
      return {
        code: 'PERMISSION_DENIED',
        message:
          'Precisamos de acesso a camera e ao microfone. Libere as permissoes no navegador e tente de novo.',
      };
    case 'NotFoundError':
    case 'OverconstrainedError':
      return {
        code: 'DEVICE_NOT_FOUND',
        message: 'Nao encontramos uma camera ou microfone disponivel neste dispositivo.',
      };
    case 'NotReadableError':
    case 'AbortError':
      return {
        code: 'DEVICE_IN_USE',
        message:
          'Sua camera ou microfone parece estar em uso por outro aplicativo. Feche-o e tente novamente.',
      };
    default:
      return {
        code: 'MEDIA_ERROR',
        message: 'Nao foi possivel acessar seus dispositivos de audio e video.',
      };
  }
}
