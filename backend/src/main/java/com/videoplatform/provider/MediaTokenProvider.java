package com.videoplatform.provider;

/**
 * Contrato da plataforma para emitir credenciais de entrada numa Room.
 * Implementacoes podem usar LiveKit ou outro provider de midia.
 */
public interface MediaTokenProvider {

    MediaToken createRoomToken(String roomId, String participantRef, String displayName);

    record MediaToken(String token, String participantRef) {
    }
}
