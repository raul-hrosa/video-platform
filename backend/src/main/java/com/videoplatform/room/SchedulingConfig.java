package com.videoplatform.room;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita {@code @Scheduled} na aplicacao. Isolado numa config propria para
 * manter {@code VideoPlatformApplication} enxuta e para poder ser omitido em
 * fatias de teste.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
