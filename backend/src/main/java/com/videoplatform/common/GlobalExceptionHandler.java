package com.videoplatform.common;

import com.videoplatform.common.logging.LogEvents;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Tratamento centralizado de excecoes. Sempre responde com {@link ErrorResponse}
 * ({@code code} + {@code message}) e nunca devolve stack trace para o cliente.
 * O stack trace de erros inesperados vai apenas para o log interno.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        int status = ex.getStatus().value();
        if (ex.getStatus().is5xxServerError()) {
            log.atError()
                    .addKeyValue("event", LogEvents.HTTP_ERROR)
                    .addKeyValue("httpStatus", status)
                    .addKeyValue("errorCode", ex.getErrorCode())
                    .setCause(ex)
                    .setMessage("request failed")
                    .log();
        } else {
            log.atWarn()
                    .addKeyValue("event", LogEvents.HTTP_ERROR)
                    .addKeyValue("httpStatus", status)
                    .addKeyValue("errorCode", ex.getErrorCode())
                    .setMessage("request rejected")
                    .log();
        }
        return ResponseEntity.status(ex.getStatus())
                .body(new ErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleValidation(Exception ex) {
        log.atWarn()
                .addKeyValue("event", LogEvents.HTTP_ERROR)
                .addKeyValue("httpStatus", 400)
                .addKeyValue("errorCode", "INVALID_REQUEST")
                .setMessage("request validation failed")
                .log();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_REQUEST", "Dados da requisicao invalidos."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.atError()
                .addKeyValue("event", LogEvents.HTTP_ERROR)
                .addKeyValue("httpStatus", 500)
                .addKeyValue("errorCode", "INTERNAL_ERROR")
                .addKeyValue("exceptionType", ex.getClass().getName())
                .setCause(ex)
                .setMessage("unexpected error")
                .log();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_ERROR", "Ocorreu um erro inesperado."));
    }
}
