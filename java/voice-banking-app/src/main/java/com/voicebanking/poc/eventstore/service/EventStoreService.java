package com.voicebanking.poc.eventstore.service;

import com.voicebanking.poc.eventstore.model.Event;

import java.time.Instant;
import java.util.List;

/**
 * Event store operations — interface allows swapping implementations
 * (H2 for POC, Cloud SQL / BigQuery for production).
 */
public interface EventStoreService {

    Event writeEvent(Event event);

    List<Event> readEvents(String eventType, Instant from, Instant to);
}
