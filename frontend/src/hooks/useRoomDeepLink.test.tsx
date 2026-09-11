import { renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useRoomDeepLink } from './useRoomDeepLink';

afterEach(() => {
  window.history.replaceState(null, '', '/');
});

describe('useRoomDeepLink', () => {
  it('fires once with the roomId when the path matches /room/:id', () => {
    window.history.replaceState(null, '', '/room/room-abc123');
    const onRoom = vi.fn();

    const { rerender } = renderHook(() => useRoomDeepLink(onRoom));
    rerender();

    expect(onRoom).toHaveBeenCalledTimes(1);
    expect(onRoom).toHaveBeenCalledWith('room-abc123');
  });

  it('does nothing on other paths', () => {
    window.history.replaceState(null, '', '/');
    const onRoom = vi.fn();

    renderHook(() => useRoomDeepLink(onRoom));

    expect(onRoom).not.toHaveBeenCalled();
  });
});
