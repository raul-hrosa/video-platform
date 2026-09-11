package com.videoplatform.organization;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Matriz de permissoes da Organization, centralizada (Sprint 7 §25). Nenhuma
 * regra de autorizacao por role fica espalhada nos controllers/services alem de
 * uma chamada a este componente.
 *
 * <pre>
 * Operacao              OWNER   ADMIN   MEMBER
 * Ver Organization        v       v       v
 * Ver membros             v       v       -
 * Criar Profile           v       v       v
 * Editar Profile          v       v       so o dono do Profile
 * Excluir Profile         v       v       -
 * Criar Room              v       v       v
 * Ver historico/analytics v       v       v
 * Gerenciar membros       v       v       -
 * Alterar Organization    v       -       -
 * </pre>
 */
@Component
public class OrgPolicy {

    private static final Logger log = LoggerFactory.getLogger(OrgPolicy.class);

    public void requireCanUpdateOrganization(OrganizationContext ctx) {
        if (!ctx.isOwner()) {
            denied(ctx, "update organization");
        }
    }

    public void requireCanViewMembers(OrganizationContext ctx) {
        if (!ctx.isAdminOrOwner()) {
            denied(ctx, "view members");
        }
    }

    public void requireCanManageMembers(OrganizationContext ctx) {
        if (!ctx.isAdminOrOwner()) {
            denied(ctx, "manage members");
        }
    }

    /** Editar Profile: OWNER, ADMIN, ou o proprio criador do Profile. */
    public void requireCanEditProfile(OrganizationContext ctx, UUID profileOwnerId) {
        if (!ctx.isAdminOrOwner() && !ctx.userId().equals(profileOwnerId)) {
            denied(ctx, "edit profile");
        }
    }

    public void requireCanDeleteProfile(OrganizationContext ctx) {
        if (!ctx.isAdminOrOwner()) {
            denied(ctx, "delete profile");
        }
    }

    /** Editar/cancelar Appointment (Sprint 8 §26/§27): OWNER, ADMIN ou o proprio criador. */
    public void requireCanManageAppointment(OrganizationContext ctx, UUID appointmentOwnerId) {
        if (!ctx.isAdminOrOwner() && !ctx.userId().equals(appointmentOwnerId)) {
            denied(ctx, "manage appointment");
        }
    }

    private void denied(OrganizationContext ctx, String operation) {
        log.atWarn()
                .addKeyValue("event", LogEvents.ORGANIZATION_ACCESS_DENIED)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .addKeyValue("role", ctx.role())
                .addKeyValue("operation", operation)
                .setMessage("organization operation denied by role")
                .log();
        throw new ApiException(HttpStatus.FORBIDDEN, "INSUFFICIENT_ROLE",
                "Voce nao possui permissao para realizar esta operacao.");
    }
}
