package com.videoplatform.appointment;

import com.videoplatform.appointment.dto.AppointmentOccurrenceResponse;
import com.videoplatform.appointment.dto.CreateAppointmentRequest;
import com.videoplatform.appointment.dto.UpdateAppointmentRequest;
import com.videoplatform.common.ApiException;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.OrgPolicy;
import com.videoplatform.organization.OrganizationContext;
import com.videoplatform.room.RoomService;
import com.videoplatform.roomprofile.RoomProfile;
import com.videoplatform.roomprofile.RoomProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Regras de gestao dos Appointments (§22-§27). Isolamento por Organization
 * (padrao Sprint 7: recurso de outro tenant = 404, nunca vaza). Autorizacao por
 * role centralizada em {@link OrgPolicy}. O tenant vem do {@link OrganizationContext},
 * nunca do request (§6).
 */
@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHANUM = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int PUBLIC_ID_LEN = 12;

    private final AppointmentRepository repository;
    private final AppointmentOccurrenceRepository occurrenceRepository;
    private final RoomProfileService roomProfileService;
    private final RoomService roomService;
    private final RecurrenceService recurrenceService;
    private final OrgPolicy policy;
    private final Clock clock;

    public AppointmentService(AppointmentRepository repository,
                              AppointmentOccurrenceRepository occurrenceRepository,
                              RoomProfileService roomProfileService,
                              RoomService roomService,
                              RecurrenceService recurrenceService,
                              OrgPolicy policy,
                              Clock clock) {
        this.repository = repository;
        this.occurrenceRepository = occurrenceRepository;
        this.roomProfileService = roomProfileService;
        this.roomService = roomService;
        this.recurrenceService = recurrenceService;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional
    public Appointment create(OrganizationContext ctx, CreateAppointmentRequest req) {
        ZoneId zone = parseZone(req.timezone());
        RoomProfile profile = roomProfileService.getInOrg(req.roomProfileId(), ctx);
        validateRecurrenceUntil(req.recurrenceType(), req.recurrenceUntil(), req.startsAt(), zone);

        Appointment appointment = Appointment.create(
                ctx.organizationId(), profile.getId(), ctx.userId(),
                safe(req.title()), generatePublicAccessId(), blankToNull(req.participantName()),
                profile.getDurationMinutes(), zone, req.startsAt(),
                req.recurrenceType(), req.recurrenceUntil());
        repository.save(appointment);

        log.atInfo()
                .addKeyValue("event", LogEvents.APPOINTMENT_CREATED)
                .addKeyValue("appointmentId", appointment.getId())
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .addKeyValue("roomProfileId", profile.getId())
                .addKeyValue("recurrenceType", appointment.getRecurrenceType().name())
                .setMessage("appointment created")
                .log();
        return appointment;
    }

    /** Appointments da Organization, ordenados pela proxima ocorrencia (§24). */
    @Transactional(readOnly = true)
    public List<Appointment> list(OrganizationContext ctx) {
        List<Appointment> all = repository.findByOrganizationIdOrderByStartsAtAsc(ctx.organizationId());
        Instant now = clock.instant();
        return all.stream()
                .sorted(Comparator
                        .comparing((Appointment a) -> a.getStatus() == AppointmentStatus.CANCELLED)
                        .thenComparing(a -> upcomingStart(a, now).orElse(Instant.MAX)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Appointment getInOrg(UUID id, OrganizationContext ctx) {
        Appointment appointment = repository.findById(id).orElseThrow(() -> notFound(id, ctx));
        if (!appointment.belongsToOrg(ctx.organizationId())) {
            log.atWarn()
                    .addKeyValue("event", LogEvents.APPOINTMENT_ACCESS_DENIED)
                    .addKeyValue("appointmentId", id)
                    .addKeyValue("organizationId", ctx.organizationId())
                    .addKeyValue("resourceOrganizationId", appointment.getOrganizationId())
                    .addKeyValue("userId", ctx.userId())
                    .setMessage("cross-organization appointment access blocked")
                    .log();
            throw notFound(id, ctx);
        }
        return appointment;
    }

    @Transactional
    public Appointment update(UUID id, OrganizationContext ctx, UpdateAppointmentRequest req) {
        Appointment appointment = getInOrg(id, ctx);
        policy.requireCanManageAppointment(ctx, appointment.getOwnerId());
        ZoneId zone = parseZone(req.timezone());
        validateRecurrenceUntil(req.recurrenceType(), req.recurrenceUntil(), req.startsAt(), zone);

        appointment.applySchedule(safe(req.title()), blankToNull(req.participantName()),
                appointment.getDurationMinutes(), zone, req.startsAt(),
                req.recurrenceType(), req.recurrenceUntil());

        log.atInfo()
                .addKeyValue("event", LogEvents.APPOINTMENT_UPDATED)
                .addKeyValue("appointmentId", id)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("appointment updated")
                .log();
        return appointment;
    }

    @Transactional
    public void cancel(UUID id, OrganizationContext ctx) {
        Appointment appointment = getInOrg(id, ctx);
        policy.requireCanManageAppointment(ctx, appointment.getOwnerId());
        appointment.cancel();
        Instant now = clock.instant();
        for (AppointmentOccurrence occ : occurrenceRepository.findByAppointmentIdOrderByScheduledStartDesc(id)) {
            if (occ.getStatus() == OccurrenceStatus.SCHEDULED && occ.getScheduledStart().isAfter(now)) {
                occ.cancel();
            }
        }
        log.atInfo()
                .addKeyValue("event", LogEvents.APPOINTMENT_CANCELLED)
                .addKeyValue("appointmentId", id)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("appointment cancelled")
                .log();
    }

    @Transactional(readOnly = true)
    public List<AppointmentOccurrenceResponse> occurrences(UUID id, OrganizationContext ctx) {
        getInOrg(id, ctx);
        List<AppointmentOccurrence> occ = occurrenceRepository.findByAppointmentIdOrderByScheduledStartDesc(id);
        var roomCodes = roomService.roomCodesByIds(
                occ.stream().map(AppointmentOccurrence::getRoomId).filter(java.util.Objects::nonNull).toList());
        return occ.stream()
                .map(o -> new AppointmentOccurrenceResponse(o.getId(), o.getScheduledStart(),
                        o.getScheduledEnd(), o.getStatus().name(), roomCodes.get(o.getRoomId())))
                .toList();
    }

    /** Proxima ocorrencia relevante para exibicao: a que esta acontecendo agora, senao a proxima futura. */
    public Optional<Instant> upcomingStart(Appointment appointment) {
        return upcomingStart(appointment, clock.instant());
    }

    private Optional<Instant> upcomingStart(Appointment appointment, Instant now) {
        if (appointment.isCancelled()) {
            return Optional.empty();
        }
        return recurrenceService
                .activeStartFor(appointment, now, java.time.Duration.ZERO)
                .or(() -> recurrenceService.nextStartOnOrAfter(appointment, now));
    }

    // ---- helpers ----

    private String generatePublicAccessId() {
        for (int attempt = 0; attempt < 5; attempt++) {
            StringBuilder sb = new StringBuilder(PUBLIC_ID_LEN);
            for (int i = 0; i < PUBLIC_ID_LEN; i++) {
                sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
            }
            String candidate = sb.toString();
            if (!repository.existsByPublicAccessId(candidate)) {
                return candidate;
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Nao foi possivel gerar o identificador do link.");
    }

    private static ZoneId parseZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIMEZONE",
                    "Timezone invalida.");
        }
    }

    private static void validateRecurrenceUntil(RecurrenceType type, LocalDate until,
                                                Instant startsAt, ZoneId zone) {
        if (type == RecurrenceType.WEEKLY && until != null
                && until.isBefore(startsAt.atZone(zone).toLocalDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECURRENCE",
                    "A data final da recorrencia e' anterior ao inicio.");
        }
    }

    private ApiException notFound(UUID id, OrganizationContext ctx) {
        log.atWarn()
                .addKeyValue("event", LogEvents.APPOINTMENT_NOT_FOUND)
                .addKeyValue("appointmentId", id)
                .addKeyValue("organizationId", ctx.organizationId())
                .setMessage("appointment not found")
                .log();
        return new ApiException(HttpStatus.NOT_FOUND, "APPOINTMENT_NOT_FOUND",
                "Atendimento nao encontrado.");
    }

    private static String safe(String s) {
        return s == null ? null : s.trim();
    }

    private static String blankToNull(String s) {
        String t = safe(s);
        return t == null || t.isEmpty() ? null : t;
    }
}
