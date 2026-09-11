package com.videoplatform.appointment.dto;

import com.videoplatform.appointment.AppointmentAccessService.Resolution;

import java.time.Instant;

/**
 * Payload minimo do link publico (§32): so o necessario para participar. Nunca
 * expoe organizationId, ownerId, ids internos, analytics, sessions ou quality.
 *
 * <p>{@code state}: {@code BEFORE_WINDOW} | {@code WAITING_ROOM} | {@code JOINABLE}
 * | {@code ENDED} | {@code CANCELLED} | {@code NO_UPCOMING}.
 * {@code roomId} so vem preenchido em {@code WAITING_ROOM}/{@code JOINABLE}.
 */
public record PublicAppointmentResponse(
        String title,
        String participantName,
        String state,
        Instant scheduledStart,
        Instant scheduledEnd,
        Instant joinWindowOpensAt,
        String roomId,
        Instant nextOccurrenceStart
) {

    public static PublicAppointmentResponse from(Resolution r) {
        return new PublicAppointmentResponse(
                r.appointment().getTitle(),
                r.appointment().getParticipantName(),
                r.state().name(),
                r.scheduledStart(),
                r.scheduledEnd(),
                r.joinWindowOpensAt(),
                r.roomId(),
                r.nextOccurrenceStart());
    }
}
