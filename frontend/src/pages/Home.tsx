import { useState } from 'react';
import { AppointmentsScreen } from './AppointmentsScreen';
import { HistoryScreen } from './HistoryScreen';
import { MembersScreen } from './MembersScreen';
import { OrganizationSettings } from './OrganizationSettings';
import { RoomDetailScreen } from './RoomDetailScreen';
import { RoomEntry } from './RoomEntry';
import { RoomsConsole } from './RoomsConsole';

interface Props {
  onRoomSelected: (roomId: string) => void;
  onCreateFromProfile: (profileId: string) => void;
  onCreateDirect: () => void;
}

type View =
  | { name: 'console' }
  | { name: 'profiles' }
  | { name: 'history'; profileId?: string }
  | { name: 'detail'; roomId: string; from: 'history' | 'appointments' | 'console' }
  | { name: 'appointments' }
  | { name: 'org-settings' }
  | { name: 'members' };

/**
 * Casca de navegação da área autenticada. Sem router. A tela inicial é o
 * console de Rooms (Sprint 9 §5); o resto vive na navegação secundária.
 */
export function Home({ onRoomSelected, onCreateFromProfile, onCreateDirect }: Props) {
  const [view, setView] = useState<View>({ name: 'console' });

  if (view.name === 'profiles') {
    return (
      <RoomEntry
        onRoomSelected={onRoomSelected}
        onCreateFromProfile={onCreateFromProfile}
        onOpenHistory={(profileId) => setView({ name: 'history', profileId })}
        onOpenAppointments={() => setView({ name: 'appointments' })}
        onOpenOrgSettings={() => setView({ name: 'org-settings' })}
        onOpenMembers={() => setView({ name: 'members' })}
        onBack={() => setView({ name: 'console' })}
      />
    );
  }

  if (view.name === 'history') {
    return (
      <HistoryScreen
        initialProfileId={view.profileId}
        onBack={() => setView({ name: 'console' })}
        onCreate={() => setView({ name: 'console' })}
        onOpenDetail={(roomId) => setView({ name: 'detail', roomId, from: 'history' })}
      />
    );
  }

  if (view.name === 'appointments') {
    return (
      <AppointmentsScreen
        onBack={() => setView({ name: 'console' })}
        onOpenRoom={(roomId) => setView({ name: 'detail', roomId, from: 'appointments' })}
      />
    );
  }

  if (view.name === 'detail') {
    const back: View =
      view.from === 'appointments'
        ? { name: 'appointments' }
        : view.from === 'history'
          ? { name: 'history' }
          : { name: 'console' };
    return (
      <RoomDetailScreen
        roomId={view.roomId}
        onBack={() => setView(back)}
        onRejoin={onRoomSelected}
      />
    );
  }

  if (view.name === 'org-settings') {
    return <OrganizationSettings onBack={() => setView({ name: 'console' })} />;
  }

  if (view.name === 'members') {
    return <MembersScreen onBack={() => setView({ name: 'console' })} />;
  }

  return (
    <RoomsConsole
      onOpenRoom={(roomId) => setView({ name: 'detail', roomId, from: 'console' })}
      onJoinRoom={onRoomSelected}
      onCreateRoom={onCreateDirect}
      onOpenProfiles={() => setView({ name: 'profiles' })}
      onOpenHistory={() => setView({ name: 'history' })}
      onOpenAppointments={() => setView({ name: 'appointments' })}
      onOpenOrgSettings={() => setView({ name: 'org-settings' })}
      onOpenMembers={() => setView({ name: 'members' })}
    />
  );
}
