package com.videoplatform.appointment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Varredura periodica do agendamento (§20). Nao e' a unica forma de criar
 * ocorrencias — o acesso ao link tambem materializa. {@code fixedDelay} serializa
 * as execucoes num unico no (ver README).
 */
@Component
public class AppointmentOccurrenceScheduler {

    private static final Logger log = LoggerFactory.getLogger(AppointmentOccurrenceScheduler.class);

    private final OccurrenceLifecycleService lifecycle;

    public AppointmentOccurrenceScheduler(OccurrenceLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${appointment.occurrence.check-interval:PT60S}")
    void sweep() {
        try {
            lifecycle.prepareUpcoming();
            lifecycle.syncStatuses();
        } catch (RuntimeException ex) {
            log.atWarn()
                    .setCause(ex)
                    .setMessage("appointment occurrence sweep failed, will retry next tick")
                    .log();
        }
    }
}
