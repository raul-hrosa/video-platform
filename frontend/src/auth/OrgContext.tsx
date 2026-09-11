import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import * as api from '../services/api';
import type { Organization } from '../types';

interface OrgContextValue {
  organization: Organization | null;
  loading: boolean;
  /** Recarrega a Organization (ex.: após renomear nas Configurações). */
  refresh: () => Promise<void>;
}

const OrgContext = createContext<OrgContextValue | null>(null);

/**
 * Contexto da Organization atual (Sprint 7). Montado apenas na área autenticada
 * — convidados nunca carregam Organization. O `organizationId` nunca é enviado
 * pelo frontend; vem sempre resolvido do backend.
 */
export function OrgProvider({ children }: { children: ReactNode }) {
  const [organization, setOrganization] = useState<Organization | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      setOrganization(await api.getCurrentOrganization());
    } catch {
      setOrganization(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <OrgContext.Provider value={{ organization, loading, refresh }}>{children}</OrgContext.Provider>
  );
}

export function useOrg(): OrgContextValue {
  const ctx = useContext(OrgContext);
  if (!ctx) throw new Error('useOrg precisa estar dentro de <OrgProvider>');
  return ctx;
}

/** Versão que não lança fora do provider — para componentes de header opcionais. */
export function useOrgOptional(): OrgContextValue | null {
  return useContext(OrgContext);
}
