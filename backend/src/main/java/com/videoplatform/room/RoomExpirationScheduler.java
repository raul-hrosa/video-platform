package com.videoplatform.room;

import com.videoplatform.common.logging.LogEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Varredura periodica que move para EXPIRED as salas cujo {@code expires_at} ja
 * passou (Sprint 5 §36). Complementa a verificacao preguicosa feita no acesso ao
 * token — a garantia de que uma sala expirada nao aceita novos participantes esta
 * em {@link RoomService#getJoinableByRoomId}; este scheduler so mantem o estado do
 * banco e os logs em dia.
 *
 * <p>{@code fixedDelay} serializa as execucoes (sem sobreposicao num unico no).
 * Nao e' um sistema distribuido — ver README.
 */
@Component
public class RoomExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomExpirationScheduler.class);

    private final RoomService roomService;

    public RoomExpirationScheduler(RoomService roomService) {
        this.roomService = roomService;
    }

    @Scheduled(fixedDelayString = "${room.expiration.check-interval:PT30S}")
    void sweep() {
        try {
            int expired = roomService.expireDueRooms(Instant.now());
            if (expired > 0) {
                log.atDebug()
                        .addKeyValue("event", LogEvents.ROOM_EXPIRED)
                        .addKeyValue("expiredCount", expired)
                        .setMessage("room expiration sweep")
                        .log();
            }
        } catch (RuntimeException ex) {
            log.atWarn()
                    .addKeyValue("event", LogEvents.ROOM_EXPIRED)
                    .setCause(ex)
                    .setMessage("room expiration sweep failed, will retry next tick")
                    .log();
        }
    }
}
