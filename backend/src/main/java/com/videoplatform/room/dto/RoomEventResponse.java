package com.videoplatform.room.dto;

import java.time.Instant;

/**
 * Um evento na linha do tempo de uma sala (Sprint 9 §14). Derivado dos dados ja
 * persistidos (transicoes da Room, sessoes de participacao, mudancas de
 * qualidade) — nao ha armazenamento novo de eventos.
 */
public record RoomEventResponse(
        String type,
        Instant at,
        String participantRef,
        String detail
) {
    public static RoomEventResponse room(String type, Instant at) {
        return new RoomEventResponse(type, at, null, null);
    }

    public static RoomEventResponse participant(String type, Instant at, String participantRef, String detail) {
        return new RoomEventResponse(type, at, participantRef, detail);
    }
}
