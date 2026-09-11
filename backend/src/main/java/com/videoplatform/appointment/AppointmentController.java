package com.videoplatform.appointment;

import com.videoplatform.appointment.dto.AppointmentOccurrenceResponse;
import com.videoplatform.appointment.dto.AppointmentResponse;
import com.videoplatform.appointment.dto.CreateAppointmentRequest;
import com.videoplatform.appointment.dto.UpdateAppointmentRequest;
import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.organization.OrganizationContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Gestao de Appointments (§22-§27). Sempre no escopo da Organization autenticada. */
@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(OrganizationContext ctx,
                                      @Valid @RequestBody CreateAppointmentRequest request) {
        Appointment appointment = appointmentService.create(ctx, request);
        return AppointmentResponse.from(appointment,
                appointmentService.upcomingStart(appointment).orElse(null));
    }

    @GetMapping
    public Map<String, List<AppointmentResponse>> list(OrganizationContext ctx) {
        List<AppointmentResponse> content = appointmentService.list(ctx).stream()
                .map(a -> AppointmentResponse.from(a, appointmentService.upcomingStart(a).orElse(null)))
                .toList();
        return Map.of("content", content);
    }

    @GetMapping("/{id}")
    public AppointmentResponse get(OrganizationContext ctx, @PathVariable UUID id) {
        Appointment appointment = appointmentService.getInOrg(id, ctx);
        log.atInfo()
                .addKeyValue("event", LogEvents.APPOINTMENT_VIEWED)
                .addKeyValue("appointmentId", id)
                .addKeyValue("organizationId", ctx.organizationId())
                .addKeyValue("userId", ctx.userId())
                .setMessage("appointment viewed")
                .log();
        return AppointmentResponse.from(appointment,
                appointmentService.upcomingStart(appointment).orElse(null));
    }

    @GetMapping("/{id}/occurrences")
    public Map<String, List<AppointmentOccurrenceResponse>> occurrences(OrganizationContext ctx,
                                                                        @PathVariable UUID id) {
        return Map.of("content", appointmentService.occurrences(id, ctx));
    }

    @PutMapping("/{id}")
    public AppointmentResponse update(OrganizationContext ctx, @PathVariable UUID id,
                                      @Valid @RequestBody UpdateAppointmentRequest request) {
        Appointment appointment = appointmentService.update(id, ctx, request);
        return AppointmentResponse.from(appointment,
                appointmentService.upcomingStart(appointment).orElse(null));
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(OrganizationContext ctx, @PathVariable UUID id) {
        appointmentService.cancel(id, ctx);
    }
}
