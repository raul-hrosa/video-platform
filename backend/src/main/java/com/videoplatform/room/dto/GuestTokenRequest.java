package com.videoplatform.room.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Entrada de visitante sem conta pelo link da sala (Sprint 5 §63.6). */
public record GuestTokenRequest(
        @NotBlank @Size(min = 1, max = 60) String name
) {
}
