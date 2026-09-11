package com.videoplatform.common;

import org.springframework.http.HttpStatus;

/**
 * Excecao de negocio com um codigo estavel (para o cliente) e uma
 * mensagem amigavel. Nunca carrega detalhes tecnicos sensiveis.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public ApiException(HttpStatus status, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
