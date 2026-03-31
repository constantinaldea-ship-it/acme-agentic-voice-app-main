package com.acme.banking.demoaccount;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilterV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WireMock request filter that validates API key on all incoming requests.
 * The expected key is read from the MOCK_API_KEY environment variable.
 * If the variable is not set, the filter is disabled (all requests pass).
 */
public class ApiKeyRequestFilter implements StubRequestFilterV2 {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyRequestFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final String expectedApiKey;

    public ApiKeyRequestFilter(String expectedApiKey) {
        this.expectedApiKey = expectedApiKey;
    }

    @Override
    public RequestFilterAction filter(Request request, ServeEvent serveEvent) {
        // If no API key configured, let everything through
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            return RequestFilterAction.continueWith(request);
        }

        String providedKey = request.getHeader(API_KEY_HEADER);

        if (providedKey == null || !expectedApiKey.equals(providedKey)) {
            logger.warn("Rejected request to {} — invalid or missing API key", request.getUrl());
            return RequestFilterAction.stopWith(
                    ResponseDefinitionBuilder.responseDefinition()
                            .withStatus(403)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"error\":\"forbidden\",\"error_description\":\"Invalid or missing X-API-Key header.\"}")
                            .build()
            );
        }

        return RequestFilterAction.continueWith(request);
    }

    @Override
    public String getName() {
        return "api-key-filter";
    }
}
