import { describe, expect, it } from 'vitest';
import { appointmentLink, matchAppointmentPath } from './appointmentLink';

describe('appointmentLink', () => {
  it('builds a permanent link from the current origin', () => {
    expect(appointmentLink('abc123xyz')).toBe(`${window.location.origin}/r/abc123xyz`);
  });
});

describe('matchAppointmentPath', () => {
  it('extracts the publicAccessId from /r/:id', () => {
    expect(matchAppointmentPath('/r/abc123xyz')).toBe('abc123xyz');
  });

  it('handles a trailing slash', () => {
    expect(matchAppointmentPath('/r/abc123xyz/')).toBe('abc123xyz');
  });

  it('returns null for other paths', () => {
    expect(matchAppointmentPath('/room/room-abc')).toBeNull();
    expect(matchAppointmentPath('/')).toBeNull();
    expect(matchAppointmentPath('/r/')).toBeNull();
  });
});
