import {
  useConnectionQualityIndicator,
  useLocalParticipant,
  useParticipants,
} from '@livekit/components-react';
import type { Participant } from 'livekit-client';
import { useState } from 'react';
import { ConnectionQualityIndicator } from '../../components/ConnectionQualityIndicator';
import type { ConnectionMetrics, QualityLevel } from '../../types/connectionQuality';
import { mapLiveKitQuality } from './livekitMetrics';
import { useRemoteMetrics } from './useRemoteMetrics';

interface Props {
  localLevel: QualityLevel | null;
  localMetrics: ConnectionMetrics;
  localReconnectCount: number;
}

function RemoteQualityRow({ participant }: { participant: Participant }) {
  const { quality } = useConnectionQualityIndicator({ participant });
  const metrics = useRemoteMetrics(participant);
  const name = participant.name || participant.identity;
  return (
    <li className="flex items-center justify-between gap-3">
      <span className="truncate text-slate-200">{name}</span>
      <ConnectionQualityIndicator
        level={mapLiveKitQuality(quality)}
        metrics={metrics}
        participantName={name}
        metricsSource="recepcao"
      />
    </li>
  );
}

export function QualityPanel({ localLevel, localMetrics, localReconnectCount }: Props) {
  const [collapsed, setCollapsed] = useState(false);
  const { localParticipant } = useLocalParticipant();
  const participants = useParticipants();
  const remotes = participants.filter((p) => p.identity !== localParticipant?.identity);

  return (
    <div className="absolute right-3 top-3 z-30 w-60 max-w-[70vw] rounded-lg border border-slate-700 bg-slate-900/90 text-xs shadow-lg backdrop-blur">
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        className="flex w-full items-center justify-between px-3 py-2 font-medium text-slate-200"
        aria-expanded={!collapsed}
      >
        Qualidade da conexao
        <span aria-hidden>{collapsed ? '▸' : '▾'}</span>
      </button>
      {!collapsed && (
        <ul className="flex flex-col gap-2 px-3 pb-3">
          <li className="flex items-center justify-between gap-3">
            <span className="truncate text-slate-200">
              {localParticipant?.name || localParticipant?.identity || 'Voce'}{' '}
              <span className="text-slate-500">(voce)</span>
            </span>
            <ConnectionQualityIndicator
              level={localLevel}
              metrics={localMetrics}
              reconnectCount={localReconnectCount}
              participantName={localParticipant?.name || 'Voce'}
            />
          </li>
          {remotes.map((p) => (
            <RemoteQualityRow key={p.sid} participant={p} />
          ))}
        </ul>
      )}
    </div>
  );
}
