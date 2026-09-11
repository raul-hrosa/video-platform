import { useOrgOptional } from '../auth/OrgContext';

/**
 * Indicação simples da Organization atual no header (Sprint 7 §37). Sem
 * dropdown/switcher — apenas o nome (uma Organization por usuário nesta sprint).
 */
export function OrgBadge() {
  const ctx = useOrgOptional();
  if (!ctx || ctx.loading || !ctx.organization) return null;
  const { organization } = ctx;
  return (
    <span
      className="inline-flex max-w-[12rem] items-center gap-1 truncate rounded-lg border border-slate-700 bg-slate-800/60 px-2.5 py-1 text-sm text-slate-200"
      title={organization.name}
    >
      <span aria-hidden>🏢</span>
      <span className="truncate">{organization.name}</span>
    </span>
  );
}
