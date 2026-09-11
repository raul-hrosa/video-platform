import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CopyLinkButton } from './CopyLinkButton';

describe('CopyLinkButton', () => {
  it('copies the room link and shows feedback', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });

    render(<CopyLinkButton roomId="room-abc" />);
    await userEvent.click(screen.getByRole('button', { name: /Copiar link/ }));

    expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/room/room-abc`);
    expect(await screen.findByText('Link copiado!')).toBeInTheDocument();
  });

  it('stays usable when the clipboard is blocked', async () => {
    const writeText = vi.fn().mockImplementation(() => Promise.reject(new Error('denied')));
    Object.assign(navigator, { clipboard: { writeText } });

    render(<CopyLinkButton roomId="room-abc" />);
    await userEvent.click(screen.getByRole('button', { name: /Copiar link/ }));

    expect(screen.queryByText('Link copiado!')).not.toBeInTheDocument();
  });
});
