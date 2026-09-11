package com.videoplatform.provider.pulsertc;

/**
 * Erro devolvido pelo control plane do PulseRTC ({@code /v1}). Carrega o codigo
 * estavel do PulseRTC (§8) e o {@code requestId} para correlacao. Nunca carrega
 * corpo cru, header {@code Authorization} nem stack trace do provider.
 */
public class PulseRtcApiException extends RuntimeException {

    private final int httpStatus;
    private final String pulseCode;
    private final String requestId;

    public PulseRtcApiException(int httpStatus, String pulseCode, String requestId, String message) {
        this(httpStatus, pulseCode, requestId, message, null);
    }

    public PulseRtcApiException(int httpStatus, String pulseCode, String requestId,
                                String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.pulseCode = pulseCode == null ? "UNKNOWN" : pulseCode;
        this.requestId = requestId;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String pulseCode() {
        return pulseCode;
    }

    public String requestId() {
        return requestId;
    }

    public boolean isIdempotencyConflict() {
        return "IDEMPOTENCY_CONFLICT".equals(pulseCode);
    }
}
