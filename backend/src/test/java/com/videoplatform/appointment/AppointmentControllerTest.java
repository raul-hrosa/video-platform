package com.videoplatform.appointment;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppointmentController.class)
@Import({GlobalExceptionHandler.class, WebSecurityTestConfig.class})
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentService appointmentService;

    private static final UUID APPT_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID PROFILE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private Appointment sample() {
        return Appointment.create(TestAuth.ORG_ID, PROFILE_ID, TestAuth.USER_ID, "Aula de Ingles — Raul",
                "abc123xyz000", "Raul", 60, ZoneId.of("America/Sao_Paulo"),
                Instant.parse("2026-09-01T18:00:00Z"), RecurrenceType.WEEKLY, LocalDate.of(2026, 12, 1));
    }

    @Test
    void unauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void createReturns201WithPublicIdAndJoinUrl() throws Exception {
        when(appointmentService.create(any(), any())).thenReturn(sample());
        when(appointmentService.upcomingStart(any()))
                .thenReturn(Optional.of(Instant.parse("2026-09-01T18:00:00Z")));

        mockMvc.perform(post("/api/v1/appointments").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roomProfileId":"%s","title":"Aula de Ingles — Raul","participantName":"Raul",
                                 "startsAt":"2026-09-01T18:00:00Z","timezone":"America/Sao_Paulo",
                                 "recurrence":{"type":"WEEKLY","dayOfWeek":"TUESDAY","until":"2026-12-01"}}
                                """.formatted(PROFILE_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicAccessId").value("abc123xyz000"))
                .andExpect(jsonPath("$.joinUrl").value("/r/abc123xyz000"))
                .andExpect(jsonPath("$.durationMinutes").value(60))
                .andExpect(jsonPath("$.nextOccurrence").value("2026-09-01T18:00:00Z"));
    }

    @Test
    void createWithoutTitleReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/appointments").with(TestAuth.user())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomProfileId\":\"%s\",\"startsAt\":\"2026-09-01T18:00:00Z\",\"timezone\":\"America/Sao_Paulo\"}"
                                .formatted(PROFILE_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOfAnotherTenantReturns404() throws Exception {
        when(appointmentService.getInOrg(eq(APPT_ID), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND", "no"));

        mockMvc.perform(get("/api/v1/appointments/" + APPT_ID).with(TestAuth.user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_FOUND"));
    }

    @Test
    void cancelReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/appointments/" + APPT_ID + "/cancel").with(TestAuth.user()))
                .andExpect(status().isNoContent());
    }
}
