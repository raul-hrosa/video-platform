import type { FriendlyError } from '../types';

interface Props {
  error: FriendlyError;
  onRetry?: () => void;
}

export function ErrorNotice({ error, onRetry }: Props) {
  return (
    <div className="rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200">
      <p>{error.message}</p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 rounded-md bg-red-500/20 px-3 py-1 font-medium text-red-100 hover:bg-red-500/30"
        >
          Tentar novamente
        </button>
      )}
    </div>
  );
}
