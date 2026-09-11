package com.videoplatform.provider.pulsertc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videoplatform.common.logging.CorrelationId;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.ConnectionView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.CreateRoomRequest;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.CreateTokenRequest;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.ErrorBody;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.ParticipantListView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.ParticipantView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.ParticipantQualityView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.RoomQualityView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.RoomView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.SessionView;
import com.videoplatform.provider.pulsertc.PulseRtcDtos.TokenView;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.function.Supplier;

/**
 * Camada isolada de acesso ao control plane HTTP do PulseRTC ({@code /v1},
 * Sprint 11 §2.1). Nao conhece Controllers, JPA nem regras de negocio; so
 * transporta e traduz erros de rede para {@link PulseRtcApiException}. A apiKey
 * segue apenas no header {@code Authorization} e nunca e logada (§3/§16).
 */
public class PulseRtcClient {

    private final RestClient http;
    private final ObjectMapper objectMapper;

    public PulseRtcClient(PulseRtcProperties properties, RestClient.Builder builder,
                          ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.http = builder
                .baseUrl(trimTrailingSlash(properties.apiUrl()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + safe(properties.apiKey()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ---- rooms ----

    public RoomView createRoom(String roomId) {
        return call(() -> errors(http.post().uri("/v1/rooms")
                .header("Idempotency-Key", roomId)
                .headers(this::correlation)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateRoomRequest(roomId))
                .retrieve()).body(RoomView.class));
    }

    public RoomView getRoom(String roomId) {
        return call(() -> errors(http.get().uri("/v1/rooms/{roomId}", roomId)
                .headers(this::correlation).retrieve()).body(RoomView.class));
    }

    public void deleteRoom(String roomId) {
        call(() -> errors(http.delete().uri("/v1/rooms/{roomId}", roomId)
                .headers(this::correlation).retrieve()).toBodilessEntity());
    }

    // ---- token / connection ----

    public TokenView createToken(String roomId, CreateTokenRequest request) {
        return call(() -> errors(http.post().uri("/v1/rooms/{roomId}/tokens", roomId)
                .headers(this::correlation)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()).body(TokenView.class));
    }

    public ConnectionView getConnection(String roomId) {
        return call(() -> errors(http.get().uri("/v1/rooms/{roomId}/connection", roomId)
                .headers(this::correlation).retrieve()).body(ConnectionView.class));
    }

    // ---- participants / sessions ----

    public ParticipantListView listParticipants(String roomId) {
        return call(() -> errors(http.get().uri("/v1/rooms/{roomId}/participants", roomId)
                .headers(this::correlation).retrieve()).body(ParticipantListView.class));
    }

    public ParticipantView getParticipant(String roomId, String identity) {
        return call(() -> errors(http.get().uri("/v1/rooms/{roomId}/participants/{identity}", roomId, identity)
                .headers(this::correlation).retrieve()).body(ParticipantView.class));
    }

    public SessionView getParticipantSession(String roomId, String identity) {
        return call(() -> errors(http.get()
                .uri("/v1/rooms/{roomId}/participants/{identity}/session", roomId, identity)
                .headers(this::correlation).retrieve()).body(SessionView.class));
    }

    // ---- quality ----

    public RoomQualityView getRoomQuality(String roomId) {
        return call(() -> errors(http.get().uri("/v1/rooms/{roomId}/quality", roomId)
                .headers(this::correlation).retrieve()).body(RoomQualityView.class));
    }

    public ParticipantQualityView getParticipantQuality(String roomId, String identity) {
        return call(() -> errors(http.get()
                .uri("/v1/rooms/{roomId}/participants/{identity}/quality", roomId, identity)
                .headers(this::correlation).retrieve()).body(ParticipantQualityView.class));
    }

    // ---- infra ----

    private void correlation(HttpHeaders headers) {
        String correlationId = MDC.get(CorrelationId.MDC_KEY);
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set(CorrelationId.HEADER, correlationId);
        }
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (PulseRtcApiException already) {
            throw already;
        } catch (ResourceAccessException network) {
            throw new PulseRtcApiException(503, "NODE_UNAVAILABLE", null,
                    "PulseRTC unreachable", network);
        }
    }

    private RestClient.ResponseSpec errors(RestClient.ResponseSpec spec) {
        return spec.onStatus(HttpStatusCode::isError, (req, res) -> {
            throw translate(res.getStatusCode().value(),
                    res.getHeaders().getFirst("X-Request-Id"),
                    readBody(res.getBody()));
        });
    }

    private PulseRtcApiException translate(int status, String requestId, String rawBody) {
        String code = null;
        String message = null;
        String bodyRequestId = null;
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                ErrorBody parsed = objectMapper.readValue(rawBody, ErrorBody.class);
                code = parsed.code();
                message = parsed.message();
                bodyRequestId = parsed.requestId();
            } catch (Exception ignored) {
                // corpo nao-JSON: cai no default por status
            }
        }
        if (code == null || code.isBlank()) {
            code = defaultCodeForStatus(status);
        }
        return new PulseRtcApiException(status, code,
                requestId != null ? requestId : bodyRequestId,
                message != null ? message : "PulseRTC error " + status);
    }

    private static String defaultCodeForStatus(int status) {
        return switch (status) {
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "ROOM_NOT_FOUND";
            case 409 -> "ROOM_CLOSED";
            case 429 -> "RATE_LIMITED";
            case 503 -> "NODE_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }

    private static String readBody(java.io.InputStream body) {
        try {
            return new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
