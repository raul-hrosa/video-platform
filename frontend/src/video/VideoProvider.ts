import type { JoinConfig } from '../types';

export interface VideoProviderProps {
  serverUrl: string;
  token: string;
  participantId: string;
  joinConfig: JoinConfig;
  resolveSession?: boolean;
  onLeave: () => void;
  onError: (err: Error) => void;
}
