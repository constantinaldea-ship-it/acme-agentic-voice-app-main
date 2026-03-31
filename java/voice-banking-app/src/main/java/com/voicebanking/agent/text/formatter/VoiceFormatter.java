package com.voicebanking.agent.text.formatter;

import com.voicebanking.agent.text.domain.VoiceOptions;

/**
 * Interface for voice-optimized text formatting.
 */
public interface VoiceFormatter<T> {

    /**
     * Format value for voice output.
     */
    String format(T value, VoiceOptions options);

    /**
     * Format value with SSML markup.
     */
    String formatSsml(T value, VoiceOptions options);
}
