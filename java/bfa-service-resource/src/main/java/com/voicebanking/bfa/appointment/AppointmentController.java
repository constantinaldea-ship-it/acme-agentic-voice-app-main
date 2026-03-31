package com.voicebanking.bfa.appointment;

import com.voicebanking.bfa.annotation.Audited;
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
import com.voicebanking.bfa.filter.BfaSecurityFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for advisory appointment booking and lifecycle operations.
 *
 * @author Codex
 * @since 2026-03-15
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Advisory Appointment", description = "Advisory appointment booking and lifecycle")
public class AppointmentController {

    private static final String MOCK_SCENARIO_HEADER = "X-Mock-Scenario";

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/appointment-taxonomy")
    @Audited(operation = "GET_APPOINTMENT_TAXONOMY")
    @Operation(summary = "Get appointment taxonomy")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Taxonomy returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required")
    })
    public ResponseEntity<ApiResponse<AppointmentTaxonomyResponse>> getAppointmentTaxonomy(
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = MOCK_SCENARIO_HEADER, required = false) String scenario,
            HttpServletRequest httpRequest) {
        String correlationId = correlationId(httpRequest);
        return toResponse(appointmentService.getTaxonomy(correlationId, authorizationHeader, scenario), correlationId);
    }

    @GetMapping("/appointment-service-search")
    @Audited(operation = "SEARCH_APPOINTMENT_SERVICES")
    @Operation(summary = "Search appointment services")
    public ResponseEntity<ApiResponse<AppointmentServiceSearchResponse>> searchAppointmentServices(
            @ParameterObject AppointmentServiceSearchRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = MOCK_SCENARIO_HEADER, required = false) String scenario,
            HttpServletRequest httpRequest) {
        String correlationId = correlationId(httpRequest);
        return toResponse(appointmentService.searchServices(request, correlationId, authorizationHeader, scenario),
                correlationId);
    }

    @GetMapping("/appointment-branches")
    @Audited(operation = "SEARCH_APPOINTMENT_BRANCHES")
    @Operation(summary = "Search booking-eligible locations")
    public ResponseEntity<ApiResponse<AppointmentBranchSearchResponse>> searchAppointmentBranches(
            @ParameterObject AppointmentBranchSearchRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = MOCK_SCENARIO_HEADER, required = false) String scenario,
            HttpServletRequest httpRequest) {
        String correlationId = correlationId(httpRequest);
        return toResponse(
                appointmentService.searchBranches(request, correlationId, authorizationHeader, scenario),
                correlationId
        );
    }

    @GetMapping("/appointment-slots")
    @Audited(operation = "GET_APPOINTMENT_SLOTS", riskLevel = "MEDIUM")
    @Operation(summary = "Get available appointment slots")
    public ResponseEntity<ApiResponse<AppointmentSlotSearchResponse>> getAppointmentSlots(
            @ParameterObject AppointmentSlotSearchRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = MOCK_SCENARIO_HEADER, required = false) String scenario,
            HttpServletRequest httpRequest) {
        String correlationId = correlationId(httpRequest);
        return toResponse(
                appointmentService.getSlots(request, correlationId, authorizationHeader, scenario),
                correlationId
        );
    }

    @PostMapping("/appointments")
    @Audited(operation = "CREATE_APPOINTMENT", riskLevel = "MEDIUM")
    @Operation(summary = "Create appointment")
    public ResponseEntity<ApiResponse<CreateAppointmentResponse>> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = MOCK_SCENARIO_HEADER, required = false) String scenario,
            HttpServletRequest httpRequest) {
        String correlationId = correlationId(httpRequest);
        return toResponse(
                appointmentService.createAppointment(request, correlationId, authorizationHeader, scenario),
                correlationId
        );
    }

    @GetMapping("/appointments/{appointmentId}")
    @Audited(operation = "GET_APPOINTMENT", riskLevel = "MEDIUM")
    @Operation(summary = "Get appointment details")
    public ResponseEntity<ApiResponse<AppointmentView>> getAppointment(
            @PathVariable String appointmentId,
            @Parameter(hidden = true)
            @RequestHeader(name = "X-Appointment-Access-Token", required = false) String headerToken,
            @Parameter(required = true, description = "Opaque appointment access token")
            @org.springframework.web.bind.annotation.RequestParam(name = "appointmentAccessToken", required = false)
            String queryToken,
            HttpServletRequest httpRequest) {
        String correlationId = correlationId(httpRequest);
        String accessToken = queryToken != null ? queryToken : headerToken;
        return toResponse(appointmentService.getAppointment(appointmentId, accessToken), correlationId);
    }

    @PostMapping("/appointments/{appointmentId}/cancel")
    @Audited(operation = "CANCEL_APPOINTMENT", riskLevel = "MEDIUM")
    @Operation(summary = "Cancel appointment")
    public ResponseEntity<ApiResponse<AppointmentView>> cancelAppointment(
            @PathVariable String appointmentId,
            @Valid @RequestBody CancelAppointmentRequest request,
            HttpServletRequest httpRequest) {
        String correlationId = correlationId(httpRequest);
        return toResponse(appointmentService.cancelAppointment(appointmentId, request), correlationId);
    }

    @PostMapping("/appointments/{appointmentId}/reschedule")
    @Audited(operation = "RESCHEDULE_APPOINTMENT", riskLevel = "MEDIUM")
    @Operation(summary = "Reschedule appointment")
    public ResponseEntity<ApiResponse<AppointmentView>> rescheduleAppointment(
            @PathVariable String appointmentId,
            @Valid @RequestBody RescheduleAppointmentRequest request,
            @RequestHeader(name = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(name = MOCK_SCENARIO_HEADER, required = false) String scenario,
            HttpServletRequest httpRequest) {
        String correlationId = correlationId(httpRequest);
        return toResponse(
                appointmentService.rescheduleAppointment(
                        appointmentId,
                        request,
                        correlationId,
                        authorizationHeader,
                        scenario
                ),
                correlationId
        );
    }

    private <T> ResponseEntity<ApiResponse<T>> toResponse(AppointmentResult<T> result, String correlationId) {
        if (result.isSuccess()) {
            return ResponseEntity.status(result.status()).body(ApiResponse.success(result.data(), correlationId));
        }
        return ResponseEntity.status(result.status()).body(
                ApiResponse.error(result.errorCode(), result.errorMessage(), result.errorDetails(), correlationId)
        );
    }

    private String correlationId(HttpServletRequest httpRequest) {
        return (String) httpRequest.getAttribute(BfaSecurityFilter.ATTR_CORRELATION_ID);
    }
}
