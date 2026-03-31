package com.voicebanking.agent.text.domain;

import java.time.Instant;
import java.util.List;

/**
 * Output model for generated text responses.
 * Contains both plain text and SSML formats.
 */
public record FormattedResponse(
    String text,
    String ssml,
    ResponseType type,
    String language,
    Instant generatedAt,
    boolean brandCompliant,
    List<String> brandWarnings
) {

    // Factory methods
    public static FormattedResponse text(String text, ResponseType type) {
        return new FormattedResponse(text, null, type, "de", Instant.now(), true, List.of());
    }

    public static FormattedResponse of(String text, String ssml, ResponseType type, String language) {
        return new FormattedResponse(text, ssml, type, language, Instant.now(), true, List.of());
    }

    public static FormattedResponse withSsml(String text, String ssml, ResponseType type, String language) {
        return new FormattedResponse(text, ssml, type, language, Instant.now(), true, List.of());
    }

    public static FormattedResponse error(String errorMessage) {
        return new FormattedResponse(errorMessage, null, ResponseType.ERROR, "de", Instant.now(), true, List.of());
    }

    // Utility methods
    public boolean hasSsml() {
        return ssml != null && ssml.isBlank() == false;
    }

    public String getEffectiveText() {
        return hasSsml() ? ssml : text;
    }

    public FormattedResponse withBrandWarnings(List<String> warnings) {
        return new FormattedResponse(text, ssml, type, language, generatedAt, false, warnings);
    }

    // Builder for complex construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String text;
        private String ssml;
        private ResponseType type = ResponseType.INFORMATION;
        private String language = "de";
        private boolean brandCompliant = true;
        private List<String> brandWarnings = List.of();

        public Builder text(String text) { this.text = text; return this; }
        public Builder ssml(String ssml) { this.ssml = ssml; return this; }
        public Builder type(ResponseType type) { this.type = type; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder brandCompliant(boolean brandCompliant) { this.brandCompliant = brandCompliant; return this; }
        public Builder brandWarnings(List<String> brandWarnings) { this.brandWarnings = brandWarnings; return this; }

        public FormattedResponse build() {
            return new FormattedResponse(text, ssml, type, language, Instant.now(), brandCompliant, brandWarnings);
        }
    }
}
