package com.acme.banking.demoaccount;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Echoes selected request headers into JSON responses when debug capture is enabled.
 *
 * <p>This transformer is global but inert unless the request includes
 * {@code X-Debug-Echo-Headers: true}. When enabled, the response body gains a
 * {@code _debug.captured_headers} block containing normalized values for the
 * CES attribution headers used by the banking agent.</p>
 */
public class HeaderCaptureResponseTransformer implements ResponseTransformerV2 {

    static final String DEBUG_ECHO_HEADER = "X-Debug-Echo-Headers";
    static final String AGENT_ID_HEADER = "X-Agent-Id";
    static final String TOOL_ID_HEADER = "X-Tool-Id";
    private static final Logger logger = LoggerFactory.getLogger(HeaderCaptureResponseTransformer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Response transform(Response response, ServeEvent serveEvent) {
        Request request = serveEvent.getRequest();
        if (!isDebugCaptureEnabled(request)) {
            return response;
        }

        String contentType = response.getHeaders().getHeader("Content-Type").firstValue();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            return response;
        }

        String body = response.getBodyAsString();
        if (body == null || body.isBlank()) {
            return response;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (!(root instanceof ObjectNode objectNode)) {
                return response;
            }

            ObjectNode debugNode = objectNode.with("_debug");
            ObjectNode capturedHeaders = debugNode.with("captured_headers");
            capturedHeaders.put("x_agent_id", request.getHeader(AGENT_ID_HEADER));
            capturedHeaders.put("x_tool_id", request.getHeader(TOOL_ID_HEADER));
            capturedHeaders.put("x_debug_echo_headers", request.getHeader(DEBUG_ECHO_HEADER));

            byte[] transformedBody = OBJECT_MAPPER.writeValueAsBytes(objectNode);
            return Response.Builder.like(response)
                    .but()
                    .body(transformedBody)
                    .build();
        } catch (Exception exc) {
            logger.warn("Failed to append debug header capture for {}", request.getUrl(), exc);
            return response;
        }
    }

    private boolean isDebugCaptureEnabled(Request request) {
        String value = request.getHeader(DEBUG_ECHO_HEADER);
        return value != null && value.equalsIgnoreCase("true");
    }

    @Override
    public boolean applyGlobally() {
        return true;
    }

    @Override
    public String getName() {
        return "header-capture-response";
    }
}
