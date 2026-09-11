interface Props {
  active: boolean;
  onToggle: () => void;
  labelOn: string;
  labelOff: string;
}

export function Toggle({ active, onToggle, labelOn, labelOff }: Props) {
  return (
    <button
      type="button"
      onClick={onToggle}
      className={`rounded-lg px-3 py-2 text-sm font-medium transition ${
        active
          ? 'bg-slate-700 text-slate-100 hover:bg-slate-600'
          : 'bg-red-500/20 text-red-200 hover:bg-red-500/30'
      }`}
    >
      {active ? labelOn : labelOff}
    </button>
  );
}
