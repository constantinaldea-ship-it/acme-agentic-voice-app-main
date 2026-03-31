package com.voicebanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;

/**
 * Voice Banking Assistant Application
 * 
 * <p>Main entry point for the Java implementation of the Voice Banking Assistant.
 * This is a migration from the TypeScript/Node.js PoC, using Google ADK for
 * LLM orchestration and Spring Boot for the REST API.</p>
 * 
 * <h2>Runtime Profiles</h2>
 * <ul>
 *   <li><b>local</b> - Uses stub adapters for STT/LLM (no cloud credentials)</li>
 *   <li><b>cloud</b> - Uses Google Cloud STT (Chirp 2) and Vertex AI Gemini</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * # Local profile (default)
 * mvn spring-boot:run -Dspring.profiles.active=local
 * 
 * # Cloud profile (requires GCP credentials)
 * export GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials.json
 * mvn spring-boot:run -Dspring.profiles.active=cloud
 * </pre>
 * 
 * @see <a href="../../../docs/JAVA-MIGRATION-MASTER-PLAN.md">Migration Plan</a>
 * @since 0.1.0
 */
@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class VoiceBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceBankingApplication.class, args);
    }

}
