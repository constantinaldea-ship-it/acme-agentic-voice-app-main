package com.voicebanking.bfa.gateway.filter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Response PEP — runs <em>after</em> the controller writes its body.
 *
 * <p>Intercepts the serialised JSON response and masks any field whose
 * name appears in the configured masked-fields list (e.g. {@code accountNumber},
 * {@code iban}, {@code cardNumber}).  Masking replaces the value with
 * {@code "****<last4>"} to prevent PII leakage through the gateway.</p>
 *
 * <p>This filter operates at the byte level using a
 * {@link ContentCachingResponseWrapper} so it is transparent to controllers
 * and adapters — they can write normal POJOs and the filter handles
 * sanitisation centrally.</p>
 *
 * @author Copilot
 * @since 2026-01-17
 */
@Component
@Order(2)
public class ResponsePepFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ResponsePepFilter.class);

    private final boolean enabled;
    private final Set<String> maskedFields;
    private final ObjectMapper objectMapper;

    public ResponsePepFilter(
            @Value("${bfa.gateway.response-pep.enabled:true}") boolean enabled,
            @Value("${bfa.gateway.response-pep.masked-fields:accountNumber,iban,cardNumber}") String maskedFieldsCsv,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.maskedFields = Stream.of(maskedFieldsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
        log.info("ResponsePepFilter initialised — enabled={}, maskedFields={}", enabled, maskedFields);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        filterChain.doFilter(request, wrappedResponse);

        // Only mask JSON responses from our API path
        String contentType = wrappedResponse.getContentType();
        if (contentType != null && contentType.contains("application/json")
                && request.getRequestURI().startsWith("/api/")) {

            byte[] original = wrappedResponse.getContentAsByteArray();
            if (original.length > 0) {
                try {
                    JsonNode tree = objectMapper.readTree(original);
                    int masked = maskFields(tree);
                    if (masked > 0) {
                        String correlationId = (String) request.getAttribute(EdgePepFilter.ATTR_CORRELATION_ID);
                        log.debug("[{}] Response PEP masked {} field(s)", correlationId, masked);
                    }
                    wrappedResponse.resetBuffer();
                    wrappedResponse.getOutputStream().write(
                            objectMapper.writeValueAsBytes(tree));
                } catch (Exception e) {
                    // If parsing fails, write original bytes untouched
                    log.warn("Response PEP could not parse JSON for masking: {}", e.getMessage());
                    wrappedResponse.resetBuffer();
                    wrappedResponse.getOutputStream().write(original);
                }
            }
        }
        wrappedResponse.copyBodyToResponse();
    }

    /**
     * Recursively walk the JSON tree and mask matching field values.
     *
     * @return number of fields masked
     */
    private int maskFields(JsonNode node) {
        int count = 0;
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (maskedFields.contains(entry.getKey()) && entry.getValue().isTextual()) {
                    String raw = entry.getValue().asText();
                    obj.set(entry.getKey(), new TextNode(mask(raw)));
                    count++;
                } else {
                    count += maskFields(entry.getValue());
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                count += maskFields(child);
            }
        }
        return count;
    }

    /**
     * Replace all but the last 4 characters with asterisks.
     */
    static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
