import type { DeviceOption } from '../types';

interface Props {
  label: string;
  devices: DeviceOption[];
  value?: string;
  onChange: (deviceId: string) => void;
  disabled?: boolean;
}

export function DeviceSelect({ label, devices, value, onChange, disabled }: Props) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block font-medium text-slate-300">{label}</span>
      <select
        className="w-full rounded-lg border border-slate-600 bg-slate-800 px-3 py-2 text-slate-100 disabled:opacity-50"
        value={value ?? ''}
        disabled={disabled || devices.length === 0}
        onChange={(e) => onChange(e.target.value)}
      >
        {devices.length === 0 && <option value="">Nenhum dispositivo encontrado</option>}
        {devices.map((d) => (
          <option key={d.deviceId} value={d.deviceId}>
            {d.label}
          </option>
        ))}
      </select>
    </label>
  );
}
