package com.videoplatform.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * {@link Clock} injetavel (Sprint 8 §46). Regras de negocio sensiveis a tempo —
 * janela de entrada, inicio/fim, expiracao, proxima ocorrencia, recorrencia —
 * usam este bean; nos testes ele e' substituido por {@code Clock.fixed(...)}.
 */
@Configuration
public class TimeConfig {

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
