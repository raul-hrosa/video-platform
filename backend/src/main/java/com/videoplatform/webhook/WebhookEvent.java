package com.videoplatform.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Registro de um evento de webhook recebido do LiveKit. Usado para idempotencia
 * (event_id unico) e auditoria.
 */
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private String eventId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WebhookStatus status;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    protected WebhookEvent() {
    }

    public static WebhookEvent received(String eventId, String eventType, String payload, Instant now) {
        WebhookEvent event = new WebhookEvent();
        event.eventId = eventId;
        event.eventType = eventType;
        event.payload = payload;
        event.receivedAt = now;
        event.status = WebhookStatus.RECEIVED;
        return event;
    }

    public void markProcessed(Instant at) {
        this.status = WebhookStatus.PROCESSED;
        this.processedAt = at;
    }

    public void markIgnored(Instant at) {
        this.status = WebhookStatus.IGNORED;
        this.processedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public WebhookStatus getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
