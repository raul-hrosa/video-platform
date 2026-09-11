package com.videoplatform.room;

import com.videoplatform.common.ApiException;
import com.videoplatform.organization.OrgRole;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.roomprofile.RoomProfile;
import com.videoplatform.roomprofile.RoomType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    private static final UUID ORG_A = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
    private static final UUID ORG_B = UUID.fromString("b0000000-0000-0000-0000-0000000000bb");
    private static final UUID USER_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID USER_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID PROFILE = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    @Mock
    private RoomRepository roomRepository;
    @Mock
    private com.videoplatform.participant.ParticipantSessionRepository participantSessionRepository;

    @InjectMocks
    private RoomService roomService;

    private static OrganizationContext ctx(UUID orgId, UUID userId) {
        return new OrganizationContext(orgId, userId, OrgRole.OWNER);
    }

    private static Room room(String roomId, UUID orgId, UUID owner) {
        return Room.createFromProfile(roomId, PROFILE, orgId, "x", 60, owner,
                Instant.parse("2026-08-28T10:00:00Z"));
    }

    private static Room expiredRoom(String roomId) {
        return Room.createFromProfile(roomId, PROFILE, ORG_A, "x", 1, USER_A,
                Instant.parse("2020-01-01T00:00:00Z"));
    }

    private static RoomProfile profile(UUID orgId, UUID creator) {
        return RoomProfile.create(orgId, creator, "Aula de Ingles", 20, RoomType.LESSON);
    }

    @Test
    void createDirectCreatesRoomWithoutProfileNameOrExpiry() {
        when(roomRepository.existsByRoomId(anyString())).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        Room room = roomService.createDirect(ctx(ORG_A, USER_A));

        assertThat(room.getRoomId()).startsWith("room-");
        assertThat(room.getOrganizationId()).isEqualTo(ORG_A);
        assertThat(room.getOwnerId()).isEqualTo(USER_A);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(room.getName()).isNull();
        assertThat(room.getRoomProfileId()).isNull();
        assertThat(room.getDurationMinutes()).isNull();
        assertThat(room.getExpiresAt()).isNull();
        assertThat(room.creationMode()).isEqualTo(Room.CreationMode.DIRECT);
        assertThat(room.displayStatus()).isEqualTo("IDLE");
        assertThat(room.isExpired(Instant.now())).isFalse();
    }

    @Test
    void directRoomIsNeverExpiredEvenLongAfterCreation() {
        Room room = Room.createDirect("room-direct", ORG_A, USER_A,
                Instant.parse("2020-01-01T00:00:00Z"));

        assertThat(room.isExpired(Instant.parse("2030-01-01T00:00:00Z"))).isFalse();
    }

    @Test
    void createFromProfileInheritsOrgAndSetsCallerAsOwner() {
        when(roomRepository.existsByRoomId(anyString())).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenAnswer(inv -> inv.getArgument(0));

        Room room = roomService.createFromProfile(profile(ORG_A, USER_A), ctx(ORG_A, USER_B));

        assertThat(room.getRoomId()).startsWith("room-");
        assertThat(room.getName()).isEqualTo("Aula de Ingles");
        assertThat(room.getOrganizationId()).isEqualTo(ORG_A);
        assertThat(room.getOwnerId()).isEqualTo(USER_B);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
        assertThat(Duration.between(room.getCreatedAt(), room.getExpiresAt()).toMinutes()).isEqualTo(20);
    }

    private void stubFindAll(List<Room> result) {
        when(roomRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Room>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(result));
    }

    @Test
    void listHistoryAppliesCreatedAtDescByDefault() {
        var unsorted = org.springframework.data.domain.PageRequest.of(0, 20);
        stubFindAll(List.of(room("room-a", ORG_A, USER_A)));

        roomService.listHistory(ctx(ORG_A, USER_A),
                RoomHistoryQuery.of(RoomStatus.ENDED, PROFILE, null, null), unsorted);

        var pageable = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(roomRepository).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Room>>any(),
                pageable.capture());
        var sort = pageable.getValue().getSort().getOrderFor("createdAt");
        assertThat(sort).isNotNull();
        assertThat(sort.getDirection()).isEqualTo(org.springframework.data.domain.Sort.Direction.DESC);
    }

    @Test
    void listHistoryKeepsClientSortWhenProvided() {
        var asc = org.springframework.data.domain.PageRequest.of(0, 20,
                org.springframework.data.domain.Sort.by("createdAt").ascending());
        stubFindAll(List.of());

        roomService.listHistory(ctx(ORG_A, USER_A), RoomHistoryQuery.of(null, null, null, null), asc);

        var pageable = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(roomRepository).findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<Room>>any(),
                pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
    }

    @Test
    void getByRoomIdThrowsRoomNotFoundWhenMissing() {
        when(roomRepository.findByRoomId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getByRoomId("ghost"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo("ROOM_NOT_FOUND"));
    }

    @Test
    void getInOrgReturnsRoomForSameOrganization() {
        when(roomRepository.findByRoomId("room-abc")).thenReturn(Optional.of(room("room-abc", ORG_A, USER_A)));

        assertThat(roomService.getInOrg("room-abc", ctx(ORG_A, USER_B)).getRoomId()).isEqualTo("room-abc");
    }

    @Test
    void getInOrgHidesRoomFromAnotherOrganization() {
        when(roomRepository.findByRoomId("room-abc")).thenReturn(Optional.of(room("room-abc", ORG_A, USER_A)));

        assertThatThrownBy(() -> roomService.getInOrg("room-abc", ctx(ORG_B, USER_B)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getErrorCode()).isEqualTo("ROOM_NOT_FOUND");
                    assertThat(api.getStatus().value()).isEqualTo(404);
                });
    }

    @Test
    void getJoinableInOrgRejectsRoomFromAnotherOrganization() {
        when(roomRepository.findByRoomId("room-abc")).thenReturn(Optional.of(room("room-abc", ORG_A, USER_A)));

        assertThatThrownBy(() -> roomService.getJoinableInOrg("room-abc", ctx(ORG_B, USER_B)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo("ROOM_NOT_FOUND"));
    }

    @Test
    void getJoinableByRoomIdReturnsRoomWhenNotExpired() {
        Room room = Room.createFromProfile("room-abc", PROFILE, ORG_A, "x", 60, USER_A, Instant.now());
        when(roomRepository.findByRoomId("room-abc")).thenReturn(Optional.of(room));

        assertThat(roomService.getJoinableByRoomId("room-abc").getRoomId()).isEqualTo("room-abc");
    }

    @Test
    void getJoinableByRoomIdRejectsExpiredRoomWithoutWriting() {
        Room room = expiredRoom("room-old");
        when(roomRepository.findByRoomId("room-old")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.getJoinableByRoomId("room-old"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getErrorCode()).isEqualTo("ROOM_EXPIRED");
                    assertThat(api.getStatus().value()).isEqualTo(409);
                });
        assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
        verify(roomRepository, never()).save(any());
    }

    @Test
    void expireDueRoomsTransitionsWaitingAndActiveAndIsIdempotent() {
        Room waiting = expiredRoom("room-w");
        Room active = expiredRoom("room-a");
        active.markActive(Instant.parse("2020-01-01T00:00:30Z"));
        when(roomRepository.findByStatusInAndExpiresAtBefore(any(), any()))
                .thenReturn(List.of(waiting, active))
                .thenReturn(List.of());

        int first = roomService.expireDueRooms(Instant.now());
        int second = roomService.expireDueRooms(Instant.now());

        assertThat(first).isEqualTo(2);
        assertThat(second).isEqualTo(0);
        assertThat(waiting.getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(active.getStatus()).isEqualTo(RoomStatus.EXPIRED);
    }

    @Test
    void markActiveMovesWaitingRoomToActiveOnce() {
        Room room = room("room-abc", ORG_A, USER_A);
        when(roomRepository.findByRoomId("room-abc")).thenReturn(Optional.of(room));

        Instant first = Instant.parse("2026-08-28T10:05:00Z");
        roomService.markActive("room-abc", first);
        roomService.markActive("room-abc", Instant.parse("2026-08-28T10:09:00Z"));

        assertThat(room.getStatus()).isEqualTo(RoomStatus.ACTIVE);
        assertThat(room.getStartedAt()).isEqualTo(first);
    }

    @Test
    void markActiveDoesNotResurrectExpiredRoom() {
        Room room = expiredRoom("room-old");
        room.markExpired(Instant.parse("2020-01-01T00:02:00Z"));
        when(roomRepository.findByRoomId("room-old")).thenReturn(Optional.of(room));

        roomService.markActive("room-old", Instant.now());

        assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
    }

    @Test
    void markEndedIsIdempotentAndDoesNotReactivate() {
        Room room = room("room-abc", ORG_A, USER_A);
        room.markActive(Instant.parse("2026-08-28T10:05:00Z"));
        when(roomRepository.findByRoomId("room-abc")).thenReturn(Optional.of(room));

        Instant end = Instant.parse("2026-08-28T11:00:00Z");
        roomService.markEnded("room-abc", end);
        roomService.markEnded("room-abc", Instant.parse("2026-08-28T12:00:00Z"));
        roomService.markActive("room-abc", Instant.parse("2026-08-28T12:30:00Z"));

        assertThat(room.getStatus()).isEqualTo(RoomStatus.ENDED);
        assertThat(room.getEndedAt()).isEqualTo(end);
    }

    @Test
    void markEndedDoesNotTouchExpiredRoom() {
        Room room = expiredRoom("room-old");
        room.markExpired(Instant.parse("2020-01-01T00:02:00Z"));
        when(roomRepository.findByRoomId("room-old")).thenReturn(Optional.of(room));

        roomService.markEnded("room-old", Instant.now());

        assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(room.getEndedAt()).isNull();
    }

    @Test
    void markActiveIgnoresUnknownRoom() {
        when(roomRepository.findByRoomId("missing")).thenReturn(Optional.empty());

        roomService.markActive("missing", Instant.now());

        verify(roomRepository, never()).save(any());
    }
}
