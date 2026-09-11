package com.videoplatform.roomprofile;

import com.videoplatform.common.ApiException;
import com.videoplatform.organization.OrgPolicy;
import com.videoplatform.organization.OrgRole;
import com.videoplatform.organization.OrganizationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomProfileServiceTest {

    private static final UUID ORG_A = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
    private static final UUID ORG_B = UUID.fromString("b0000000-0000-0000-0000-0000000000bb");
    private static final UUID CREATOR = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID OTHER_MEMBER = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID PROFILE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    @Mock
    private RoomProfileRepository repository;

    private final OrgPolicy policy = new OrgPolicy();
    private RoomProfileService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new RoomProfileService(repository, policy);
    }

    private static OrganizationContext ctx(UUID orgId, UUID userId, OrgRole role) {
        return new OrganizationContext(orgId, userId, role);
    }

    private static RoomProfile profile(UUID orgId, UUID creator) {
        return RoomProfile.create(orgId, creator, "Aula de Ingles", 20, RoomType.LESSON);
    }

    @Test
    void createUsesOrganizationAndUserFromContext() {
        when(repository.save(any(RoomProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        RoomProfile created = service.create(
                ctx(ORG_A, CREATOR, OrgRole.MEMBER), "  Aula de Ingles  ", 20, RoomType.LESSON);

        assertThat(created.getName()).isEqualTo("Aula de Ingles");
        assertThat(created.getOrganizationId()).isEqualTo(ORG_A);
        assertThat(created.getOwnerId()).isEqualTo(CREATOR);
        assertThat(created.isDeleted()).isFalse();
    }

    @Test
    void listReturnsActiveProfilesOfOrganization() {
        when(repository.findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(ORG_A))
                .thenReturn(List.of(profile(ORG_A, CREATOR)));

        assertThat(service.list(ctx(ORG_A, CREATOR, OrgRole.MEMBER))).hasSize(1);
        verify(repository).findByOrganizationIdAndDeletedAtIsNullOrderByCreatedAtAsc(ORG_A);
    }

    @Test
    void getInOrgThrowsNotFoundWhenMissingOrDeleted() {
        when(repository.findByIdAndDeletedAtIsNull(PROFILE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInOrg(PROFILE_ID, ctx(ORG_A, CREATOR, OrgRole.OWNER)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                        .isEqualTo("ROOM_PROFILE_NOT_FOUND"));
    }

    @Test
    void getInOrgHidesProfileFromAnotherOrganization() {
        when(repository.findByIdAndDeletedAtIsNull(PROFILE_ID))
                .thenReturn(Optional.of(profile(ORG_A, CREATOR)));

        assertThatThrownBy(() -> service.getInOrg(PROFILE_ID, ctx(ORG_B, OTHER_MEMBER, OrgRole.OWNER)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    // Recurso de outro tenant e' tratado como inexistente — nunca vaza.
                    assertThat(api.getErrorCode()).isEqualTo("ROOM_PROFILE_NOT_FOUND");
                    assertThat(api.getStatus().value()).isEqualTo(404);
                });
    }

    @Test
    void memberCanEditOwnProfile() {
        RoomProfile existing = profile(ORG_A, CREATOR);
        when(repository.findByIdAndDeletedAtIsNull(PROFILE_ID)).thenReturn(Optional.of(existing));

        RoomProfile updated = service.update(PROFILE_ID, ctx(ORG_A, CREATOR, OrgRole.MEMBER),
                "  Aula Intensiva ", 30, RoomType.MEETING);

        assertThat(updated.getName()).isEqualTo("Aula Intensiva");
        assertThat(updated.getDurationMinutes()).isEqualTo(30);
    }

    @Test
    void memberCannotEditProfileOfAnotherCreator() {
        when(repository.findByIdAndDeletedAtIsNull(PROFILE_ID))
                .thenReturn(Optional.of(profile(ORG_A, CREATOR)));

        assertThatThrownBy(() -> service.update(PROFILE_ID, ctx(ORG_A, OTHER_MEMBER, OrgRole.MEMBER),
                "x", 10, RoomType.OTHER))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo("INSUFFICIENT_ROLE"));
    }

    @Test
    void adminCanEditAnyProfileInOrganization() {
        RoomProfile existing = profile(ORG_A, CREATOR);
        when(repository.findByIdAndDeletedAtIsNull(PROFILE_ID)).thenReturn(Optional.of(existing));

        RoomProfile updated = service.update(PROFILE_ID, ctx(ORG_A, OTHER_MEMBER, OrgRole.ADMIN),
                "Novo", 15, RoomType.OTHER);

        assertThat(updated.getName()).isEqualTo("Novo");
    }

    @Test
    void memberCannotDeleteProfile() {
        when(repository.findByIdAndDeletedAtIsNull(PROFILE_ID))
                .thenReturn(Optional.of(profile(ORG_A, CREATOR)));

        assertThatThrownBy(() -> service.softDelete(PROFILE_ID, ctx(ORG_A, CREATOR, OrgRole.MEMBER)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo("INSUFFICIENT_ROLE"));
    }

    @Test
    void ownerSoftDeletesWithoutRemovingRow() {
        RoomProfile existing = profile(ORG_A, CREATOR);
        when(repository.findByIdAndDeletedAtIsNull(PROFILE_ID)).thenReturn(Optional.of(existing));

        service.softDelete(PROFILE_ID, ctx(ORG_A, CREATOR, OrgRole.OWNER));

        assertThat(existing.isDeleted()).isTrue();
        verify(repository, never()).delete(any());
        verify(repository, never()).deleteById(any());
    }
}
