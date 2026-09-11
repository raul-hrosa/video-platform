package com.videoplatform.organization;

import com.videoplatform.auth.User;
import com.videoplatform.auth.UserRepository;
import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.dto.MemberResponse;
import com.videoplatform.organization.dto.OrganizationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ciclo de vida da Organization e resolucao do {@link OrganizationContext} a
 * partir do usuario autenticado (Sprint 7 §7, §8, §26–§30). O tenant nunca vem
 * do frontend.
 */
@Service
public class OrganizationService {

    public static final String MDC_KEY = "organizationId";

    private static final Logger log = LoggerFactory.getLogger(OrganizationService.class);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OrgPolicy policy;

    public OrganizationService(OrganizationRepository organizationRepository,
                               OrganizationMemberRepository memberRepository,
                               UserRepository userRepository,
                               OrgPolicy policy) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.policy = policy;
    }

    /**
     * Resolve o contexto da Organization do usuario. Sem membership -> 403
     * {@code MEMBERSHIP_NOT_FOUND} (Sprint 7 §8: nenhum usuario deve ficar sem
     * Organization; se acontecer e' erro de migracao). Publica {@code
     * organizationId} no MDC para os logs da requisicao (§35).
     */
    @Transactional(readOnly = true)
    public OrganizationContext contextFor(UUID userId) {
        OrganizationMember member = memberRepository.findFirstByUserIdOrderByCreatedAtAsc(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "MEMBERSHIP_NOT_FOUND",
                        "Usuario sem Organization. Contate o suporte."));
        MDC.put(MDC_KEY, member.getOrganizationId().toString());
        return new OrganizationContext(member.getOrganizationId(), userId, member.getRole());
    }

    /**
     * Cria a Organization pessoal do usuario recem-cadastrado e o torna OWNER
     * (Sprint 7 §7). Chamado dentro da transacao de registro.
     */
    @Transactional
    public Organization provisionPersonalOrg(User user) {
        String slug = Slug.of(user.getName()) + "-"
                + user.getId().toString().replace("-", "").substring(0, 8);
        Organization org = organizationRepository.save(Organization.create(user.getName(), slug));
        memberRepository.save(OrganizationMember.of(org.getId(), user.getId(), OrgRole.OWNER));
        log.atInfo()
                .addKeyValue("event", LogEvents.ORGANIZATION_CREATED)
                .addKeyValue("organizationId", org.getId())
                .addKeyValue("userId", user.getId())
                .setMessage("personal organization created")
                .log();
        return org;
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getCurrent(OrganizationContext ctx) {
        Organization org = load(ctx.organizationId());
        log.atInfo()
                .addKeyValue("event", LogEvents.ORGANIZATION_VIEWED)
                .addKeyValue("organizationId", org.getId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("organization viewed")
                .log();
        return OrganizationResponse.from(org, ctx.role());
    }

    @Transactional
    public OrganizationResponse updateName(OrganizationContext ctx, String rawName) {
        policy.requireCanUpdateOrganization(ctx);
        Organization org = load(ctx.organizationId());
        org.rename(rawName.trim());
        log.atInfo()
                .addKeyValue("event", LogEvents.ORGANIZATION_UPDATED)
                .addKeyValue("organizationId", org.getId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("organization updated")
                .log();
        return OrganizationResponse.from(org, ctx.role());
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(OrganizationContext ctx) {
        policy.requireCanViewMembers(ctx);
        List<OrganizationMember> members =
                memberRepository.findByOrganizationIdOrderByCreatedAtAsc(ctx.organizationId());
        Map<UUID, User> users = userRepository.findAllById(
                        members.stream().map(OrganizationMember::getUserId).toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        log.atInfo()
                .addKeyValue("event", LogEvents.MEMBER_VIEWED)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .addKeyValue("resultCount", members.size())
                .setMessage("organization members viewed")
                .log();
        return members.stream()
                .map(m -> {
                    User u = users.get(m.getUserId());
                    return new MemberResponse(
                            m.getUserId(),
                            u != null ? u.getName() : null,
                            u != null ? u.getEmail() : null,
                            m.getRole(),
                            m.getCreatedAt());
                })
                .sorted(Comparator.comparing(MemberResponse::role))
                .toList();
    }

    private Organization load(UUID organizationId) {
        return organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORGANIZATION_NOT_FOUND",
                        "Organization nao encontrada."));
    }
}
