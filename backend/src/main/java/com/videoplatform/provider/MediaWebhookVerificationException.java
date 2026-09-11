package com.videoplatform.provider;

/**
 * Lancada quando o webhook do provider de midia nao passa na validacao de
 * assinatura. Nao carrega o header nem a assinatura na mensagem.
 */
public class MediaWebhookVerificationException extends RuntimeException {

    public MediaWebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
