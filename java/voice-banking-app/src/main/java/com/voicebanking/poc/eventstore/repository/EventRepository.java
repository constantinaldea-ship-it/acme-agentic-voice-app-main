package com.voicebanking.poc.eventstore.repository;

import com.voicebanking.poc.eventstore.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {

    List<Event> findByEventTypeOrderByTimestampDesc(String eventType);

    List<Event> findByTimestampBetweenOrderByTimestampDesc(Instant from, Instant to);

    List<Event> findByEventTypeAndTimestampBetweenOrderByTimestampDesc(
            String eventType, Instant from, Instant to);
}
