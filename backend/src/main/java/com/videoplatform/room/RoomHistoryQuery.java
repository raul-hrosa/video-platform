package com.videoplatform.room;

import java.time.Instant;
import java.util.UUID;

/**
 * Filtros opcionais do histórico de salas (Sprint 6 §7). Qualquer campo
 * {@code null} desliga o filtro correspondente.
 *
 * @param status        só salas neste estado
 * @param roomProfileId só salas geradas por este Profile
 * @param createdFrom   {@code createdAt >= createdFrom} (inclusivo)
 * @param createdTo     {@code createdAt < createdTo} (exclusivo)
 */
public record RoomHistoryQuery(
        RoomStatus status,
        UUID roomProfileId,
        Instant createdFrom,
        Instant createdTo
) {
    public static RoomHistoryQuery of(RoomStatus status, UUID roomProfileId,
                                      Instant createdFrom, Instant createdTo) {
        return new RoomHistoryQuery(status, roomProfileId, createdFrom, createdTo);
    }
}
