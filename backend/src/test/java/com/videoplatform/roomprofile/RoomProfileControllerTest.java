package com.videoplatform.roomprofile;

import com.videoplatform.common.ApiException;
import com.videoplatform.common.GlobalExceptionHandler;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.room.Room;
import com.videoplatform.room.RoomService;
import com.videoplatform.support.TestAuth;
import com.videoplatform.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomProfileController.class)
@Import({GlobalExceptionHandler.class, WebSecurityTestConfig.class})
class RoomProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RoomProfileService roomProfileService;
    @MockBean
    private RoomService roomService;

    private static final UUID PROFILE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private static RoomProfile sampleProfile() {
        return RoomProfile.create(TestAuth.ORG_ID, TestAuth.USER_ID, "Aula de Ingles", 20, RoomType.LESSON);
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/room-profiles"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void profileSummaryChecksTenantAndReturnsAggregates() throws Exception {
        when(roomProfileService.getInOrg(eq(PROFILE_ID), any(OrganizationContext.class)))
                .thenReturn(sampleProfile());
        when(roomService.profileSummary(any()))
                .thenReturn(new com.videoplatform.roomprofile.dto.RoomProfileSummaryResponse(
                        PROFILE_ID, 37, 44640, 42));

        mockMvc.perform(get("/api/v1/room-profiles/" + PROFILE_ID + "/summary").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomsTotal").value(37))
                .andExpect(jsonPath("$.participations").value(42));
    }

    @Test
    void profileSummaryOfAnotherTenantReturns404() throws Exception {
        when(roomProfileService.getInOrg(eq(PROFILE_ID), any(OrganizationContext.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "ROOM_PROFILE_NOT_FOUND", "no"));

        mockMvc.perform(get("/api/v1/room-profiles/" + PROFILE_ID + "/summary").with(TestAuth.user()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReturns201() throws Exception {
        when(roomProfileService.create(any(OrganizationContext.class), eq("Aula de Ingles"), eq(20),
                eq(RoomType.LESSON)))
                .thenReturn(sampleProfile());

        mockMvc.perform(post("/api/v1/room-profiles").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aula de Ingles\",\"durationMinutes\":20,\"type\":\"LESSON\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Aula de Ingles"))
                .andExpect(jsonPath("$.durationMinutes").value(20))
                .andExpect(jsonPath("$.type").value("LESSON"))
                .andExpect(jsonPath("$.ownerId").doesNotExist());
    }

    @Test
    void createWithZeroDurationReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/room-profiles").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"durationMinutes\":0,\"type\":\"LESSON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void createWithTooLongDurationReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/room-profiles").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"durationMinutes\":481,\"type\":\"LESSON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/room-profiles").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"durationMinutes\":20,\"type\":\"LESSON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void createWithUnknownTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/room-profiles").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"durationMinutes\":20,\"type\":\"PARTY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void listReturnsContentEnvelopeWithOrganizationProfiles() throws Exception {
        when(roomProfileService.list(any(OrganizationContext.class)))
                .thenReturn(List.of(sampleProfile()));

        mockMvc.perform(get("/api/v1/room-profiles").with(TestAuth.user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Aula de Ingles"))
                .andExpect(jsonPath("$.content[0].durationMinutes").value(20));
    }

    @Test
    void getMissingProfileReturns404() throws Exception {
        when(roomProfileService.getInOrg(eq(PROFILE_ID), any(OrganizationContext.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "ROOM_PROFILE_NOT_FOUND", "no"));

        mockMvc.perform(get("/api/v1/room-profiles/" + PROFILE_ID).with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_PROFILE_NOT_FOUND"));
    }

    @Test
    void updateReturns200() throws Exception {
        when(roomProfileService.update(eq(PROFILE_ID), any(OrganizationContext.class), eq("Aula Intensiva"),
                eq(30), eq(RoomType.MEETING)))
                .thenReturn(RoomProfile.create(TestAuth.ORG_ID, TestAuth.USER_ID, "Aula Intensiva", 30,
                        RoomType.MEETING));

        mockMvc.perform(put("/api/v1/room-profiles/" + PROFILE_ID).with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Aula Intensiva\",\"durationMinutes\":30,\"type\":\"MEETING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aula Intensiva"));
    }

    @Test
    void updateForbiddenByRoleReturns403() throws Exception {
        when(roomProfileService.update(eq(PROFILE_ID), any(OrganizationContext.class), any(), any(Integer.class),
                any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "INSUFFICIENT_ROLE", "no"));

        mockMvc.perform(put("/api/v1/room-profiles/" + PROFILE_ID).with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"durationMinutes\":30,\"type\":\"MEETING\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_ROLE"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/room-profiles/" + PROFILE_ID).with(TestAuth.user()))
                .andExpect(status().isNoContent());

        verify(roomProfileService).softDelete(eq(PROFILE_ID), any(OrganizationContext.class));
    }

    @Test
    void createRoomFromProfileReturns201WithWaitingRoom() throws Exception {
        when(roomProfileService.getInOrg(eq(PROFILE_ID), any(OrganizationContext.class)))
                .thenReturn(sampleProfile());
        Room room = Room.createFromProfile("room-abc123", PROFILE_ID, TestAuth.ORG_ID, "Aula de Ingles", 20,
                TestAuth.USER_ID, java.time.Instant.parse("2026-08-28T14:00:00Z"));
        when(roomService.createFromProfile(any(RoomProfile.class), any(OrganizationContext.class)))
                .thenReturn(room);

        mockMvc.perform(post("/api/v1/room-profiles/" + PROFILE_ID + "/rooms").with(TestAuth.user()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value("room-abc123"))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.expiresAt").exists());
    }

    @Test
    void createRoomFromMissingOrCrossTenantProfileReturns404() throws Exception {
        when(roomProfileService.getInOrg(eq(PROFILE_ID), any(OrganizationContext.class)))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "ROOM_PROFILE_NOT_FOUND", "no"));

        mockMvc.perform(post("/api/v1/room-profiles/" + PROFILE_ID + "/rooms").with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_PROFILE_NOT_FOUND"));
    }
}
