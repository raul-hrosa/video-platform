package com.videoplatform.provider.pulsertc;

import com.videoplatform.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Traduz os codigos do PulseRTC (§8) para o modelo de erro da plataforma
 * ({@link ApiException} com {@code code} + {@code message}). Nunca devolve stack
 * trace nem detalhe interno do provider ao cliente.
 */
final class PulseRtcErrorMapper {

    private PulseRtcErrorMapper() {
    }

    static ApiException toApiException(PulseRtcApiException ex) {
        return switch (ex.pulseCode()) {
            case "ROOM_NOT_FOUND" -> new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND",
                    "Sala nao encontrada.", ex);
            case "PARTICIPANT_NOT_FOUND" -> new ApiException(HttpStatus.NOT_FOUND, "PARTICIPANT_NOT_FOUND",
                    "Participante nao encontrado.", ex);
            case "SESSION_NOT_FOUND" -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND",
                    "Sessao nao encontrada.", ex);
            case "ROOM_CLOSED" -> new ApiException(HttpStatus.CONFLICT, "ROOM_CLOSED",
                    "Esta sala ja foi encerrada.", ex);
            case "RATE_LIMITED" -> new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                    "Muitas requisicoes ao servico de video. Tente novamente em instantes.", ex);
            case "FORBIDDEN" -> new ApiException(HttpStatus.FORBIDDEN, "MEDIA_FORBIDDEN",
                    "Operacao nao permitida pelo servico de video.", ex);
            case "UNAUTHORIZED", "NOT_CONFIGURED" -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MEDIA_PROVIDER_NOT_CONFIGURED", "Servico de video indisponivel no momento.", ex);
            case "ROOM_ON_OTHER_NODE", "NODE_UNAVAILABLE", "CLUSTER_STATE_UNAVAILABLE" ->
                    new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MEDIA_PROVIDER_UNAVAILABLE",
                            "Servico de video temporariamente indisponivel. Tente novamente.", ex);
            case "IDEMPOTENCY_CONFLICT" -> new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                    "Requisicao concorrente para o mesmo recurso.", ex);
            default -> new ApiException(HttpStatus.BAD_GATEWAY, "MEDIA_PROVIDER_ERROR",
                    "Falha ao falar com o servico de video.", ex);
        };
    }
}
