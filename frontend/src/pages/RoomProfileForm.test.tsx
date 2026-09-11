import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { RoomProfile } from '../types';

const { createRoomProfile, updateRoomProfile, deleteRoomProfile } = vi.hoisted(() => ({
  createRoomProfile: vi.fn(),
  updateRoomProfile: vi.fn(),
  deleteRoomProfile: vi.fn(),
}));

vi.mock('../services/api', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../services/api')>()),
  createRoomProfile: (...a: unknown[]) => createRoomProfile(...a),
  updateRoomProfile: (...a: unknown[]) => updateRoomProfile(...a),
  deleteRoomProfile: (...a: unknown[]) => deleteRoomProfile(...a),
}));

// eslint-disable-next-line import/first
import { RoomProfileForm } from './RoomProfileForm';

const existing: RoomProfile = {
  id: 'p1',
  name: 'Aula de Ingles',
  durationMinutes: 20,
  type: 'LESSON',
  createdAt: '2026-08-28T10:00:00Z',
  updatedAt: '2026-08-28T10:00:00Z',
};

beforeEach(() => {
  createRoomProfile.mockReset();
  updateRoomProfile.mockReset();
  deleteRoomProfile.mockReset();
});

describe('RoomProfileForm', () => {
  it('creates a profile with the entered values', async () => {
    createRoomProfile.mockResolvedValue({ ...existing, id: 'new' });
    const onSaved = vi.fn();
    render(<RoomProfileForm onSaved={onSaved} onDeleted={vi.fn()} onCancel={vi.fn()} />);

    await userEvent.type(screen.getByLabelText('Nome'), 'Teleconsulta');
    await userEvent.clear(screen.getByLabelText('Duracao'));
    await userEvent.type(screen.getByLabelText('Duracao'), '30');
    await userEvent.selectOptions(screen.getByLabelText('Tipo'), 'CONSULTATION');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(createRoomProfile).toHaveBeenCalledWith({
      name: 'Teleconsulta',
      durationMinutes: 30,
      type: 'CONSULTATION',
    });
    expect(onSaved).toHaveBeenCalled();
  });

  it('blocks submit for an out-of-range duration', async () => {
    render(<RoomProfileForm onSaved={vi.fn()} onDeleted={vi.fn()} onCancel={vi.fn()} />);

    await userEvent.clear(screen.getByLabelText('Duracao'));
    await userEvent.type(screen.getByLabelText('Duracao'), '999');

    expect(screen.getByRole('button', { name: 'Salvar' })).toBeDisabled();
    expect(screen.getByText('Entre 1 e 480 minutos.')).toBeInTheDocument();
  });

  it('edits an existing profile', async () => {
    updateRoomProfile.mockResolvedValue(existing);
    const onSaved = vi.fn();
    render(
      <RoomProfileForm existing={existing} onSaved={onSaved} onDeleted={vi.fn()} onCancel={vi.fn()} />,
    );

    await userEvent.clear(screen.getByLabelText('Nome'));
    await userEvent.type(screen.getByLabelText('Nome'), 'Aula Intensiva');
    await userEvent.click(screen.getByRole('button', { name: 'Salvar' }));

    expect(updateRoomProfile).toHaveBeenCalledWith('p1', {
      name: 'Aula Intensiva',
      durationMinutes: 20,
      type: 'LESSON',
    });
  });

  it('deletes an existing profile', async () => {
    deleteRoomProfile.mockResolvedValue(undefined);
    const onDeleted = vi.fn();
    render(
      <RoomProfileForm existing={existing} onSaved={vi.fn()} onDeleted={onDeleted} onCancel={vi.fn()} />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Excluir' }));

    expect(deleteRoomProfile).toHaveBeenCalledWith('p1');
    expect(onDeleted).toHaveBeenCalledWith('p1');
  });
});
