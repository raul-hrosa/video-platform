package com.videoplatform.organization;

import com.videoplatform.auth.User;
import com.videoplatform.auth.UserRepository;
import com.videoplatform.common.ApiException;
import com.videoplatform.organization.dto.MemberResponse;
import com.videoplatform.organization.dto.OrganizationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
    private static final UUID USER = UUID.fromString("11111111-0000-0000-0000-000000000001");

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;

    private OrganizationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationService(organizationRepository, memberRepository, userRepository,
                new OrgPolicy());
        MDC.clear();
    }

    @Test
    void contextForResolvesMembershipAndPublishesMdc() {
        when(memberRepository.findFirstByUserIdOrderByCreatedAtAsc(USER))
                .thenReturn(Optional.of(OrganizationMember.of(ORG, USER, OrgRole.ADMIN)));

        OrganizationContext ctx = service.contextFor(USER);

        assertThat(ctx.organizationId()).isEqualTo(ORG);
        assertThat(ctx.userId()).isEqualTo(USER);
        assertThat(ctx.role()).isEqualTo(OrgRole.ADMIN);
        assertThat(MDC.get("organizationId")).isEqualTo(ORG.toString());
    }

    @Test
    void contextForFailsWhenUserHasNoMembership() {
        when(memberRepository.findFirstByUserIdOrderByCreatedAtAsc(USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.contextFor(USER))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo("MEMBERSHIP_NOT_FOUND"));
    }

    @Test
    void provisionPersonalOrgCreatesOrgAndOwnerMembership() {
        User user = User.register("Joao da Silva", "joao@x.com", "hash");
        setId(user, USER);
        when(organizationRepository.save(any(Organization.class))).thenAnswer(i -> i.getArgument(0));

        Organization org = service.provisionPersonalOrg(user);

        assertThat(org.getName()).isEqualTo("Joao da Silva");
        assertThat(org.getSlug()).startsWith("joao-da-silva-");
        verify(memberRepository).save(any(OrganizationMember.class));
    }

    @Test
    void updateNameRejectsNonOwner() {
        assertThatThrownBy(() -> service.updateName(
                new OrganizationContext(ORG, USER, OrgRole.ADMIN), "Novo Nome"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo("INSUFFICIENT_ROLE"));
    }

    @Test
    void updateNameRenamesForOwner() {
        Organization org = Organization.create("Antigo", "antigo-123");
        when(organizationRepository.findById(ORG)).thenReturn(Optional.of(org));

        OrganizationResponse res = service.updateName(
                new OrganizationContext(ORG, USER, OrgRole.OWNER), "  Clinica ABC Saude  ");

        assertThat(res.name()).isEqualTo("Clinica ABC Saude");
        assertThat(res.slug()).isEqualTo("antigo-123"); // slug imutavel
    }

    @Test
    void listMembersRequiresAdminOrOwnerAndJoinsUserData() {
        when(memberRepository.findByOrganizationIdOrderByCreatedAtAsc(ORG)).thenReturn(List.of(
                OrganizationMember.of(ORG, USER, OrgRole.OWNER)));
        User u = User.register("Joao", "joao@x.com", "hash");
        setId(u, USER);
        when(userRepository.findAllById(any())).thenReturn(List.of(u));

        List<MemberResponse> members = service.listMembers(new OrganizationContext(ORG, USER, OrgRole.OWNER));

        assertThat(members).singleElement().satisfies(m -> {
            assertThat(m.name()).isEqualTo("Joao");
            assertThat(m.email()).isEqualTo("joao@x.com");
            assertThat(m.role()).isEqualTo(OrgRole.OWNER);
        });
    }

    @Test
    void listMembersForbiddenForMember() {
        assertThatThrownBy(() -> service.listMembers(new OrganizationContext(ORG, USER, OrgRole.MEMBER)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo("INSUFFICIENT_ROLE"));
    }

    private static void setId(User user, UUID id) {
        try {
            var f = User.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
