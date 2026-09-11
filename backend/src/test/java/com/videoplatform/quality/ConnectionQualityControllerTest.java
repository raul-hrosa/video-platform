package com.videoplatform.quality;

import com.videoplatform.auth.security.AuthenticatedUser;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.common.ApiException;
import com.videoplatform.common.GlobalExceptionHandler;
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

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConnectionQualityController.class)
@Import({GlobalExceptionHandler.class, WebSecurityTestConfig.class})
class ConnectionQualityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConnectionMetricsService metricsService;

    private static final UUID SID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ConnectionQualityMetric metric() {
        return ConnectionQualityMetric.record("room-1", SID, "user:" + TestAuth.USER_ID, Instant.now(),
                QualityLevel.GOOD, 90, 1.2, 15, null, null, null, null, null, "connected");
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/room-1/sessions/{sid}/quality", SID)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidSnapshot() throws Exception {
        when(metricsService.record(eq("room-1"), eq(SID), any(OrganizationContext.class),
                any(AuthenticatedUser.class), any()))
                .thenReturn(metric());

        mockMvc.perform(post("/api/v1/rooms/room-1/sessions/{sid}/quality", SID).with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"rttMs":90,"packetLossPercent":1.2,"jitterMs":15,
                             "videoWidth":1280,"videoHeight":720,"videoFps":30}"""))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.qualityLevel").value("GOOD"));
    }

    @Test
    void rejectsPacketLossAbove100() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/room-1/sessions/{sid}/quality", SID).with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"packetLossPercent\":120}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsNegativeRtt() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/room-1/sessions/{sid}/quality", SID).with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rttMs\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void forbiddenWhenSessionIsNotYours() throws Exception {
        when(metricsService.record(any(), any(), any(OrganizationContext.class),
                any(AuthenticatedUser.class), any()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Esta sessao nao pertence a voce."));

        mockMvc.perform(post("/api/v1/rooms/room-1/sessions/{sid}/quality", SID).with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rttMs\":90}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void propagatesSessionNotFound() throws Exception {
        when(metricsService.record(any(), any(), any(OrganizationContext.class),
                any(AuthenticatedUser.class), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                        "Participant session not found."));

        mockMvc.perform(post("/api/v1/rooms/room-1/sessions/{sid}/quality", SID).with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rttMs\":90}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }
}
