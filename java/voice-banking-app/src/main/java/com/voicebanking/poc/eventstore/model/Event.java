package com.voicebanking.poc.eventstore.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a persisted event.
 * POC only — decoupled from production domain models.
 */
@Entity
@Table(name = "poc_events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "CLOB")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant timestamp;

    @Column(name = "metadata", columnDefinition = "CLOB")
    private String metadata;

    protected Event() {
        // JPA
    }

    public Event(String eventType, String payload, Instant timestamp, String metadata) {
        this.eventType = eventType;
        this.payload = payload;
        this.timestamp = timestamp;
        this.metadata = metadata;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getTimestamp() { return timestamp; }
    public String getMetadata() { return metadata; }

    public void setId(UUID id) { this.id = id; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public void setPayload(String payload) { this.payload = payload; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
