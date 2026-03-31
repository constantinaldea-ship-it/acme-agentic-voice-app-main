package com.voicebanking.poc.eventstore.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.poc.eventstore.dto.WriteEventRequest;
import com.voicebanking.poc.eventstore.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "poc"})
class EventStoreControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
    }

    @Test
    void writeEvent_shouldReturn201WithPersistedEvent() throws Exception {
        var request = new WriteEventRequest("audit", "{\"action\":\"login\"}", "{\"source\":\"test\"}");

        mockMvc.perform(post("/api/poc/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.eventType", is("audit")))
                .andExpect(jsonPath("$.payload", is("{\"action\":\"login\"}")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.metadata", is("{\"source\":\"test\"}")));
    }

    @Test
    void writeEvent_shouldRejectMissingEventType() throws Exception {
        var body = "{\"payload\":\"{}\"}";

        mockMvc.perform(post("/api/poc/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void writeEvent_shouldRejectMissingPayload() throws Exception {
        var body = "{\"eventType\":\"audit\"}";

        mockMvc.perform(post("/api/poc/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void readEvents_shouldReturnAllEvents() throws Exception {
        writeTestEvent("audit", "{\"a\":1}");
        writeTestEvent("metric", "{\"b\":2}");

        mockMvc.perform(get("/api/poc/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void readEvents_shouldFilterByEventType() throws Exception {
        writeTestEvent("audit", "{\"a\":1}");
        writeTestEvent("metric", "{\"b\":2}");
        writeTestEvent("audit", "{\"c\":3}");

        mockMvc.perform(get("/api/poc/events").param("eventType", "audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].eventType", is("audit")))
                .andExpect(jsonPath("$[1].eventType", is("audit")));
    }

    @Test
    void readEvents_shouldFilterByTimeRange() throws Exception {
        writeTestEvent("audit", "{\"a\":1}");

        mockMvc.perform(get("/api/poc/events")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-12-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void readEvents_shouldReturnEmptyForNonMatchingType() throws Exception {
        writeTestEvent("audit", "{\"a\":1}");

        mockMvc.perform(get("/api/poc/events").param("eventType", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private void writeTestEvent(String type, String payload) throws Exception {
        var request = new WriteEventRequest(type, payload, null);
        mockMvc.perform(post("/api/poc/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }
}
