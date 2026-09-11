package com.videoplatform.common;

/**
 * Corpo padrao de erro devolvido ao cliente.
 * Exemplo: {"code":"ROOM_NOT_FOUND","message":"Room not found."}
 */
public record ErrorResponse(String code, String message) {
}
