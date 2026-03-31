package com.voicebanking.agent.text;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.text.domain.*;
import com.voicebanking.agent.text.formatter.*;
import com.voicebanking.agent.text.template.TemplateEngine;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.Temporal;
import java.util.*;

/**
 * Agent responsible for generating voice-optimized text responses.
 * Provides tools for response generation, voice formatting, and data formatting.
 */
@Component
public class TextGeneratorAgent implements Agent {

    private static final String AGENT_ID = "text-generator";
    private static final String TOOL_GENERATE_RESPONSE = "generateResponse";
    private static final String TOOL_FORMAT_FOR_VOICE = "formatForVoice";
    private static final String TOOL_FORMAT_CURRENCY = "formatCurrency";
    private static final String TOOL_FORMAT_DATE = "formatDate";
    private static final String TOOL_FORMAT_ACCOUNT_NUMBER = "formatAccountNumber";

    private static final List<String> TOOL_IDS = List.of(
        TOOL_GENERATE_RESPONSE,
        TOOL_FORMAT_FOR_VOICE,
        TOOL_FORMAT_CURRENCY,
        TOOL_FORMAT_DATE,
        TOOL_FORMAT_ACCOUNT_NUMBER
    );

    private final TemplateEngine templateEngine;
    private final CurrencyFormatter currencyFormatter;
    private final DateFormatter dateFormatter;
    private final AccountFormatter accountFormatter;
    private final NumberFormatter numberFormatter;
    private final BrandEnforcer brandEnforcer;

    public TextGeneratorAgent(
            TemplateEngine templateEngine,
            CurrencyFormatter currencyFormatter,
            DateFormatter dateFormatter,
            AccountFormatter accountFormatter,
            NumberFormatter numberFormatter,
            BrandEnforcer brandEnforcer) {
        this.templateEngine = templateEngine;
        this.currencyFormatter = currencyFormatter;
        this.dateFormatter = dateFormatter;
        this.accountFormatter = accountFormatter;
        this.numberFormatter = numberFormatter;
        this.brandEnforcer = brandEnforcer;
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getDescription() {
        return "Generates voice-optimized text responses with German and English localization";
    }

    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeTool(String toolId, Map<String, Object> parameters) {
        Object result = switch (toolId) {
            case TOOL_GENERATE_RESPONSE -> executeGenerateResponse(parameters);
            case TOOL_FORMAT_FOR_VOICE -> executeFormatForVoice(parameters);
            case TOOL_FORMAT_CURRENCY -> executeFormatCurrency(parameters);
            case TOOL_FORMAT_DATE -> executeFormatDate(parameters);
            case TOOL_FORMAT_ACCOUNT_NUMBER -> executeFormatAccountNumber(parameters);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolId);
        };
        return Map.of("success", true, "result", result);
    }

    private FormattedResponse executeGenerateResponse(Map<String, Object> parameters) {
        ResponseType type = ResponseType.valueOf((String) parameters.getOrDefault("type", "INFORMATION"));
        String templateId = (String) parameters.get("templateId");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) parameters.getOrDefault("data", Map.of());
        VoiceOptions options = getVoiceOptions(parameters);

        ResponseContext context = ResponseContext.builder()
            .type(type)
            .templateId(templateId)
            .data(data)
            .voiceOptions(options)
            .build();

        FormattedResponse response = templateEngine.render(context);

        // Check brand compliance
        BrandEnforcer.BrandCheckResult brandCheck = brandEnforcer.check(response.text());
        if (brandCheck.compliant() == false) {
            return response.withBrandWarnings(brandCheck.violations());
        }

        return response;
    }

    private String executeFormatForVoice(Map<String, Object> parameters) {
        String text = (String) parameters.get("text");
        VoiceOptions options = getVoiceOptions(parameters);

        // Apply voice formatting rules
        return formatTextForVoice(text, options);
    }

    private String executeFormatCurrency(Map<String, Object> parameters) {
        Object amountObj = parameters.get("amount");
        BigDecimal amount = amountObj instanceof BigDecimal bd ? bd : new BigDecimal(amountObj.toString());
        VoiceOptions options = getVoiceOptions(parameters);

        return options.ssmlEnabled()
            ? currencyFormatter.formatSsml(amount, options)
            : currencyFormatter.format(amount, options);
    }

    private String executeFormatDate(Map<String, Object> parameters) {
        Object dateObj = parameters.get("date");
        Temporal date = dateObj instanceof Temporal t ? t : LocalDate.parse(dateObj.toString());
        VoiceOptions options = getVoiceOptions(parameters);

        return options.ssmlEnabled()
            ? dateFormatter.formatSsml(date, options)
            : dateFormatter.format(date, options);
    }

    private String executeFormatAccountNumber(Map<String, Object> parameters) {
        String accountNumber = (String) parameters.get("accountNumber");
        VoiceOptions options = getVoiceOptions(parameters);

        return options.ssmlEnabled()
            ? accountFormatter.formatSsml(accountNumber, options)
            : accountFormatter.format(accountNumber, options);
    }

    private VoiceOptions getVoiceOptions(Map<String, Object> parameters) {
        String language = (String) parameters.getOrDefault("language", "de");
        boolean ssmlEnabled = Boolean.parseBoolean(parameters.getOrDefault("ssmlEnabled", "false").toString());
        boolean conversational = Boolean.parseBoolean(parameters.getOrDefault("conversationalNumbers", "true").toString());
        boolean relativeDates = Boolean.parseBoolean(parameters.getOrDefault("useRelativeDates", "true").toString());

        Locale locale = "en".equals(language) ? Locale.ENGLISH : Locale.GERMAN;
        return new VoiceOptions(locale, ssmlEnabled, 1.0, relativeDates, conversational, 15);
    }

    private String formatTextForVoice(String text, VoiceOptions options) {
        if (text == null) return "";
        // Apply basic voice-friendly formatting
        String result = text;
        // Expand common abbreviations
        result = result.replace("&", " and ");
        result = result.replace("€", options.locale().getLanguage().equals("de") ? " Euro " : " euros ");
        return result.trim();
    }
}
