package com.videoplatform.roomprofile;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.OrgPolicy;
import com.videoplatform.organization.OrganizationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Regras de negocio dos Room Profiles. A partir da Sprint 7 um Profile pertence
 * a uma <b>Organization</b> (isolamento) e continua tendo um {@code ownerId}
 * (quem criou). O tenant vem sempre do {@link OrganizationContext} — nunca do
 * request (§3, §27). A autorizacao por role fica em {@link OrgPolicy} (§25).
 */
@Service
public class RoomProfileService {

    private static final Logger log = LoggerFactory.getLogger(RoomProfileService.class);

    private final RoomProfileRepository repository;
    private final OrgPolicy policy;

    public RoomProfileService(RoomProfileRepository repository, OrgPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    @Transactional
    public RoomProfile create(OrganizationContext ctx, String name, int durationMinutes, RoomType type) {
        RoomProfile profile = repository.save(RoomProfile.create(
                ctx.organizationId(), ctx.userId(), safeName(name), durationMinutes, type));
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_PROFILE_CREATED)
                .addKeyValue("roomProfileId", profile.getId())
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .addKeyValue("durationMinutes", durationMinutes)
                .addKeyValue("type", type.name())
                .setMessage("room profile created")
                .log();
        return profile;
    }

    /** Todos os Profiles ativos da Organization (Sprint 7 §21/§41). */
    @Transactional(readOnly = true)
    public List<RoomProfile> list(OrganizationContext ctx) {
        return repository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(ctx.organizationId());
    }

    /**
     * Resolve varios Profiles de uma vez (sem N+1 na listagem do historico).
     * Inclui Profiles com soft delete. Sem checagem de tenant — quem chama ja
     * validou o acesso a's Rooms de origem.
     */
    @Transactional(readOnly = true)
    public java.util.Map<UUID, RoomProfile> mapByIds(java.util.Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(RoomProfile::getId, p -> p));
    }

    /**
     * Consulta exigindo que o Profile exista (nao deletado) e pertenca a'
     * Organization do contexto. Um Profile de outra Organization e' tratado como
     * inexistente — nunca vaza dados (Sprint 7 §15).
     */
    @Transactional(readOnly = true)
    public RoomProfile getInOrg(UUID profileId, OrganizationContext ctx) {
        RoomProfile profile = repository.findByIdAndDeletedAtIsNull(profileId)
                .orElseThrow(() -> notFound(profileId, ctx));
        if (!profile.belongsToOrg(ctx.organizationId())) {
            log.atWarn()
                    .addKeyValue("event", LogEvents.ORGANIZATION_ACCESS_DENIED)
                    .addKeyValue("organizationId", ctx.organizationId())
                    .addKeyValue("resourceOrganizationId", profile.getOrganizationId())
                    .addKeyValue("roomProfileId", profileId)
                    .addKeyValue("userId", ctx.userId())
                    .setMessage("cross-organization room profile access blocked")
                    .log();
            throw notFound(profileId, ctx);
        }
        return profile;
    }

    @Transactional
    public RoomProfile update(UUID profileId, OrganizationContext ctx,
                              String name, int durationMinutes, RoomType type) {
        RoomProfile profile = getInOrg(profileId, ctx);
        policy.requireCanEditProfile(ctx, profile.getOwnerId());
        profile.update(safeName(name), durationMinutes, type);
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_PROFILE_UPDATED)
                .addKeyValue("roomProfileId", profileId)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("room profile updated")
                .log();
        return profile;
    }

    @Transactional
    public void softDelete(UUID profileId, OrganizationContext ctx) {
        RoomProfile profile = getInOrg(profileId, ctx);
        policy.requireCanDeleteProfile(ctx);
        profile.softDelete(Instant.now());
        log.atInfo()
                .addKeyValue("event", LogEvents.ROOM_PROFILE_DELETED)
                .addKeyValue("roomProfileId", profileId)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("room profile deleted (soft)")
                .log();
    }

    private ApiException notFound(UUID profileId, OrganizationContext ctx) {
        log.atWarn()
                .addKeyValue("event", LogEvents.ROOM_PROFILE_NOT_FOUND)
                .addKeyValue("roomProfileId", profileId)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("room profile not found")
                .log();
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_PROFILE_NOT_FOUND",
                "Perfil de sala nao encontrado.");
    }

    private static String safeName(String name) {
        return name == null ? null : name.trim();
    }
}
