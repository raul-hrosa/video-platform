package com.videoplatform.organization;

import java.util.UUID;

/**
 * Contexto da Organization do usuario autenticado (Sprint 7 §26). Resolvido por
 * request pelo {@link OrganizationContextResolver} a partir do membership — o
 * {@code organizationId} <b>nunca</b> vem do frontend (§3, §27).
 *
 * <p>Os controllers recebem este record via
 * {@code @AuthenticationPrincipal}-style injection; os services o usam para
 * aplicar isolamento e autorizacao.
 */
public record OrganizationContext(UUID organizationId, UUID userId, OrgRole role) {

    public boolean isOwner() {
        return role == OrgRole.OWNER;
    }

    public boolean isAdminOrOwner() {
        return role == OrgRole.OWNER || role == OrgRole.ADMIN;
    }
}
