package com.videoplatform.appointment;

import com.videoplatform.appointment.dto.CreateAppointmentRequest;
import com.videoplatform.appointment.dto.CreateAppointmentRequest.Recurrence;
import com.videoplatform.appointment.dto.UpdateAppointmentRequest;
import com.videoplatform.common.ApiException;
import com.videoplatform.organization.OrgPolicy;
import com.videoplatform.organization.OrgRole;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.roomprofile.RoomProfile;
import com.videoplatform.roomprofile.RoomProfileService;
import com.videoplatform.roomprofile.RoomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    private static final UUID ORG_A = UUID.fromString("a0000000-0000-0000-0000-0000000000aa");
    private static final UUID ORG_B = UUID.fromString("b0000000-0000-0000-0000-0000000000bb");
    private static final UUID CREATOR = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("22222222-0000-0000-0000-000000000002");
    private static final UUID PROFILE_ID = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID APPT_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Mock
    private AppointmentRepository repository;
    @Mock
    private AppointmentOccurrenceRepository occurrenceRepository;
    @Mock
    private RoomProfileService roomProfileService;
    @Mock
    private com.videoplatform.room.RoomService roomService;

    private AppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AppointmentService(repository, occurrenceRepository, roomProfileService,
                roomService, new RecurrenceService(), new OrgPolicy(),
                Clock.fixed(NOW, ZoneId.of("UTC")));
        lenient().when(repository.existsByPublicAccessId(any())).thenReturn(false);
        lenient().when(repository.save(any(Appointment.class))).thenAnswer(i -> i.getArgument(0));
    }

    private static OrganizationContext ctx(UUID org, UUID user, OrgRole role) {
        return new OrganizationContext(org, user, role);
    }

    private RoomProfile profile(UUID org) {
        return RoomProfile.create(org, CREATOR, "Aula de Ingles", 60, RoomType.LESSON);
    }

    private CreateAppointmentRequest createReq(String tz) {
        return new CreateAppointmentRequest(PROFILE_ID, "Aula de Ingles — Raul", "Raul",
                Instant.parse("2026-09-01T18:00:00Z"), tz,
                new Recurrence(RecurrenceType.WEEKLY, "TUESDAY", LocalDate.of(2026, 12, 1)));
    }

    private Appointment sampleAppt(UUID org, UUID owner) {
        return Appointment.create(org, PROFILE_ID, owner, "Aula", "pub123456789", "Raul",
                60, ZoneId.of("America/Sao_Paulo"), Instant.parse("2026-09-01T18:00:00Z"),
                RecurrenceType.WEEKLY, LocalDate.of(2026, 12, 1));
    }

    @Test
    void createSnapshotsDurationFromProfileAndUsesContextTenantAndOwner() {
        when(roomProfileService.getInOrg(PROFILE_ID, ctx(ORG_A, CREATOR, OrgRole.MEMBER)))
                .thenReturn(profile(ORG_A));

        Appointment created = service.create(ctx(ORG_A, CREATOR, OrgRole.MEMBER), createReq("America/Sao_Paulo"));

        assertThat(created.getOrganizationId()).isEqualTo(ORG_A);
        assertThat(created.getOwnerId()).isEqualTo(CREATOR);
        assertThat(created.getDurationMinutes()).isEqualTo(60);
        assertThat(created.getParticipantName()).isEqualTo("Raul");
        assertThat(created.getPublicAccessId()).hasSize(12);
        assertThat(created.getRecurrenceDayOfWeek()).isEqualTo(java.time.DayOfWeek.TUESDAY);
    }

    @Test
    void createRejectsInvalidTimezone() {
        assertThatThrownBy(() -> service.create(ctx(ORG_A, CREATOR, OrgRole.OWNER), createReq("Mars/Olympus")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo("INVALID_TIMEZONE"));
    }

    @Test
    void getInOrgHidesAppointmentOfAnotherTenant() {
        when(repository.findById(APPT_ID)).thenReturn(Optional.of(sampleAppt(ORG_A, CREATOR)));

        assertThatThrownBy(() -> service.getInOrg(APPT_ID, ctx(ORG_B, OTHER, OrgRole.OWNER)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException api = (ApiException) e;
                    assertThat(api.getErrorCode()).isEqualTo("APPOINTMENT_NOT_FOUND");
                    assertThat(api.getStatus().value()).isEqualTo(404);
                });
    }

    @Test
    void memberCannotEditAppointmentOfAnotherCreator() {
        when(repository.findById(APPT_ID)).thenReturn(Optional.of(sampleAppt(ORG_A, CREATOR)));
        UpdateAppointmentRequest req = new UpdateAppointmentRequest("Novo", null,
                Instant.parse("2026-09-01T19:00:00Z"), "America/Sao_Paulo", null);

        assertThatThrownBy(() -> service.update(APPT_ID, ctx(ORG_A, OTHER, OrgRole.MEMBER), req))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo("INSUFFICIENT_ROLE"));
    }

    @Test
    void adminCanEditAnyAppointmentAndScheduleChangeDoesNotTouchDuration() {
        when(repository.findById(APPT_ID)).thenReturn(Optional.of(sampleAppt(ORG_A, CREATOR)));
        UpdateAppointmentRequest req = new UpdateAppointmentRequest("  Aula 16h ", "Raul",
                Instant.parse("2026-09-01T19:00:00Z"), "America/Sao_Paulo",
                new Recurrence(RecurrenceType.WEEKLY, null, null));

        Appointment updated = service.update(APPT_ID, ctx(ORG_A, OTHER, OrgRole.ADMIN), req);

        assertThat(updated.getTitle()).isEqualTo("Aula 16h");
        assertThat(updated.getStartsAt()).isEqualTo(Instant.parse("2026-09-01T19:00:00Z"));
        assertThat(updated.getDurationMinutes()).isEqualTo(60);
        assertThat(updated.getRecurrenceUntil()).isNull();
    }

    @Test
    void cancelMarksCancelledAndCancelsFutureScheduledOccurrences() {
        Appointment appt = sampleAppt(ORG_A, CREATOR);
        when(repository.findById(APPT_ID)).thenReturn(Optional.of(appt));
        AppointmentOccurrence past = AppointmentOccurrence.schedule(APPT_ID,
                NOW.minusSeconds(3600), NOW.minusSeconds(60), NOW.minusSeconds(4000));
        AppointmentOccurrence future = AppointmentOccurrence.schedule(APPT_ID,
                NOW.plusSeconds(86400), NOW.plusSeconds(90000), NOW);
        when(occurrenceRepository.findByAppointmentIdOrderByScheduledStartDesc(APPT_ID))
                .thenReturn(List.of(future, past));

        service.cancel(APPT_ID, ctx(ORG_A, CREATOR, OrgRole.OWNER));

        assertThat(appt.isCancelled()).isTrue();
        assertThat(future.getStatus()).isEqualTo(OccurrenceStatus.CANCELLED);
        assertThat(past.getStatus()).isEqualTo(OccurrenceStatus.SCHEDULED);
    }
}
