package com.voicebanking.bfa.appointment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the advisory appointment mock-server upstream.
 *
 * @author Codex
 * @since 2026-03-15
 */
@Component
@ConfigurationProperties(prefix = "bfa.appointment.upstream")
public class AppointmentUpstreamProperties {

    private String baseUrl = "http://localhost:8080";
    private String authorizationHeaderName = "Authorization";
    private String correlationHeaderName = "X-Correlation-ID";
    private String clientHeaderName = "X-BFA-Client";
    private String clientHeaderValue = "advisory-appointment-bff";
    private String scenarioHeaderName = "X-Mock-Scenario";
    private String fallbackBearerToken = "mock-appointment-service-token";
    private boolean cloudRunAuthEnabled = true;
    private String cloudRunAuthorizationHeaderName = "X-Serverless-Authorization";
    private String cloudRunAudience;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthorizationHeaderName() {
        return authorizationHeaderName;
    }

    public void setAuthorizationHeaderName(String authorizationHeaderName) {
        this.authorizationHeaderName = authorizationHeaderName;
    }

    public String getCorrelationHeaderName() {
        return correlationHeaderName;
    }

    public void setCorrelationHeaderName(String correlationHeaderName) {
        this.correlationHeaderName = correlationHeaderName;
    }

    public String getClientHeaderName() {
        return clientHeaderName;
    }

    public void setClientHeaderName(String clientHeaderName) {
        this.clientHeaderName = clientHeaderName;
    }

    public String getClientHeaderValue() {
        return clientHeaderValue;
    }

    public void setClientHeaderValue(String clientHeaderValue) {
        this.clientHeaderValue = clientHeaderValue;
    }

    public String getScenarioHeaderName() {
        return scenarioHeaderName;
    }

    public void setScenarioHeaderName(String scenarioHeaderName) {
        this.scenarioHeaderName = scenarioHeaderName;
    }

    public String getFallbackBearerToken() {
        return fallbackBearerToken;
    }

    public void setFallbackBearerToken(String fallbackBearerToken) {
        this.fallbackBearerToken = fallbackBearerToken;
    }

    public boolean isCloudRunAuthEnabled() {
        return cloudRunAuthEnabled;
    }

    public void setCloudRunAuthEnabled(boolean cloudRunAuthEnabled) {
        this.cloudRunAuthEnabled = cloudRunAuthEnabled;
    }

    public String getCloudRunAuthorizationHeaderName() {
        return cloudRunAuthorizationHeaderName;
    }

    public void setCloudRunAuthorizationHeaderName(String cloudRunAuthorizationHeaderName) {
        this.cloudRunAuthorizationHeaderName = cloudRunAuthorizationHeaderName;
    }

    public String getCloudRunAudience() {
        if (cloudRunAudience != null && !cloudRunAudience.isBlank()) {
            return cloudRunAudience;
        }
        return baseUrl;
    }

    public void setCloudRunAudience(String cloudRunAudience) {
        this.cloudRunAudience = cloudRunAudience;
    }
}
