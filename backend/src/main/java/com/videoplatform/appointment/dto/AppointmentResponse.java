package com.videoplatform.appointment.dto;

import com.videoplatform.appointment.Appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Um Appointment na API de gestao (§23, §25). */
public record AppointmentResponse(
        UUID id,
        String title,
        String participantName,
        String publicAccessId,
        String joinUrl,
        UUID roomProfileId,
        int durationMinutes,
        String timezone,
        Instant startsAt,
        String recurrenceType,
        String recurrenceDayOfWeek,
        LocalDate recurrenceUntil,
        String status,
        Instant nextOccurrence
) {

    public static AppointmentResponse from(Appointment a, Instant nextOccurrence) {
        return new AppointmentResponse(
                a.getId(),
                a.getTitle(),
                a.getParticipantName(),
                a.getPublicAccessId(),
                "/r/" + a.getPublicAccessId(),
                a.getRoomProfileId(),
                a.getDurationMinutes(),
                a.getTimezone(),
                a.getStartsAt(),
                a.getRecurrenceType().name(),
                a.getRecurrenceDayOfWeek() == null ? null : a.getRecurrenceDayOfWeek().name(),
                a.getRecurrenceUntil(),
                a.getStatus().name(),
                nextOccurrence);
    }
}
