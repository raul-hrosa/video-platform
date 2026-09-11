package com.videoplatform.appointment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma ocorrencia no historico do Appointment (§33). Cada ocorrencia aponta para
 * a sua Room — a partir dela o frontend reaproveita a tela de detalhe da sala
 * (sessions/quality/analytics separados por ocorrencia).
 */
public record AppointmentOccurrenceResponse(
        UUID id,
        Instant scheduledStart,
        Instant scheduledEnd,
        String status,
        String roomId
) {
}
