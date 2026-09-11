package com.videoplatform.provider.pulsertc;

import com.videoplatform.common.logging.LogEvents;
import com.videoplatform.provider.pulsertc.PulseRtcMediaProvider.ParticipantQualitySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Expõe ao frontend a qualidade ao vivo calculada pela Quality Engine do
 * PulseRTC (Sprint 12 §16). O frontend envia {@code quality_report} pelo
 * signaling (§15) e consulta o veredito por aqui — a API {@code /v1} do PulseRTC
 * nunca é chamada direto do browser. Só existe quando {@code media.provider=pulsertc};
 * com o LiveKit a rota devolve 404 e o cliente ignora.
 */
@RestController
@RequestMapping("/api/v1/rooms")
@ConditionalOnProperty(prefix = "media", name = "provider", havingValue = "pulsertc")
public class MediaQualityController {

    private static final Logger log = LoggerFactory.getLogger(MediaQualityController.class);

    private final PulseRtcMediaProvider provider;

    public MediaQualityController(PulseRtcMediaProvider provider) {
        this.provider = provider;
    }

    @GetMapping("/{roomId}/media-quality")
    public List<ParticipantQualitySnapshot> roomQuality(@PathVariable String roomId) {
        List<ParticipantQualitySnapshot> breakdown = provider.roomQualityBreakdown(roomId);
        log.atDebug()
                .addKeyValue("event", LogEvents.PULSERTC_QUALITY_UPDATED)
                .addKeyValue("provider", "pulsertc")
                .addKeyValue("roomId", roomId)
                .addKeyValue("participants", breakdown.size())
                .setMessage("pulsertc room quality queried")
                .log();
        return breakdown;
    }
}
