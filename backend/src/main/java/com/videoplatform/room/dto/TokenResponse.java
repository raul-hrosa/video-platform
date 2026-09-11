package com.videoplatform.room.dto;

/**
 * Credenciais minimas entregues ao browser para entrar na chamada (Sprint 11 §6).
 * Nunca inclui API key, JWT secret nem credencial interna do provider.
 *
 * @param token         token de participante emitido pelo provider de midia
 * @param roomId        id da sala no dominio da plataforma
 * @param participantId identity atribuido ao participante; o cliente usa para
 *                      resolver a propria ParticipantSession
 * @param serverUrl     URL {@code wss://...} do signaling/media plane do provider
 */
public record TokenResponse(String token, String roomId, String participantId, String serverUrl) {
}
