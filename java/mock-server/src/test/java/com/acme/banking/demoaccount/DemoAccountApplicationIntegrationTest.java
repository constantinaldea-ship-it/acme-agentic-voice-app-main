package com.acme.banking.demoaccount;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.*;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for the Demo Account Mock Server.
 * <p>
 * These tests verify that the WireMock server correctly serves
 * the mock responses for the personal-data endpoint.
 * </p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoAccountApplicationIntegrationTest {

    private static final int WIREMOCK_PORT = 8089;
    private static final String BASE_URL = "http://localhost:" + WIREMOCK_PORT;
    private static final String PERSONAL_DATA_ENDPOINT = "/customers/{partnerId}/personal-data";
    private static final String PARTNER_ID = "1234567890";
    private static final String HEADER_DEUBA_CLIENT_ID = "deuba-client-id";
    private static final String HEADER_DB_ID = "DB-ID";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_AGENT_ID = "X-Agent-Id";
    private static final String HEADER_TOOL_ID = "X-Tool-Id";
    private static final String HEADER_DEBUG_ECHO = "X-Debug-Echo-Headers";
    private static final String AUTH_TOKEN = "Bearer mock-authz-token";

    private WireMockServer wireMockServer;

    @BeforeAll
    void setUp() {
        WireMockConfiguration config = WireMockConfiguration.options()
                .port(WIREMOCK_PORT)
                .usingFilesUnderClasspath("wiremock")
                .globalTemplating(true)
                .extensions(new HeaderCaptureResponseTransformer());

        wireMockServer = new WireMockServer(config);
        wireMockServer.start();

        RestAssured.baseURI = BASE_URL;
    }

    @AfterAll
    void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("Should echo CES attribution headers when debug capture is enabled")
    void shouldEchoCesAttributionHeadersWhenDebugCaptureIsEnabled() {
        authorizedPersonalDataRequest("pb-banking")
                .header(HEADER_AGENT_ID, "customer_details_agent")
                .header(HEADER_TOOL_ID, "get_customer_details")
                .header(HEADER_DEBUG_ECHO, "true")
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("_debug.captured_headers.x_agent_id", equalTo("customer_details_agent"))
                .body("_debug.captured_headers.x_tool_id", equalTo("get_customer_details"))
                .body("_debug.captured_headers.x_debug_echo_headers", equalTo("true"));
    }

    @Test
    @DisplayName("Should return personal data with valid deuba-client-id header containing 'pb-banking'")
    void shouldReturnPersonalDataWithPbBankingHeader() {
        authorizedPersonalDataRequest("pb-banking")
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("firstname", equalTo("Maria"))
                .body("lastname", equalTo("Musterfrau"))
                .body("nationality", equalTo("DEU"))
                .body("id", equalTo(1234567890))
                .body("fullName", equalTo("Dr. Maria Musterfrau"));
    }

    @Test
    @DisplayName("Should return personal data with valid deuba-client-id header containing 'mobile-banking'")
    void shouldReturnPersonalDataWithMobileBankingHeader() {
        authorizedPersonalDataRequest("mobile-banking")
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("firstname", equalTo("Maria"))
                .body("nationality", equalTo("DEU"));
    }

    @Test
    @DisplayName("Should return personal data with valid deuba-client-id header containing 'app-banking'")
    void shouldReturnPersonalDataWithAppBankingHeader() {
        authorizedPersonalDataRequest("app-banking")
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("firstname", equalTo("Maria"))
                .body("nationality", equalTo("DEU"));
    }

    @Test
    @DisplayName("Should return personal data with correct address information")
    void shouldReturnCorrectAddressInformation() {
        authorizedPersonalDataRequest("pb-banking")
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(200)
                .body("registrationAddress.street", equalTo("Friedrichstraße"))
                .body("registrationAddress.streetNumber", equalTo("45"))
                .body("registrationAddress.postalCode", equalTo("10117"))
                .body("registrationAddress.city", equalTo("Berlin"))
                .body("postalAddress.street", equalTo("Friedrichstraße"));
    }

    @Test
    @DisplayName("Should return personal data with correct contact information")
    void shouldReturnCorrectContactInformation() {
        authorizedPersonalDataRequest("pb-banking")
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(200)
                .body("emailAddress.address", equalTo("maria.musterfrau@mail.com"))
                .body("emailAddress.type", equalTo("PRIVATE"))
                .body("phoneNumbers.private.countryCode", equalTo("+49"))
                .body("phoneNumbers.private.number", equalTo("987654321"))
                .body("phoneNumbers.mobile.number", equalTo("800000600"));
    }

    @Test
    @DisplayName("Should return personal data with correct personal details")
    void shouldReturnCorrectPersonalDetails() {
        authorizedPersonalDataRequest("pb-banking")
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(200)
                .body("dateOfBirth", equalTo("1988-05-15"))
                .body("maritalStatus", equalTo("single"))
                .body("gender", equalTo("FEMALE"));
    }

    @Test
    @DisplayName("Should work with different partner IDs due to regex pattern matching")
    void shouldWorkWithDifferentPartnerIds() {
        authorizedPersonalDataRequest("pb-banking")
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, "9999999999")
        .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("firstname", equalTo("Hans"));
    }

    @Test
    @DisplayName("Should return 404 when deuba-client-id header is missing")
    void shouldReturn404WhenHeaderIsMissing() {
        given()
                .header(HEADER_DB_ID, "acme-banking-db-01")
                .header(HEADER_AUTHORIZATION, AUTH_TOKEN)
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("Should return 404 when deuba-client-id header does not contain '-banking'")
    void shouldReturn404WhenHeaderValueIsInvalid() {
        given()
                .header(HEADER_DEUBA_CLIENT_ID, "invalid-header")
                .header(HEADER_DB_ID, "acme-banking-db-01")
                .header(HEADER_AUTHORIZATION, AUTH_TOKEN)
                .accept(ContentType.JSON)
        .when()
                .get(PERSONAL_DATA_ENDPOINT, PARTNER_ID)
        .then()
                .statusCode(404);
    }

    private io.restassured.specification.RequestSpecification authorizedPersonalDataRequest(String clientId) {
        return given()
                .header(HEADER_DEUBA_CLIENT_ID, clientId)
                .header(HEADER_DB_ID, "acme-banking-db-01")
                .header(HEADER_AUTHORIZATION, AUTH_TOKEN);
    }
}
