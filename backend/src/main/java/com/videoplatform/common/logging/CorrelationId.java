package com.videoplatform.common.logging;

import org.slf4j.MDC;

/**
 * Chaves de MDC e o header usado para propagar o correlationId.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    public static final String MDC_WEBHOOK_EVENT_ID = "webhookEventId";
    public static final String MDC_EVENT_TYPE = "eventType";

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
