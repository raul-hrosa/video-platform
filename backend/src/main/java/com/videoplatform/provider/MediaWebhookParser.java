package com.videoplatform.provider;

/**
 * Valida a autenticidade de um webhook do provider de midia e o traduz para o
 * modelo interno {@link MediaWebhookEvent}. A implementacao concreta (LiveKit)
 * vive na camada de infraestrutura; o dominio depende apenas deste contrato.
 */
public interface MediaWebhookParser {

    /**
     * @param body       corpo cru da requisicao (bytes exatos usados no checksum)
     * @param authHeader  valor do header {@code Authorization}, pode ser {@code null}
     * @throws MediaWebhookVerificationException se a assinatura for invalida
     */
    MediaWebhookEvent parse(String body, String authHeader);
}
