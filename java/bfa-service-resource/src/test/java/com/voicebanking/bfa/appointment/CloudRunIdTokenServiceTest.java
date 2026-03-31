package com.voicebanking.bfa.appointment;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudRunIdTokenServiceTest {

    @Test
    void returnsServerlessHeaderForCloudRunAudience() {
        CloudRunIdTokenService service = new CloudRunIdTokenService(audience ->
                new CloudRunIdTokenService.CachedToken("id-token", Instant.now().plusSeconds(300)));

        Optional<String> header = service.serverlessAuthorizationHeaderValue(
                "https://mock-server-abc-uc.a.run.app/advisory-appointments/taxonomy"
        );

        assertTrue(header.isPresent());
        assertEquals("Bearer id-token", header.orElseThrow());
    }

    @Test
    void skipsNonCloudRunUrls() {
        CloudRunIdTokenService service = new CloudRunIdTokenService(audience ->
                new CloudRunIdTokenService.CachedToken("unused", Instant.now().plusSeconds(300)));

        Optional<String> header = service.serverlessAuthorizationHeaderValue("http://localhost:8080/health");

        assertTrue(header.isEmpty());
    }

    @Test
    void reusesCachedTokensBeforeExpiry() {
        AtomicInteger invocations = new AtomicInteger();
        CloudRunIdTokenService service = new CloudRunIdTokenService(audience -> {
            invocations.incrementAndGet();
            return new CloudRunIdTokenService.CachedToken("cached-token", Instant.now().plusSeconds(300));
        });

        service.serverlessAuthorizationHeaderValue("https://mock-server-abc-uc.a.run.app/one");
        service.serverlessAuthorizationHeaderValue("https://mock-server-abc-uc.a.run.app/two");

        assertEquals(1, invocations.get());
    }

    @Test
    void returnsEmptyWhenTokenFactoryFails() {
        CloudRunIdTokenService service = new CloudRunIdTokenService(audience -> {
            throw new IOException("boom");
        });

        Optional<String> header = service.serverlessAuthorizationHeaderValue(
                "https://mock-server-abc-uc.a.run.app/advisory-appointments/taxonomy"
        );

        assertTrue(header.isEmpty());
    }
}
