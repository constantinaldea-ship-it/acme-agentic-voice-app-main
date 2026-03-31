package com.voicebanking.agent.text.domain;

import java.util.Locale;

/**
 * Voice Options Configuration
 * 
 * Configures voice output options including language, SSML support,
 * and TTS-specific settings.
 * 
 * @author Augment Agent
 * @since 2026-01-22
 */
public record VoiceOptions(
    Locale locale,
    boolean ssmlEnabled,
    double speakingRate,
    boolean useRelativeDates,
    boolean conversationalNumbers,
    int maxWordsPerSentence
) {
    
    public static final VoiceOptions GERMAN_DEFAULT = new VoiceOptions(
        Locale.GERMAN, false, 1.0, true, false, 15
    );
    
    public static final VoiceOptions ENGLISH_DEFAULT = new VoiceOptions(
        Locale.ENGLISH, false, 1.0, true, true, 15
    );
    
    public static final VoiceOptions GERMAN_SSML = new VoiceOptions(
        Locale.GERMAN, true, 1.0, true, false, 15
    );
    
    public static final VoiceOptions ENGLISH_SSML = new VoiceOptions(
        Locale.ENGLISH, true, 1.0, true, true, 15
    );
    
    public VoiceOptions {
        if (locale == null) locale = Locale.GERMAN;
        if (speakingRate < 0.5 || speakingRate > 2.0) speakingRate = 1.0;
        if (maxWordsPerSentence < 5 || maxWordsPerSentence > 50) maxWordsPerSentence = 15;
    }
    
    public static VoiceOptions forLanguage(String languageCode) {
        if (languageCode == null) return GERMAN_DEFAULT;
        return switch (languageCode.toLowerCase()) {
            case "en", "english" -> ENGLISH_DEFAULT;
            case "de", "german", "deutsch" -> GERMAN_DEFAULT;
            default -> GERMAN_DEFAULT;
        };
    }
    
    public VoiceOptions withSsml() {
        return new VoiceOptions(locale, true, speakingRate, useRelativeDates, conversationalNumbers, maxWordsPerSentence);
    }
    
    public VoiceOptions withLocale(Locale newLocale) {
        return new VoiceOptions(newLocale, ssmlEnabled, speakingRate, useRelativeDates, conversationalNumbers, maxWordsPerSentence);
    }
    
    public boolean isGerman() {
        return locale != null && "de".equalsIgnoreCase(locale.getLanguage());
    }
    
    public boolean isEnglish() {
        return locale != null && "en".equalsIgnoreCase(locale.getLanguage());
    }
}
