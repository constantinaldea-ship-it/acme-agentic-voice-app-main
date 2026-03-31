package com.voicebanking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Application context loading test.
 * Verifies that the Spring Boot application starts correctly.
 */
@SpringBootTest
@ActiveProfiles("local")
class VoiceBankingApplicationTest {

    @Test
    void contextLoads() {
        // Verifies Spring context loads without errors
    }

}
