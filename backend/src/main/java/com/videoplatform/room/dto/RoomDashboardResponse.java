package com.videoplatform.room.dto;

/**
 * Resumo do dashboard inicial (Sprint 6 §38) — um panorama pequeno, não um
 * dashboard corporativo. Todos os números vêm da API; o frontend não agrega.
 *
 * <p>{@code roomsToday}/{@code roomsThisWeek} contam salas criadas a partir dos
 * instantes que o frontend informa (meia-noite local convertida para UTC).
 * {@code totalCallSeconds} e {@code distinctParticipants} são histórico completo
 * do dono.
 */
public record RoomDashboardResponse(
        long roomsToday,
        long roomsThisWeek,
        long totalRooms,
        long totalCallSeconds,
        long distinctParticipants
) {
}
