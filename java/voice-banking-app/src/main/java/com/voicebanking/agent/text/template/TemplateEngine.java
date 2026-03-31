package com.voicebanking.agent.text.template;

import com.voicebanking.agent.text.domain.FormattedResponse;
import com.voicebanking.agent.text.domain.ResponseContext;
import com.voicebanking.agent.text.domain.ResponseType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Template engine for generating voice banking responses.
 * Loads templates and renders them with context data.
 */
@Component
public class TemplateEngine {

    private final Map<String, ResponseTemplate> templates = new HashMap<>();

    public TemplateEngine() {
        loadDefaultTemplates();
    }

    public FormattedResponse render(ResponseContext context) {
        String templateId = context.templateId();
        ResponseTemplate template = templates.get(templateId);

        if (template == null) {
            template = findTemplateByType(context.type());
        }

        if (template == null) {
            return FormattedResponse.error("Template not found: " + templateId);
        }

        String text = template.render(context.data());
        String ssml = context.voiceOptions().ssmlEnabled() ? template.renderSsml(context.data()) : null;
        String language = context.voiceOptions().locale().getLanguage();

        return FormattedResponse.of(text, ssml, context.type(), language);
    }

    public void registerTemplate(ResponseTemplate template) {
        templates.put(template.id(), template);
    }

    public Optional<ResponseTemplate> getTemplate(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    private ResponseTemplate findTemplateByType(ResponseType type) {
        return templates.values().stream()
            .filter(t -> t.type() == type)
            .findFirst()
            .orElse(null);
    }

    private void loadDefaultTemplates() {
        registerTemplate(ResponseTemplate.textOnly("balance-success", ResponseType.BALANCE,
                "Your account balance is ${balance}."));
        registerTemplate(ResponseTemplate.textOnly("error-generic", ResponseType.ERROR,
                "I'm sorry, I couldn't complete that request. ${message}"));
        registerTemplate(ResponseTemplate.textOnly("greeting", ResponseType.GREETING,
                "Welcome to Voice Banking. ${greeting}"));
        registerTemplate(ResponseTemplate.textOnly("balance.single.de", ResponseType.BALANCE,
                "Ihr aktueller Kontostand betraegt ${balance}."));
        registerTemplate(ResponseTemplate.textOnly("balance.single.en", ResponseType.BALANCE,
                "Your current balance is ${balance}."));
    }
}
