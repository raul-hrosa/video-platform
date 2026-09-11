package com.videoplatform.webhook;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Recebe eventos do LiveKit. O corpo precisa ser lido cru porque a validacao
     * confere o checksum SHA-256 dos bytes exatos. O LiveKit envia Content-Type
     * application/webhook+json, por isso consumimos qualquer tipo.
     */
    @PostMapping(value = "/livekit", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> livekit(
            @RequestBody String body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return respond(webhookService.process(body, authorization));
    }

    /**
     * Recebe eventos do PulseRTC (Sprint 11 §12). O corpo precisa ser lido cru
     * porque o HMAC-SHA256 confere os bytes exatos; a assinatura chega no header
     * {@code X-PulseRTC-Signature} e e repassada ao parser via {@code authHeader}.
     * Apenas um {@code MediaWebhookParser} fica ativo por vez (§17), entao ambos
     * os endpoints delegam ao mesmo {@code WebhookService}.
     */
    @PostMapping(value = "/pulsertc", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> pulsertc(
            @RequestBody String body,
            @RequestHeader(value = "X-PulseRTC-Signature", required = false) String signature) {
        return respond(webhookService.process(body, signature));
    }

    private static Map<String, Object> respond(WebhookService.WebhookResult result) {
        return Map.of(
                "processed", result.processed(),
                "reason", result.reason() == null ? "" : result.reason());
    }
}
