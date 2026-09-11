package com.videoplatform.room;

/**
 * Ciclo de vida da sala:
 *
 * <pre>
 *   CREATE -> WAITING -> ACTIVE -> ENDED     (todos sairam)
 *               |          |
 *               +----+-----+
 *                    v
 *                 EXPIRED   (expires_at atingido)
 * </pre>
 *
 * {@code ENDED} e {@code EXPIRED} sao finais — nenhuma transicao sai deles.
 */
public enum RoomStatus {
    WAITING,
    ACTIVE,
    ENDED,
    EXPIRED
}
