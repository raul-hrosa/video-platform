import { useEffect, useRef } from 'react';
import { matchRoomPath } from '../services/roomLink';

/**
 * Deep-link `/room/{roomId}` sem react-router (decisao Sprint 4). No mount, se a
 * URL casar, chama {@code onRoom} uma unica vez. A URL e' mantida — quem sai da
 * chamada (`reset`) e' que a limpa.
 */
export function useRoomDeepLink(onRoom: (roomId: string) => void): void {
  const fired = useRef(false);

  useEffect(() => {
    if (fired.current) return;
    const roomId = matchRoomPath();
    if (roomId) {
      fired.current = true;
      onRoom(roomId);
    }
  }, [onRoom]);
}
