interface Props {
  onBack: () => void;
}

/** Sprint 5 §46 — link de uma sala expirada. Nao abre o Device Setup. */
export function RoomExpiredNotice({ onBack }: Props) {
  return (
    <div className="mx-auto flex min-h-full max-w-md flex-col items-center justify-center gap-4 p-6 text-center">
      <h1 className="text-xl font-semibold text-slate-100">Sala expirada</h1>
      <p className="text-sm text-slate-400">Esta sala nao aceita novos participantes.</p>
      <button
        type="button"
        onClick={onBack}
        className="rounded-lg bg-indigo-600 px-4 py-3 font-semibold text-white hover:bg-indigo-500"
      >
        Voltar
      </button>
    </div>
  );
}
