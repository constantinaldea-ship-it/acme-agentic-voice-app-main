package com.voicebanking.agent.text.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public record ResponseContext(
    ResponseType type,
    String templateId,
    Map<String, Object> data,
    VoiceOptions voiceOptions
) {
    public ResponseContext {
        if (type == null) type = ResponseType.INFORMATION;
        if (data == null) data = Collections.emptyMap();
        else data = Collections.unmodifiableMap(new HashMap<>(data));
        if (voiceOptions == null) voiceOptions = VoiceOptions.GERMAN_DEFAULT;
    }
    
    public static ResponseContext of(ResponseType type, Map<String, Object> data) {
        return new ResponseContext(type, null, data, VoiceOptions.GERMAN_DEFAULT);
    }
    
    public static ResponseContext of(ResponseType type, Map<String, Object> data, String language) {
        return new ResponseContext(type, null, data, VoiceOptions.forLanguage(language));
    }
    
    public String getEffectiveTemplateId() {
        if (templateId != null && templateId.isBlank() == false) return templateId;
        return type.getTemplatePrefix() + "_default";
    }
    
    public Object get(String key) { return data.get(key); }
    public String getString(String key) {
        Object v = data.get(key);
        return v != null ? v.toString() : "";
    }
    public Number getNumber(String key) {
        Object v = data.get(key);
        if (v instanceof Number) return (Number) v;
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); }
            catch (NumberFormatException e) { return null; }
        }
        return null;
    }
    public boolean hasKey(String key) { return data.containsKey(key); }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private ResponseType type = ResponseType.INFORMATION;
        private String templateId;
        private final Map<String, Object> data = new HashMap<>();
        private VoiceOptions voiceOptions = VoiceOptions.GERMAN_DEFAULT;
        
        public Builder type(ResponseType t) { this.type = t; return this; }
        public Builder templateId(String t) { this.templateId = t; return this; }
        public Builder data(String k, Object v) { this.data.put(k, v); return this; }
        public Builder data(Map<String, Object> d) { this.data.putAll(d); return this; }
        public Builder voiceOptions(VoiceOptions v) { this.voiceOptions = v; return this; }
        public Builder language(String l) { this.voiceOptions = VoiceOptions.forLanguage(l); return this; }
        public Builder german() { this.voiceOptions = VoiceOptions.GERMAN_DEFAULT; return this; }
        public Builder english() { this.voiceOptions = VoiceOptions.ENGLISH_DEFAULT; return this; }
        public ResponseContext build() { return new ResponseContext(type, templateId, data, voiceOptions); }
    }
}
