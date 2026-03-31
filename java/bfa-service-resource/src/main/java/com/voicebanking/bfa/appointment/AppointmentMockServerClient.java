package com.voicebanking.bfa.appointment;

import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentBranchSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentServiceSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentSlotSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.CancelAppointmentRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.CreateAppointmentRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.RescheduleAppointmentRequest;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentBranchSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentServiceSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentSlotSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentTaxonomyResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentView;
import com.voicebanking.bfa.appointment.AppointmentResponses.CreateAppointmentResponse;
import com.voicebanking.bfa.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/**
 * Typed outbound client for the advisory appointment mock-server upstream.
 *
 * <p>Step 2 uses deterministic local fallbacks when the upstream is unavailable,
 * but the contract-facing client exists now so Step 3 can switch to real
 * WireMock-backed responses without changing the controller surface.</p>
 *
 * @author Codex
 * @since 2026-03-15
 */
@Component
public class AppointmentMockServerClient {

    private static final Logger log = LoggerFactory.getLogger(AppointmentMockServerClient.class);

    private final RestClient restClient;
    private final AppointmentUpstreamProperties properties;
    private final CloudRunIdTokenService cloudRunIdTokenService;

    public AppointmentMockServerClient(RestClient.Builder restClientBuilder,
                                       AppointmentUpstreamProperties properties,
                                       CloudRunIdTokenService cloudRunIdTokenService) {
        this.properties = properties;
        this.cloudRunIdTokenService = cloudRunIdTokenService;
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) ->
                        log.warn("Appointment upstream error: {} {} -> {}",
                                request.getMethod(), request.getURI(), response.getStatusCode()))
                .build();
    }

    public Optional<AppointmentTaxonomyResponse> fetchTaxonomy(String correlationId,
                                                               String authorizationHeader,
                                                               String scenario) {
        return get("/advisory-appointments/taxonomy",
                new ParameterizedTypeReference<ApiResponse<AppointmentTaxonomyResponse>>() {},
                correlationId,
                authorizationHeader,
                scenario);
    }

    public Optional<AppointmentServiceSearchResponse> searchServices(AppointmentServiceSearchRequest request,
                                                                     String correlationId,
                                                                     String authorizationHeader,
                                                                     String scenario) {
        try {
            ApiResponse<AppointmentServiceSearchResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/advisory-appointments/service-search")
                            .queryParam("query", request.query())
                            .queryParamIfPresent("entryPath", Optional.ofNullable(request.entryPath()).map(Enum::name))
                            .build())
                    .headers(headers -> addRequiredHeaders(headers, correlationId, authorizationHeader, scenario))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<AppointmentServiceSearchResponse>>() {});
            return extractData(response, "searchServices");
        } catch (Exception ex) {
            log.debug("Appointment upstream unavailable for service-search fallback: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<AppointmentBranchSearchResponse> searchEligibility(AppointmentBranchSearchRequest request,
                                                                       String correlationId,
                                                                       String authorizationHeader,
                                                                       String scenario) {
        try {
            ApiResponse<AppointmentBranchSearchResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/advisory-appointments/eligibility")
                            .queryParam("entryPath", request.entryPath())
                            .queryParam("consultationChannel", request.consultationChannel())
                            .queryParamIfPresent("topicCode", Optional.ofNullable(request.topicCode()))
                            .queryParamIfPresent("serviceCode", Optional.ofNullable(request.serviceCode()))
                            .queryParamIfPresent("city", Optional.ofNullable(request.city()))
                            .queryParamIfPresent("postalCode", Optional.ofNullable(request.postalCode()))
                            .queryParamIfPresent("address", Optional.ofNullable(request.address()))
                            .queryParamIfPresent("accessible", Optional.ofNullable(request.accessible()))
                            .queryParamIfPresent("limit", Optional.ofNullable(request.limit()))
                            .build())
                    .headers(headers -> addRequiredHeaders(headers, correlationId, authorizationHeader, scenario))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<AppointmentBranchSearchResponse>>() {});
            return extractData(response, "searchEligibility");
        } catch (Exception ex) {
            log.debug("Appointment upstream unavailable for eligibility fallback: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<AppointmentSlotSearchResponse> fetchSlots(AppointmentSlotSearchRequest request,
                                                              String correlationId,
                                                              String authorizationHeader,
                                                              String scenario) {
        try {
            ApiResponse<AppointmentSlotSearchResponse> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/advisory-appointments/availability")
                            .queryParam("entryPath", request.entryPath())
                            .queryParam("consultationChannel", request.consultationChannel())
                            .queryParam("locationId", request.locationId())
                            .queryParamIfPresent("topicCode", Optional.ofNullable(request.topicCode()))
                            .queryParamIfPresent("serviceCode", Optional.ofNullable(request.serviceCode()))
                            .queryParamIfPresent("selectedDay", Optional.ofNullable(request.selectedDay()))
                            .queryParamIfPresent("advisorMode", Optional.ofNullable(request.advisorMode()).map(Enum::name))
                            .build())
                    .headers(headers -> addRequiredHeaders(headers, correlationId, authorizationHeader, scenario))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<AppointmentSlotSearchResponse>>() {});
            return extractData(response, "fetchSlots");
        } catch (Exception ex) {
            log.debug("Appointment upstream unavailable for slot fallback: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<CreateAppointmentResponse> createAppointment(CreateAppointmentRequest request,
                                                                 String correlationId,
                                                                 String authorizationHeader,
                                                                 String scenario) {
        return post("/advisory-appointments/lifecycle",
                request,
                new ParameterizedTypeReference<ApiResponse<CreateAppointmentResponse>>() {},
                correlationId,
                authorizationHeader,
                scenario,
                "createAppointment");
    }

    public Optional<AppointmentView> getAppointment(String appointmentId,
                                                    String appointmentAccessToken,
                                                    String correlationId,
                                                    String authorizationHeader,
                                                    String scenario) {
        try {
            ApiResponse<AppointmentView> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/advisory-appointments/lifecycle/{appointmentId}")
                            .queryParam("appointmentAccessToken", appointmentAccessToken)
                            .build(appointmentId))
                    .headers(headers -> addRequiredHeaders(headers, correlationId, authorizationHeader, scenario))
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponse<AppointmentView>>() {});
            return extractData(response, "getAppointment");
        } catch (Exception ex) {
            log.debug("Appointment upstream unavailable for get fallback: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<AppointmentView> cancelAppointment(String appointmentId,
                                                       CancelAppointmentRequest request,
                                                       String correlationId,
                                                       String authorizationHeader,
                                                       String scenario) {
        return post("/advisory-appointments/lifecycle/{appointmentId}/cancel",
                request,
                new ParameterizedTypeReference<ApiResponse<AppointmentView>>() {},
                correlationId,
                authorizationHeader,
                scenario,
                "cancelAppointment",
                appointmentId);
    }

    public Optional<AppointmentView> rescheduleAppointment(String appointmentId,
                                                           RescheduleAppointmentRequest request,
                                                           String correlationId,
                                                           String authorizationHeader,
                                                           String scenario) {
        return post("/advisory-appointments/lifecycle/{appointmentId}/reschedule",
                request,
                new ParameterizedTypeReference<ApiResponse<AppointmentView>>() {},
                correlationId,
                authorizationHeader,
                scenario,
                "rescheduleAppointment",
                appointmentId);
    }

    private <T> Optional<T> get(String path,
                                ParameterizedTypeReference<ApiResponse<T>> typeReference,
                                String correlationId,
                                String authorizationHeader,
                                String scenario) {
        try {
            ApiResponse<T> response = restClient.get()
                    .uri(path)
                    .headers(headers -> addRequiredHeaders(headers, correlationId, authorizationHeader, scenario))
                    .retrieve()
                    .body(typeReference);
            return extractData(response, path);
        } catch (Exception ex) {
            log.debug("Appointment upstream GET fallback for {}: {}", path, ex.getMessage());
            return Optional.empty();
        }
    }

    private <T, B> Optional<T> post(String path,
                                    B body,
                                    ParameterizedTypeReference<ApiResponse<T>> typeReference,
                                    String correlationId,
                                    String authorizationHeader,
                                    String scenario,
                                    String operation,
                                    Object... uriVariables) {
        try {
            ApiResponse<T> response = restClient.post()
                    .uri(path, uriVariables)
                    .headers(headers -> addRequiredHeaders(headers, correlationId, authorizationHeader, scenario))
                    .body(body)
                    .retrieve()
                    .body(typeReference);
            return extractData(response, operation);
        } catch (Exception ex) {
            log.debug("Appointment upstream POST fallback for {}: {}", operation, ex.getMessage());
            return Optional.empty();
        }
    }

    private void addRequiredHeaders(HttpHeaders headers,
                                    String correlationId,
                                    String authorizationHeader,
                                    String scenario) {
        String authValue = authorizationHeader;
        if (authValue == null || authValue.isBlank()) {
            authValue = "Bearer " + properties.getFallbackBearerToken();
        }

        headers.set(properties.getAuthorizationHeaderName(), authValue);
        headers.set(properties.getClientHeaderName(), properties.getClientHeaderValue());
        headers.set(properties.getCorrelationHeaderName(), correlationId);

        if (properties.isCloudRunAuthEnabled()) {
            cloudRunIdTokenService.serverlessAuthorizationHeaderValue(properties.getCloudRunAudience())
                    .ifPresent(value -> headers.set(properties.getCloudRunAuthorizationHeaderName(), value));
        }

        if (scenario != null && !scenario.isBlank()) {
            headers.set(properties.getScenarioHeaderName(), scenario);
        }
    }

    private <T> Optional<T> extractData(ApiResponse<T> response, String operation) {
        if (response == null || !response.success() || response.data() == null) {
            log.debug("Appointment upstream returned no usable data for {}", operation);
            return Optional.empty();
        }
        return Optional.of(response.data());
    }
}
