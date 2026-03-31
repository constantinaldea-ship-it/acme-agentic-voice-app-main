package com.acme.banking.demoaccount.appointment;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

/**
 * Integration tests for the advisory appointment WireMock fixtures.
 *
 * @author Codex
 * @since 2026-03-15
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdvisoryAppointmentWireMockIntegrationTest {

    private static final int WIREMOCK_PORT = 8091;
    private static final String BASE_URL = "http://localhost:" + WIREMOCK_PORT;
    private static final String AUTHORIZATION = "Authorization";
    private static final String CLIENT_HEADER = "X-BFA-Client";
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String SCENARIO_HEADER = "X-Mock-Scenario";
    private static final String APPOINTMENT_TOKEN = "aat-branch-0001";

    private WireMockServer wireMockServer;

    @BeforeAll
    void setUp() {
        WireMockConfiguration config = WireMockConfiguration.options()
                .port(WIREMOCK_PORT)
                .usingFilesUnderClasspath("wiremock")
                .globalTemplating(true);

        wireMockServer = new WireMockServer(config);
        wireMockServer.start();

        RestAssured.baseURI = BASE_URL;
        WireMock.configureFor("localhost", WIREMOCK_PORT);
    }

    @AfterAll
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should reject taxonomy requests without an Authorization header")
    void shouldRejectTaxonomyWithoutAuthorization() {
        given()
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-taxonomy-401")
                .accept(ContentType.JSON)
        .when()
                .get("/advisory-appointments/taxonomy")
        .then()
                .statusCode(401)
                .contentType(ContentType.JSON)
                .body("success", equalTo(false))
                .body("error.code", equalTo("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Should return taxonomy with the required upstream headers")
    void shouldReturnTaxonomyWithRequiredHeaders() {
        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-taxonomy-200")
                .accept(ContentType.JSON)
        .when()
                .get("/advisory-appointments/taxonomy")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.entryPaths", hasSize(2))
                .body("data.topics.size()", greaterThan(2))
                .body("data.consultationChannels[0].code", equalTo("BRANCH"));
    }

    @Test
    @DisplayName("Should return a deterministic no-availability response when forced by scenario header")
    void shouldReturnNoAvailabilityScenario() {
        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-slots-none")
                .header(SCENARIO_HEADER, "no-availability")
                .queryParam("entryPath", "PRODUCT_CONSULTATION")
                .queryParam("consultationChannel", "BRANCH")
                .queryParam("locationId", "20286143")
                .queryParam("topicCode", "IN")
                .queryParam("selectedDay", "2030-06-20")
                .accept(ContentType.JSON)
        .when()
                .get("/advisory-appointments/availability")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("success", equalTo(true))
                .body("data.slots", hasSize(0))
                .body("data.fallbackSuggestions", hasSize(2));
    }

    @Test
    @DisplayName("Should exercise the lifecycle scenario chain from booked to rescheduled to cancelled")
    void shouldExerciseLifecycleScenarioChain() {
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

        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-create")
                .header(SCENARIO_HEADER, "branch-lifecycle")
                .contentType(ContentType.JSON)
                .body(createBody)
        .when()
                .post("/advisory-appointments/lifecycle")
        .then()
                .statusCode(200)
                .body("data.appointment.appointmentId", equalTo("APT-BRANCH-0001"))
                .body("data.appointmentAccessToken", equalTo(APPOINTMENT_TOKEN))
                .body("data.appointment.status", equalTo("CONFIRMED"));

        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-get-booked")
                .queryParam("appointmentAccessToken", APPOINTMENT_TOKEN)
                .accept(ContentType.JSON)
        .when()
                .get("/advisory-appointments/lifecycle/APT-BRANCH-0001")
        .then()
                .statusCode(200)
                .body("data.status", equalTo("CONFIRMED"));

        String rescheduleBody = """
                {
                  "appointmentAccessToken": "aat-branch-0001",
                  "selectedDay": "2030-06-19",
                  "selectedTimeSlotId": "SLOT-BRANCH-20286143-20300619-1100",
                  "summaryConfirmed": true
                }
                """;

        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-reschedule")
                .contentType(ContentType.JSON)
                .body(rescheduleBody)
        .when()
                .post("/advisory-appointments/lifecycle/APT-BRANCH-0001/reschedule")
        .then()
                .statusCode(200)
                .body("data.status", equalTo("RESCHEDULED"))
                .body("data.scheduledStart", equalTo("2030-06-19T11:00:00"));

        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-get-rescheduled")
                .queryParam("appointmentAccessToken", APPOINTMENT_TOKEN)
                .accept(ContentType.JSON)
        .when()
                .get("/advisory-appointments/lifecycle/APT-BRANCH-0001")
        .then()
                .statusCode(200)
                .body("data.status", equalTo("RESCHEDULED"));

        String cancelBody = """
                {
                  "appointmentAccessToken": "aat-branch-0001",
                  "summaryConfirmed": true
                }
                """;

        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-cancel")
                .contentType(ContentType.JSON)
                .body(cancelBody)
        .when()
                .post("/advisory-appointments/lifecycle/APT-BRANCH-0001/cancel")
        .then()
                .statusCode(200)
                .body("data.status", equalTo("CANCELLED"));

        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-get-cancelled")
                .queryParam("appointmentAccessToken", APPOINTMENT_TOKEN)
                .accept(ContentType.JSON)
        .when()
                .get("/advisory-appointments/lifecycle/APT-BRANCH-0001")
        .then()
                .statusCode(200)
                .body("data.status", equalTo("CANCELLED"));

        given()
                .header(AUTHORIZATION, "Bearer test-service-token")
                .header(CLIENT_HEADER, "advisory-appointment-bff")
                .header(CORRELATION_HEADER, "corr-cancel-again")
                .contentType(ContentType.JSON)
                .body(cancelBody)
        .when()
                .post("/advisory-appointments/lifecycle/APT-BRANCH-0001/cancel")
        .then()
                .statusCode(409)
                .body("error.code", equalTo("CANCEL_WINDOW_CLOSED"));
    }
}
