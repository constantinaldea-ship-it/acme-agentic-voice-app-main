package com.voicebanking.agent.text.template;

import com.voicebanking.agent.text.domain.ResponseType;
import java.util.List;
import java.util.Map;

/**
 * Represents a response template with text and optional SSML.
 */
public record ResponseTemplate(
    String id,
    ResponseType type,
    String text,
    String ssml,
    List<String> requiredVariables
) {

    public static ResponseTemplate textOnly(String id, ResponseType type, String text) {
        return new ResponseTemplate(id, type, text, null, List.of());
    }

    public static ResponseTemplate withSsml(String id, ResponseType type, String text, String ssml) {
        return new ResponseTemplate(id, type, text, ssml, List.of());
    }

    public boolean hasSsml() {
        return ssml != null && ssml.isBlank() == false;
    }

    public String render(Map<String, Object> data) {
        return renderTemplate(text, data);
    }

    public String renderSsml(Map<String, Object> data) {
        if (hasSsml() == false) return render(data);
        return renderTemplate(ssml, data);
    }

    private String renderTemplate(String template, Map<String, Object> data) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
