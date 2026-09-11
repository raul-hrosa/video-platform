package com.videoplatform.organization;

import com.videoplatform.common.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Matriz de permissoes da Sprint 7 §25, centralizada em {@link OrgPolicy}.
 */
class OrgPolicyTest {

    private static final UUID ORG = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
    private static final UUID CREATOR = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("22222222-0000-0000-0000-000000000002");

    private final OrgPolicy policy = new OrgPolicy();

    private static OrganizationContext ctx(OrgRole role, UUID userId) {
        return new OrganizationContext(ORG, userId, role);
    }

    @Test
    void onlyOwnerUpdatesOrganization() {
        assertThatCode(() -> policy.requireCanUpdateOrganization(ctx(OrgRole.OWNER, OTHER)))
                .doesNotThrowAnyException();
        assertInsufficientRole(() -> policy.requireCanUpdateOrganization(ctx(OrgRole.ADMIN, OTHER)));
        assertInsufficientRole(() -> policy.requireCanUpdateOrganization(ctx(OrgRole.MEMBER, OTHER)));
    }

    @Test
    void ownerAndAdminViewAndManageMembers() {
        assertThatCode(() -> policy.requireCanViewMembers(ctx(OrgRole.ADMIN, OTHER)))
                .doesNotThrowAnyException();
        assertInsufficientRole(() -> policy.requireCanViewMembers(ctx(OrgRole.MEMBER, OTHER)));
        assertInsufficientRole(() -> policy.requireCanManageMembers(ctx(OrgRole.MEMBER, OTHER)));
    }

    @Test
    void editProfileAllowedForAdminOwnerOrTheCreator() {
        assertThatCode(() -> policy.requireCanEditProfile(ctx(OrgRole.MEMBER, CREATOR), CREATOR))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanEditProfile(ctx(OrgRole.ADMIN, OTHER), CREATOR))
                .doesNotThrowAnyException();
        assertInsufficientRole(() -> policy.requireCanEditProfile(ctx(OrgRole.MEMBER, OTHER), CREATOR));
    }

    @Test
    void deleteProfileForbiddenForMember() {
        assertThatCode(() -> policy.requireCanDeleteProfile(ctx(OrgRole.ADMIN, OTHER)))
                .doesNotThrowAnyException();
        assertInsufficientRole(() -> policy.requireCanDeleteProfile(ctx(OrgRole.MEMBER, CREATOR)));
    }

    private static void assertInsufficientRole(org.junit.jupiter.api.function.Executable exec) {
        assertThatThrownBy(exec::execute)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getErrorCode()).isEqualTo("INSUFFICIENT_ROLE");
                    assertThat(api.getStatus().value()).isEqualTo(403);
                });
    }
}
