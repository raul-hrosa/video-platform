package com.videoplatform.room;

import com.videoplatform.auth.UserDirectory;
import com.videoplatform.common.ApiException;
import com.videoplatform.common.GlobalExceptionHandler;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.participant.ParticipantSessionService;
import com.videoplatform.participant.ParticipantSummaryService;
import com.videoplatform.provider.MediaTokenProvider;
import com.videoplatform.provider.ParticipantIdentityProvider;
import com.videoplatform.quality.RoomQualityService;
import com.videoplatform.roomprofile.RoomProfile;
import com.videoplatform.roomprofile.RoomProfileService;
import com.videoplatform.roomprofile.RoomType;
import com.videoplatform.support.TestAuth;
import com.videoplatform.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@Import({GlobalExceptionHandler.class, WebSecurityTestConfig.class})
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomService roomService;
    @MockBean
    private MediaTokenProvider tokenProvider;
    @MockBean
    private com.videoplatform.provider.MediaConnectionInfoProvider connectionInfoProvider;
    @MockBean
    private com.videoplatform.participant.ParticipantNameRegistry participantNameRegistry;
    @MockBean
    private ParticipantIdentityProvider participantIdentityProvider;
    @MockBean
    private ParticipantSessionService participantSessionService;
    @MockBean
    private RoomProfileService roomProfileService;
    @MockBean
    private ParticipantSummaryService participantSummaryService;
    @MockBean
    private RoomQualityService roomQualityService;
    @MockBean
    private com.videoplatform.participant.RoomParticipantsService roomParticipantsService;
    @MockBean
    private RoomEventsService roomEventsService;
    @MockBean
    private UserDirectory userDirectory;

    private static final UUID PROFILE_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private Room sampleRoom() {
        return Room.createFromProfile("room-abc123", PROFILE_ID, TestAuth.ORG_ID, "Aula de Ingles", 20,
                TestAuth.USER_ID, Instant.parse("2026-08-28T14:00:00Z"));
    }

    private RoomProfile sampleProfile() {
        return RoomProfile.create(TestAuth.ORG_ID, TestAuth.USER_ID, "Aula de Ingles", 20, RoomType.LESSON);
    }

    @Test
    void createDirectRoomReturns201WithDisplayStatusAndCreationMode() throws Exception {
        Room direct = Room.createDirect("room-direct1", TestAuth.ORG_ID, TestAuth.USER_ID,
                Instant.parse("2026-08-31T14:00:00Z"));
        when(roomService.createDirect(any(OrganizationContext.class))).thenReturn(direct);
        when(userDirectory.namesByIds(any())).thenReturn(Map.of(TestAuth.USER_ID, "Joao"));

        mockMvc.perform(post("/api/v1/rooms").with(TestAuth.user()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value("room-direct1"))
                .andExpect(jsonPath("$.displayStatus").value("IDLE"))
                .andExpect(jsonPath("$.creationMode").value("DIRECT"))
                .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.ownerName").value("Joao"));
    }

    @Test
    void createDirectRoomRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/rooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void participantsEndpointReturnsAggregatedIdentities() throws Exception {
        when(roomService.getInOrg(eq("room-abc123"), any(OrganizationContext.class))).thenReturn(sampleRoom());
        when(roomParticipantsService.listByRoom("room-abc123")).thenReturn(List.of(
                new com.videoplatform.participant.dto.RoomParticipantResponse(
                        "user:" + TestAuth.USER_ID, "USER", "Joao", 3, 1800L, 2, true,
                        Instant.parse("2026-08-28T14:00:00Z"), Instant.parse("2026-08-28T15:00:00Z"),
                        "GOOD")));

        mockMvc.perform(get("/api/v1/rooms/room-abc123/participants").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].participantRef").value("user:" + TestAuth.USER_ID))
                .andExpect(jsonPath("$[0].totalSessions").value(3))
                .andExpect(jsonPath("$[0].currentSessionOpen").value(true))
                .andExpect(jsonPath("$[0].latestQualityLevel").value("GOOD"));
    }

    @Test
    void eventsEndpointReturnsTimeline() throws Exception {
        when(roomService.getInOrg(eq("room-abc123"), any(OrganizationContext.class))).thenReturn(sampleRoom());
        when(roomEventsService.forRoom(any(Room.class))).thenReturn(List.of(
                com.videoplatform.room.dto.RoomEventResponse.room("ROOM_CREATED",
                        Instant.parse("2026-08-28T14:00:00Z")),
                com.videoplatform.room.dto.RoomEventResponse.participant("PARTICIPANT_SESSION_STARTED",
                        Instant.parse("2026-08-28T14:01:00Z"), "guest:x", null)));

        mockMvc.perform(get("/api/v1/rooms/room-abc123/events").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("ROOM_CREATED"))
                .andExpect(jsonPath("$[1].participantRef").value("guest:x"));
    }

    @Test
    void participantDetailReturnsAnalytics() throws Exception {
        when(roomService.getInOrg(eq("room-abc123"), any(OrganizationContext.class))).thenReturn(sampleRoom());
        when(roomParticipantsService.detail("room-abc123", "guest:x")).thenReturn(
                new com.videoplatform.participant.dto.ParticipantAnalyticsResponse(
                        "guest:x", "GUEST", "Maria", null,
                        new com.videoplatform.participant.dto.ParticipantAnalyticsResponse.History(2, 900L, 1),
                        null, List.of()));

        mockMvc.perform(get("/api/v1/rooms/room-abc123/participants/guest:x").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Maria"))
                .andExpect(jsonPath("$.history.totalSessions").value(2))
                .andExpect(jsonPath("$.quality").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/rooms"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void listRoomsReturnsOrganizationRoomsWithProfileNameAndOwnerName() throws Exception {
        when(roomService.listHistory(any(OrganizationContext.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sampleRoom())));
        when(roomProfileService.mapByIds(any())).thenReturn(Map.of(PROFILE_ID, sampleProfile()));
        when(userDirectory.namesByIds(any())).thenReturn(Map.of(TestAuth.USER_ID, "Joao"));

        mockMvc.perform(get("/api/v1/rooms?page=0&size=20").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roomId").value("room-abc123"))
                .andExpect(jsonPath("$.content[0].roomProfileName").value("Aula de Ingles"))
                .andExpect(jsonPath("$.content[0].ownerName").value("Joao"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listRoomsPassesFiltersToService() throws Exception {
        when(roomService.listHistory(any(OrganizationContext.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(roomProfileService.mapByIds(any())).thenReturn(Map.of());
        when(userDirectory.namesByIds(any())).thenReturn(Map.of());

        mockMvc.perform(get("/api/v1/rooms?status=ENDED&roomProfileId=" + PROFILE_ID
                        + "&createdFrom=2026-08-01T00:00:00Z&createdTo=2026-08-31T00:00:00Z")
                        .with(TestAuth.user()))
                .andExpect(status().isOk());

        ArgumentCaptor<RoomHistoryQuery> query = ArgumentCaptor.forClass(RoomHistoryQuery.class);
        verify(roomService).listHistory(any(OrganizationContext.class), query.capture(), any());
        assertThat(query.getValue().status()).isEqualTo(RoomStatus.ENDED);
        assertThat(query.getValue().roomProfileId()).isEqualTo(PROFILE_ID);
        assertThat(query.getValue().createdFrom()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    }

    @Test
    void dashboardSummaryReturnsCountsFromService() throws Exception {
        when(roomService.dashboard(any(OrganizationContext.class), any(), any()))
                .thenReturn(new com.videoplatform.room.dto.RoomDashboardResponse(3, 12, 40, 16320, 21));

        mockMvc.perform(get("/api/v1/rooms/summary"
                        + "?todayStart=2026-08-29T03:00:00Z&weekStart=2026-08-24T03:00:00Z")
                        .with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomsToday").value(3))
                .andExpect(jsonPath("$.distinctParticipants").value(21));
    }

    @Test
    void dashboardSummaryWithoutBoundsReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/rooms/summary").with(TestAuth.user()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRoomOfAnotherTenantReturns404() throws Exception {
        when(roomService.getInOrg(eq("room-abc123"), any(OrganizationContext.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "no"));

        mockMvc.perform(get("/api/v1/rooms/room-abc123").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void qualityRequiresTenantMatch() throws Exception {
        when(roomService.getInOrg(eq("room-abc123"), any(OrganizationContext.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "no"));

        mockMvc.perform(get("/api/v1/rooms/room-abc123/quality").with(TestAuth.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void tokenEndpointRequiresExistingRoom() throws Exception {
        when(roomService.getJoinableInOrg(eq("ghost"), any(OrganizationContext.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room not found."));

        mockMvc.perform(post("/api/v1/rooms/ghost/token").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void tokenEndpointRejectsExpiredRoomWith409() throws Exception {
        when(roomService.getJoinableInOrg(eq("room-abc123"), any(OrganizationContext.class)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "ROOM_EXPIRED", "expirou"));

        mockMvc.perform(post("/api/v1/rooms/room-abc123/token").with(TestAuth.user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }

    @Test
    void tokenEndpointReturnsTokenForOrganizationMember() throws Exception {
        when(roomService.getJoinableInOrg(eq("room-abc123"), any(OrganizationContext.class)))
                .thenReturn(sampleRoom());
        when(participantIdentityProvider.forUser(TestAuth.USER_ID)).thenReturn("user:" + TestAuth.USER_ID);
        when(tokenProvider.createRoomToken(eq("room-abc123"), eq("user:" + TestAuth.USER_ID), eq("Joao")))
                .thenReturn(new MediaTokenProvider.MediaToken("fake.jwt.token", "user:" + TestAuth.USER_ID));

        mockMvc.perform(post("/api/v1/rooms/room-abc123/token").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake.jwt.token"))
                .andExpect(jsonPath("$.participantId").value("user:" + TestAuth.USER_ID));
    }

    // ---- guest token (rota publica, sem conta) ----

    @Test
    void guestTokenReturns200WithoutAuthentication() throws Exception {
        when(roomService.getJoinableByRoomId("room-abc123")).thenReturn(sampleRoom());
        when(participantIdentityProvider.forGuest()).thenReturn("guest:generated");
        when(tokenProvider.createRoomToken(eq("room-abc123"), eq("guest:generated"), eq("Maria")))
                .thenReturn(new MediaTokenProvider.MediaToken("guest.jwt", "guest:xyz"));

        mockMvc.perform(post("/api/v1/rooms/room-abc123/guest-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Maria\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("guest.jwt"))
                .andExpect(jsonPath("$.participantId").value("guest:xyz"));
    }

    @Test
    void guestTokenWithoutNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/room-abc123/guest-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void guestTokenForExpiredRoomReturns409() throws Exception {
        when(roomService.getJoinableByRoomId("room-abc123"))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "ROOM_EXPIRED", "expirou"));

        mockMvc.perform(post("/api/v1/rooms/room-abc123/guest-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Maria\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_EXPIRED"));
    }
}
