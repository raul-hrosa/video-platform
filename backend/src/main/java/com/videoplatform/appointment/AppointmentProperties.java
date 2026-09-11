package com.videoplatform.appointment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Parametros do agendamento (§11, §20). {@code joinEarlyMinutes} = a janela de
 * entrada antes do horario (default 15 — nao espalhar o literal pelo codigo).
 * {@code prepareAhead} = com quanta antecedencia o scheduler materializa a
 * proxima ocorrencia.
 */
@ConfigurationProperties(prefix = "appointment")
public record AppointmentProperties(
        Integer joinEarlyMinutes,
        Duration prepareAhead
) {

    public AppointmentProperties {
        if (joinEarlyMinutes == null) {
            joinEarlyMinutes = 15;
        }
        if (prepareAhead == null) {
            prepareAhead = Duration.ofMinutes(20);
        }
    }

    public Duration joinEarly() {
        return Duration.ofMinutes(joinEarlyMinutes);
    }
}
