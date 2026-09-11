import { useEffect, useState } from 'react';
import { findMySession } from '../services/api';

interface Params {
  roomId: string;
  connected: boolean;
}

/**
 * Descobre o sessionId da propria ParticipantSession apos conectar. A sessao e'
 * criada pelo webhook `participant_joined` do LiveKit, que pode chegar depois da
 * conexao — por isso tenta algumas vezes. A identidade vem do usuario
 * autenticado (o backend resolve pelo JWT).
 */
export function useSessionResolver({ roomId, connected }: Params): string | null {
  const [sessionId, setSessionId] = useState<string | null>(null);

  useEffect(() => {
    if (!connected || sessionId) return;

    let cancelled = false;
    let attempts = 0;

    async function tryResolve() {
      attempts += 1;
      try {
        const session = await findMySession(roomId);
        if (!cancelled && session) {
          setSessionId(session.sessionId);
          return;
        }
      } catch {
        // ignora e tenta de novo
      }
      if (!cancelled && attempts < 6) {
        window.setTimeout(tryResolve, 2000);
      }
    }

    void tryResolve();
    return () => {
      cancelled = true;
    };
  }, [roomId, connected, sessionId]);

  return sessionId;
}
