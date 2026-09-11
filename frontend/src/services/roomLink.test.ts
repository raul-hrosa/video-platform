import { describe, expect, it } from 'vitest';
import { matchRoomPath, parseRoomId, roomLink } from './roomLink';

describe('roomLink', () => {
  it('builds a link from the current origin', () => {
    expect(roomLink('room-abc')).toBe(`${window.location.origin}/room/room-abc`);
  });
});

describe('parseRoomId', () => {
  it('returns a bare code unchanged', () => {
    expect(parseRoomId('room-abc123')).toBe('room-abc123');
  });

  it('extracts the code from a full URL', () => {
    expect(parseRoomId('https://app.example.com/room/room-abc123')).toBe('room-abc123');
  });

  it('ignores surrounding whitespace and query strings', () => {
    expect(parseRoomId('  https://app.example.com/room/room-xyz?foo=1  ')).toBe('room-xyz');
  });

  it('handles a trailing slash', () => {
    expect(parseRoomId('http://localhost:5173/room/room-abc/')).toBe('room-abc');
  });
});

describe('matchRoomPath', () => {
  it('extracts the roomId from /room/:id', () => {
    expect(matchRoomPath('/room/room-abc123')).toBe('room-abc123');
    expect(matchRoomPath('/room/room-abc123/')).toBe('room-abc123');
  });

  it('returns null for other paths', () => {
    expect(matchRoomPath('/')).toBeNull();
    expect(matchRoomPath('/rooms')).toBeNull();
    expect(matchRoomPath('/room/')).toBeNull();
    expect(matchRoomPath('/room/a/b')).toBeNull();
  });
});
