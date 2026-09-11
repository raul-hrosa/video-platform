package com.videoplatform.appointment.dto;

import com.videoplatform.appointment.dto.CreateAppointmentRequest.Recurrence;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.videoplatform.appointment.RecurrenceType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Atualizacao de Appointment (§26). Room Profile e' imutavel nesta sprint.
 * Alteracoes so afetam ocorrencias futuras — as ja realizadas nao mudam.
 */
public record UpdateAppointmentRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 120) String participantName,
        @NotNull Instant startsAt,
        @NotBlank @Size(max = 64) String timezone,
        Recurrence recurrence
) {

    public RecurrenceType recurrenceType() {
        return recurrence == null ? RecurrenceType.NONE : recurrence.type();
    }

    public LocalDate recurrenceUntil() {
        return recurrence == null ? null : recurrence.until();
    }
}
