package com.voicebanking.bfa.appointment;

import com.voicebanking.bfa.appointment.AppointmentEnums.AdvisorMode;
import com.voicebanking.bfa.appointment.AppointmentEnums.ConsultationChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.FallbackSuggestionType;
import com.voicebanking.bfa.appointment.AppointmentEnums.LocationType;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentBranchSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentLocationOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentSlotOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentSlotSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AvailableDay;
import com.voicebanking.bfa.appointment.AppointmentResponses.FallbackSuggestion;
import com.voicebanking.bfa.location.Branch;
import com.voicebanking.bfa.location.BranchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the advisory appointment controller.
 *
 * @author Codex
 * @since 2026-03-15
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppointmentControllerTest {

    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_TOKEN = "Bearer advisory-test-user";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private BranchRepository branchRepository;

    @MockBean
    private AppointmentMockServerClient appointmentMockServerClient;

    @BeforeEach
    void setUp() {
        appointmentRepository.resetRuntimeState();
        reset(appointmentMockServerClient);

        given(appointmentMockServerClient.fetchTaxonomy(any(), any(), any()))
                .willReturn(Optional.empty());
        given(appointmentMockServerClient.searchServices(any(), any(), any(), any()))
                .willReturn(Optional.empty());
        given(appointmentMockServerClient.searchEligibility(any(), any(), any(), any()))
                .willReturn(Optional.empty());
        given(appointmentMockServerClient.fetchSlots(any(), any(), any(), any()))
                .willReturn(Optional.empty());
    }

    @Test
    void appointmentEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/appointment-taxonomy")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchAppointmentBranchesUsesUpstreamEligibilityAndEnrichesBranchMetadata() throws Exception {
        Branch branch = branchRepository.findById("20286143").orElseThrow();
        AppointmentLocationOption upstreamLocation = new AppointmentLocationOption(
                "20286143",
                LocationType.BRANCH,
                "20286143",
                "Upstream advisory center",
                null,
                "Berlin",
                null,
                null,
                null,
                1.2,
                List.of(ConsultationChannel.BRANCH),
                List.of(AdvisorMode.INTERNAL, AdvisorMode.PRIVATE_BANKING),
                LocalDate.of(2030, 6, 18),
                "Supports investment consultations"
        );

        given(appointmentMockServerClient.searchEligibility(any(), any(), any(), any()))
                .willReturn(Optional.of(new AppointmentBranchSearchResponse(
                        List.of(upstreamLocation),
                        1,
                        1,
                        List.of()
                )));

        mockMvc.perform(get("/api/v1/appointment-branches")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .param("entryPath", "PRODUCT_CONSULTATION")
                        .param("consultationChannel", "BRANCH")
                        .param("topicCode", "IN")
                        .param("city", "Berlin")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.locations[0].locationId").value("20286143"))
                .andExpect(jsonPath("$.data.locations[0].name").value(branch.name()))
                .andExpect(jsonPath("$.data.locations[0].address").value(branch.address()))
                .andExpect(jsonPath("$.data.locations[0].postalCode").value(branch.postalCode()))
                .andExpect(jsonPath("$.data.locations[0].distanceKm").value(1.2))
                .andExpect(jsonPath("$.data.locations[0].supportedChannels[0]").value("BRANCH"));
    }

        @Test
        void searchAppointmentServicesRecognizesInvestmentAdviceFallback() throws Exception {
                given(appointmentMockServerClient.searchServices(any(), any(), any(), any()))
                        .willReturn(Optional.of(new AppointmentResponses.AppointmentServiceSearchResponse(
                                List.of(),
                                List.of("Try describing the request in a few more words.")
                        )));

                mockMvc.perform(get("/api/v1/appointment-service-search")
                                                .header(AUTH_HEADER, AUTH_TOKEN)
                                                .param("entryPath", "PRODUCT_CONSULTATION")
                                                .param("query", "investment advice")
                                                .accept(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true))
                                .andExpect(jsonPath("$.data.matches[0].serviceCode").value("INVESTMENT_CONSULTATION"))
                                .andExpect(jsonPath("$.data.matches[0].label").value("Investment consultation"))
                                .andExpect(jsonPath("$.data.matches[0].topicCode").value("IN"))
                                .andExpect(jsonPath("$.data.matches[0].requiresComment").value(false));
        }

    @Test
    void getAppointmentSlotsReturnsStructuredNoAvailabilityFallback() throws Exception {
        given(appointmentMockServerClient.fetchSlots(any(), any(), any(), any()))
                .willReturn(Optional.of(new AppointmentSlotSearchResponse(
                        "20286143",
                        "Europe/Berlin",
                        List.of(new AvailableDay(
                                LocalDate.of(2030, 6, 20),
                                "THURSDAY, 2030-06-20",
                                0,
                                null
                        )),
                        List.of(),
                        List.of(
                                new FallbackSuggestion(
                                        FallbackSuggestionType.TRY_ANOTHER_DAY,
                                        "Try another day with available slots."
                                ),
                                new FallbackSuggestion(
                                        FallbackSuggestionType.TRY_ANOTHER_LOCATION,
                                        "Try another eligible location."
                                )
                        )
                )));

        mockMvc.perform(get("/api/v1/appointment-slots")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .param("entryPath", "PRODUCT_CONSULTATION")
                        .param("consultationChannel", "BRANCH")
                        .param("locationId", "20286143")
                        .param("topicCode", "IN")
                        .param("selectedDay", "2030-06-20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.slots").isEmpty())
                .andExpect(jsonPath("$.data.fallbackSuggestions[0].type").value("TRY_ANOTHER_DAY"))
                .andExpect(jsonPath("$.data.fallbackSuggestions[1].type").value("TRY_ANOTHER_LOCATION"));
    }

    @Test
    void createRescheduleAndCancelAppointmentUseDeterministicRuntimeOverlay() throws Exception {
        given(appointmentMockServerClient.fetchSlots(any(), any(), any(), any()))
                .willAnswer(invocation -> {
                    AppointmentRequests.AppointmentSlotSearchRequest request = invocation.getArgument(0);
                    LocalDate selectedDay = request.selectedDay();
                    if (LocalDate.of(2030, 6, 19).equals(selectedDay)) {
                        return Optional.of(branchSlotsForDay(
                                LocalDate.of(2030, 6, 19),
                                "SLOT-BRANCH-20286143-20300619-1100",
                                LocalDateTime.of(2030, 6, 19, 11, 0),
                                "Anna Becker"
                        ));
                    }
                    return Optional.of(branchSlotsForDay(
                            LocalDate.of(2030, 6, 18),
                            "SLOT-BRANCH-20286143-20300618-0930",
                            LocalDateTime.of(2030, 6, 18, 9, 30),
                            "Anna Becker"
                    ));
                });

        String createBody = """
                {
                  "entryPath": "PRODUCT_CONSULTATION",
                  "consultationChannel": "BRANCH",
                  "topicCode": "IN",
                  "locationId": "20286143",
                  "selectedDay": "2030-06-18",
                  "selectedTimeSlotId": "SLOT-BRANCH-20286143-20300618-0930",
                  "customer": {
                    "salutation": "FRAU",
                    "firstName": "Maria",
                    "lastName": "Musterfrau",
                    "email": "maria@example.com",
                    "phone": "+491701234567",
                    "isExistingCustomer": true
                  },
                  "summaryConfirmed": true
                }
                """;

        mockMvc.perform(post("/api/v1/appointments")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appointment.appointmentId").value("APT-001001"))
                .andExpect(jsonPath("$.data.appointmentAccessToken").value("aat-00017017"))
                .andExpect(jsonPath("$.data.appointment.status").value("CONFIRMED"));

        mockMvc.perform(post("/api/v1/appointments")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NO_SLOTS_AVAILABLE"));

        mockMvc.perform(get("/api/v1/appointments/APT-001001")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .param("appointmentAccessToken", "aat-00017017")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        String rescheduleBody = """
                {
                  "appointmentAccessToken": "aat-00017017",
                  "selectedDay": "2030-06-19",
                  "selectedTimeSlotId": "SLOT-BRANCH-20286143-20300619-1100",
                  "summaryConfirmed": true
                }
                """;

        mockMvc.perform(post("/api/v1/appointments/APT-001001/reschedule")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rescheduleBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESCHEDULED"))
                .andExpect(jsonPath("$.data.scheduledStart").value("2030-06-19T11:00:00"));

        mockMvc.perform(post("/api/v1/appointments/APT-001001/cancel")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appointmentAccessToken": "aat-00017017",
                                  "summaryConfirmed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/appointments/APT-001001")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .param("appointmentAccessToken", "aat-00017017")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    private AppointmentSlotSearchResponse branchSlotsForDay(LocalDate day,
                                                            String slotId,
                                                            LocalDateTime start,
                                                            String advisorName) {
        return new AppointmentSlotSearchResponse(
                "20286143",
                "Europe/Berlin",
                List.of(new AvailableDay(day, day.getDayOfWeek().name() + ", " + day, 1, start.toLocalTime().toString())),
                List.of(new AppointmentSlotOption(
                        slotId,
                        start,
                        start.plusMinutes(45),
                        "ADV-BRANCH-1",
                        advisorName,
                        AdvisorMode.INTERNAL
                )),
                List.of()
        );
    }
}
