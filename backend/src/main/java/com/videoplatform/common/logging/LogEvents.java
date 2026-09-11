package com.videoplatform.common.logging;

/**
 * Nomes de eventos usados no campo estruturado {@code event} dos logs.
 * Manter consistente em toda a aplicacao (pesquisavel em Loki/Grafana).
 */
public final class LogEvents {

    private LogEvents() {
    }

    // Room
    public static final String ROOM_CREATED = "ROOM_CREATED";
    public static final String ROOM_CREATED_FROM_PROFILE = "ROOM_CREATED_FROM_PROFILE";
    public static final String ROOM_STARTED = "ROOM_STARTED";
    public static final String ROOM_ENDED = "ROOM_ENDED";
    public static final String ROOM_EXPIRED = "ROOM_EXPIRED";
    public static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    public static final String ROOM_ACCESS_DENIED = "ROOM_ACCESS_DENIED";

    // Room history / analytics (Sprint 6 §34) — acesso do owner às informações da sala.
    public static final String ROOM_HISTORY_VIEWED = "ROOM_HISTORY_VIEWED";
    public static final String ROOM_DETAIL_VIEWED = "ROOM_DETAIL_VIEWED";
    public static final String ROOM_ANALYTICS_VIEWED = "ROOM_ANALYTICS_VIEWED";
    public static final String ROOM_SESSIONS_VIEWED = "ROOM_SESSIONS_VIEWED";
    public static final String ROOM_PARTICIPANTS_VIEWED = "ROOM_PARTICIPANTS_VIEWED";
    public static final String ROOM_QUALITY_VIEWED = "ROOM_QUALITY_VIEWED";
    public static final String ROOM_EVENTS_VIEWED = "ROOM_EVENTS_VIEWED";
    public static final String PARTICIPANT_ANALYTICS_VIEWED = "PARTICIPANT_ANALYTICS_VIEWED";

    // Organization (Sprint 7 §34)
    public static final String ORGANIZATION_CREATED = "ORGANIZATION_CREATED";
    public static final String ORGANIZATION_VIEWED = "ORGANIZATION_VIEWED";
    public static final String ORGANIZATION_UPDATED = "ORGANIZATION_UPDATED";
    public static final String ORGANIZATION_ACCESS_DENIED = "ORGANIZATION_ACCESS_DENIED";
    public static final String MEMBER_VIEWED = "MEMBER_VIEWED";

    // Appointment (Sprint 8 §36)
    public static final String APPOINTMENT_CREATED = "APPOINTMENT_CREATED";
    public static final String APPOINTMENT_UPDATED = "APPOINTMENT_UPDATED";
    public static final String APPOINTMENT_CANCELLED = "APPOINTMENT_CANCELLED";
    public static final String APPOINTMENT_VIEWED = "APPOINTMENT_VIEWED";
    public static final String APPOINTMENT_NOT_FOUND = "APPOINTMENT_NOT_FOUND";
    public static final String APPOINTMENT_ACCESS_DENIED = "APPOINTMENT_ACCESS_DENIED";
    public static final String APPOINTMENT_OCCURRENCE_CREATED = "APPOINTMENT_OCCURRENCE_CREATED";
    public static final String APPOINTMENT_OCCURRENCE_RESOLVED = "APPOINTMENT_OCCURRENCE_RESOLVED";
    public static final String WAITING_ROOM_ENTERED = "WAITING_ROOM_ENTERED";
    public static final String WAITING_ROOM_LEFT = "WAITING_ROOM_LEFT";
    public static final String APPOINTMENT_CALL_STARTED = "APPOINTMENT_CALL_STARTED";
    public static final String APPOINTMENT_CALL_ENDED = "APPOINTMENT_CALL_ENDED";

    // Room profile
    public static final String ROOM_PROFILE_CREATED = "ROOM_PROFILE_CREATED";
    public static final String ROOM_PROFILE_UPDATED = "ROOM_PROFILE_UPDATED";
    public static final String ROOM_PROFILE_DELETED = "ROOM_PROFILE_DELETED";
    public static final String ROOM_PROFILE_NOT_FOUND = "ROOM_PROFILE_NOT_FOUND";

    // Token
    public static final String TOKEN_GENERATION_STARTED = "TOKEN_GENERATION_STARTED";
    public static final String TOKEN_GENERATED = "TOKEN_GENERATED";
    public static final String TOKEN_GENERATION_FAILED = "TOKEN_GENERATION_FAILED";

    // Participant
    public static final String PARTICIPANT_IDENTIFIED = "PARTICIPANT_IDENTIFIED";
    public static final String PARTICIPANT_JOINED = "PARTICIPANT_JOINED";
    public static final String PARTICIPANT_LEFT = "PARTICIPANT_LEFT";
    public static final String PARTICIPANT_RECONNECTED = "PARTICIPANT_RECONNECTED";
    public static final String PARTICIPANT_SESSION_STARTED = "PARTICIPANT_SESSION_STARTED";
    public static final String PARTICIPANT_SESSION_ENDED = "PARTICIPANT_SESSION_ENDED";

    // Auth
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    // Connection quality
    public static final String CONNECTION_QUALITY_CHANGED = "CONNECTION_QUALITY_CHANGED";
    public static final String CONNECTION_METRICS_RECORDED = "CONNECTION_METRICS_RECORDED";
    public static final String CONNECTION_METRICS_FAILED = "CONNECTION_METRICS_FAILED";

    // Webhook
    public static final String WEBHOOK_RECEIVED = "WEBHOOK_RECEIVED";
    public static final String WEBHOOK_VALIDATED = "WEBHOOK_VALIDATED";
    public static final String WEBHOOK_REJECTED = "WEBHOOK_REJECTED";
    public static final String WEBHOOK_PROCESSED = "WEBHOOK_PROCESSED";
    public static final String WEBHOOK_DUPLICATED = "WEBHOOK_DUPLICATED";
    public static final String WEBHOOK_PROCESSING_FAILED = "WEBHOOK_PROCESSING_FAILED";

    // PulseRTC media provider (Sprint 11 §16)
    public static final String PULSERTC_ROOM_CREATED = "PULSERTC_ROOM_CREATED";
    public static final String PULSERTC_ROOM_CLOSED = "PULSERTC_ROOM_CLOSED";
    public static final String PULSERTC_TOKEN_CREATED = "PULSERTC_TOKEN_CREATED";
    public static final String PULSERTC_PARTICIPANT_JOINED = "PULSERTC_PARTICIPANT_JOINED";
    public static final String PULSERTC_PARTICIPANT_LEFT = "PULSERTC_PARTICIPANT_LEFT";
    public static final String PULSERTC_SESSION_RECONNECTED = "PULSERTC_SESSION_RECONNECTED";
    public static final String PULSERTC_SESSION_RECOVERED = "PULSERTC_SESSION_RECOVERED";
    public static final String PULSERTC_CONNECTION_FAILED = "PULSERTC_CONNECTION_FAILED";
    public static final String PULSERTC_QUALITY_UPDATED = "PULSERTC_QUALITY_UPDATED";
    public static final String PULSERTC_API_ERROR = "PULSERTC_API_ERROR";
    public static final String PULSERTC_WEBHOOK_REPLAY_REJECTED = "PULSERTC_WEBHOOK_REPLAY_REJECTED";

    // HTTP
    public static final String HTTP_REQUEST = "HTTP_REQUEST";
    public static final String HTTP_RESPONSE = "HTTP_RESPONSE";
    public static final String HTTP_ERROR = "HTTP_ERROR";
}
