import { useMemo } from 'react';
import { AuthProvider, useAuth } from './auth/AuthContext';
import { OrgProvider } from './auth/OrgContext';
import { config } from './config';
import { ErrorNotice } from './components/ErrorNotice';
import { useAppState } from './hooks/useAppState';
import { useRoomDeepLink } from './hooks/useRoomDeepLink';
import { matchRoomPath } from './services/roomLink';
import { matchAppointmentPath } from './services/appointmentLink';
import { AppointmentGuestApp } from './pages/AppointmentGuestApp';
import { AuthScreen } from './pages/AuthScreen';
import { CallRoom } from './pages/CallRoom';
import { DeviceSetup } from './pages/DeviceSetup';
import { GuestApp } from './pages/GuestApp';
import { Home } from './pages/Home';
import { RoomExpiredNotice } from './pages/RoomExpiredNotice';

function VideoApp({ userName }: { userName: string }) {
  const {
    state,
    roomId,
    session,
    createdRoom,
    error,
    selectRoom,
    createRoom,
    createDirectRoom,
    join,
    leave,
    fail,
    reset,
  } = useAppState(config.livekitUrl);

  useRoomDeepLink(selectRoom);

  // Com PulseRTC o `serverUrl` vem no token (§6); so o LiveKit exige URL local.
  if (config.mediaProvider === 'livekit' && !config.livekitUrl) {
    return (
      <div className="mx-auto max-w-md p-6">
        <ErrorNotice
          error={{
            code: 'CONFIG_MISSING',
            message: 'VITE_LIVEKIT_URL nao foi configurada. Preencha o .env e reinicie.',
          }}
        />
      </div>
    );
  }

  if (state === 'CONNECTED' && session) {
    return (
      <CallRoom
        serverUrl={session.serverUrl}
        token={session.token}
        participantId={session.participantId}
        joinConfig={session.joinConfig}
        onLeave={leave}
        onError={fail}
      />
    );
  }

  if (state === 'ROOM_EXPIRED') {
    return <RoomExpiredNotice onBack={reset} />;
  }

  if (state === 'DISCONNECTED' || state === 'ERROR') {
    return (
      <div className="mx-auto flex min-h-full max-w-md flex-col justify-center gap-4 p-6 text-center">
        <h1 className="text-xl font-semibold text-slate-100">
          {state === 'ERROR' ? 'Problema de conexao' : 'Voce saiu da chamada'}
        </h1>
        {error && <ErrorNotice error={error} />}
        <button
          type="button"
          onClick={reset}
          className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white hover:bg-indigo-500"
        >
          Voltar ao inicio
        </button>
      </div>
    );
  }

  if (state === 'ROOM_CREATING') {
    return (
      <div className="mx-auto flex min-h-full max-w-md items-center justify-center p-6 text-slate-400">
        Criando sala...
      </div>
    );
  }

  if (state === 'INITIAL' || !roomId) {
    return (
      <Home
        onRoomSelected={selectRoom}
        onCreateFromProfile={createRoom}
        onCreateDirect={createDirectRoom}
      />
    );
  }

  return (
    <DeviceSetup
      roomId={roomId}
      userName={userName}
      createdRoom={createdRoom}
      joining={state === 'CONNECTING'}
      joinError={error}
      onJoin={join}
      onBack={reset}
    />
  );
}

function Gate() {
  const { status, user } = useAuth();
  const guestRoomId = useMemo(() => matchRoomPath(), []);
  const appointmentId = useMemo(() => matchAppointmentPath(), []);

  // Link permanente de atendimento (/r/{id}): fluxo publico, autenticado ou nao (§28, §31).
  if (appointmentId) {
    return <AppointmentGuestApp publicAccessId={appointmentId} />;
  }

  if (status === 'loading') {
    return (
      <div className="flex min-h-full items-center justify-center p-6 text-slate-400">Carregando...</div>
    );
  }
  if (status === 'authenticated' && user) {
    return (
      <OrgProvider>
        <VideoApp userName={user.name} />
      </OrgProvider>
    );
  }
  // Anonimo: se veio por um link de sala, entra como visitante (sem conta).
  if (guestRoomId) {
    return <GuestApp roomId={guestRoomId} />;
  }
  return <AuthScreen />;
}

export default function App() {
  return (
    <AuthProvider>
      <Gate />
    </AuthProvider>
  );
}
