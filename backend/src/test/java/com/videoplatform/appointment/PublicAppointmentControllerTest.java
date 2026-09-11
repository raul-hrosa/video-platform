package com.videoplatform.appointment;

import com.videoplatform.appointment.AppointmentAccessService.Resolution;
import com.videoplatform.appointment.AppointmentAccessService.State;
import com.videoplatform.common.GlobalExceptionHandler;
import com.videoplatform.provider.MediaTokenProvider;
import com.videoplatform.provider.ParticipantIdentityProvider;
import com.videoplatform.support.WebSecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicAppointmentController.class)
@Import({GlobalExceptionHandler.class, WebSecurityTestConfig.class})
class PublicAppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AppointmentAccessService accessService;
    @MockBean
    private MediaTokenProvider tokenProvider;
    @MockBean
    private com.videoplatform.provider.MediaConnectionInfoProvider connectionInfoProvider;
    @MockBean
    private com.videoplatform.participant.ParticipantNameRegistry participantNameRegistry;
    @MockBean
    private ParticipantIdentityProvider participantIdentityProvider;

    private static final String PUB = "abc123xyz000";

    private Appointment appt() {
        return Appointment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Aula de Ingles", PUB, "Raul", 60, ZoneId.of("America/Sao_Paulo"),
                Instant.parse("2026-09-01T18:00:00Z"), RecurrenceType.WEEKLY, LocalDate.of(2026, 12, 1));
    }

    @Test
    void resolveIsPublicAndReturnsOnlyMinimalFields() throws Exception {
        Resolution r = new Resolution(appt(), State.WAITING_ROOM,
                Instant.parse("2026-09-01T18:00:00Z"), Instant.parse("2026-09-01T19:00:00Z"),
                Instant.parse("2026-09-01T17:45:00Z"), "room-appt01", null);
        when(accessService.resolve(PUB)).thenReturn(r);

        mockMvc.perform(get("/api/v1/public/appointments/" + PUB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Aula de Ingles"))
                .andExpect(jsonPath("$.participantName").value("Raul"))
                .andExpect(jsonPath("$.state").value("WAITING_ROOM"))
                .andExpect(jsonPath("$.roomId").value("room-appt01"))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @Test
    void joinBeforeWindowReturns409() throws Exception {
        Resolution r = new Resolution(appt(), State.BEFORE_WINDOW,
                Instant.parse("2026-09-08T18:00:00Z"), Instant.parse("2026-09-08T19:00:00Z"),
                Instant.parse("2026-09-08T17:45:00Z"), null, Instant.parse("2026-09-08T18:00:00Z"));
        when(accessService.resolve(PUB)).thenReturn(r);

        mockMvc.perform(post("/api/v1/public/appointments/" + PUB + "/token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Raul\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPOINTMENT_NOT_YET_OPEN"));
    }

    @Test
    void joinWhenJoinableReturnsGuestToken() throws Exception {
        Resolution r = new Resolution(appt(), State.JOINABLE,
                Instant.parse("2026-09-01T18:00:00Z"), Instant.parse("2026-09-01T19:00:00Z"),
                Instant.parse("2026-09-01T17:45:00Z"), "room-appt01", null);
        when(accessService.resolve(PUB)).thenReturn(r);
        when(participantIdentityProvider.forGuest()).thenReturn("guest:generated");
        when(tokenProvider.createRoomToken(eq("room-appt01"), eq("guest:generated"), eq("Raul")))
                .thenReturn(new MediaTokenProvider.MediaToken("guest.jwt", "guest:xyz"));

        mockMvc.perform(post("/api/v1/public/appointments/" + PUB + "/token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("guest.jwt"))
                .andExpect(jsonPath("$.participantId").value("guest:xyz"));
    }
}
