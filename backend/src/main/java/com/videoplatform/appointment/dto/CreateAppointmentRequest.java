package com.videoplatform.appointment.dto;

import com.videoplatform.appointment.RecurrenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Criacao de Appointment (§22). {@code organizationId} nunca vem daqui — e'
 * resolvido do contexto autenticado (§6). A duracao vem do Room Profile (§8).
 */
public record CreateAppointmentRequest(
        @NotNull UUID roomProfileId,
        @NotBlank @Size(max = 200) String title,
        @Size(max = 120) String participantName,
        @NotNull Instant startsAt,
        @NotBlank @Size(max = 64) String timezone,
        Recurrence recurrence
) {

    /** {@code dayOfWeek} e' aceito mas ignorado — deriva de {@code startsAt} na timezone (§45). */
    public record Recurrence(
            @NotNull RecurrenceType type,
            String dayOfWeek,
            LocalDate until
    ) {
    }

    public RecurrenceType recurrenceType() {
        return recurrence == null ? RecurrenceType.NONE : recurrence.type();
    }

    public LocalDate recurrenceUntil() {
        return recurrence == null ? null : recurrence.until();
    }
}
