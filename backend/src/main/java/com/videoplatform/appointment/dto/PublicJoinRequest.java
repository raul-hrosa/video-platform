package com.videoplatform.appointment.dto;

import jakarta.validation.constraints.Size;

/**
 * Entrada pelo link publico (§28.5). O nome e' opcional: se o Appointment ja tem
 * {@code participantName}, ele e' usado; senao o visitante informa o proprio. O
 * nome nunca autentica nem autoriza (§9).
 */
public record PublicJoinRequest(
        @Size(max = 120) String name
) {
}
