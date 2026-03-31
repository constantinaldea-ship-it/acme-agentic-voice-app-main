package com.voicebanking.agent.text.formatter;

import com.voicebanking.agent.text.domain.VoiceOptions;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Formats currency values for voice output.
 * Supports German and English locales.
 */
@Component
public class CurrencyFormatter implements VoiceFormatter<BigDecimal> {

    private final NumberFormatter numberFormatter;

    public CurrencyFormatter(NumberFormatter numberFormatter) {
        this.numberFormatter = numberFormatter;
    }

    @Override
    public String format(BigDecimal value, VoiceOptions options) {
        if (value == null) {
            return options.locale().getLanguage().equals("de") ? "null Euro" : "null euros";
        }

        boolean isGerman = options.locale().getLanguage().equals("de");

        if (options.conversationalNumbers()) {
            return formatConversational(value, isGerman);
        }

        return formatStandard(value, options.locale());
    }

    @Override
    public String formatSsml(BigDecimal value, VoiceOptions options) {
        String text = format(value, options);
        if (options.ssmlEnabled() == false) {
            return text;
        }
        return "<say-as interpret-as=\"currency\">" + text + "</say-as>";
    }

    private String formatStandard(BigDecimal value, Locale locale) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
        return currencyFormat.format(value);
    }

    private String formatConversational(BigDecimal value, boolean isGerman) {
        long euros = value.longValue();
        int cents = value.remainder(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).intValue();

        if (isGerman) {
            return formatGermanConversational(euros, cents);
        } else {
            return formatEnglishConversational(euros, cents);
        }
    }

    private String formatGermanConversational(long euros, int cents) {
        StringBuilder sb = new StringBuilder();
        sb.append(numberFormatter.toGermanWords(euros));
        sb.append(euros == 1 ? " Euro" : " Euro");

        if (cents > 0) {
            sb.append(" und ");
            sb.append(numberFormatter.toGermanWords(cents));
            sb.append(cents == 1 ? " Cent" : " Cent");
        }
        return sb.toString();
    }

    private String formatEnglishConversational(long euros, int cents) {
        StringBuilder sb = new StringBuilder();
        sb.append(numberFormatter.toEnglishWords(euros));
        sb.append(euros == 1 ? " euro" : " euros");

        if (cents > 0) {
            sb.append(" and ");
            sb.append(numberFormatter.toEnglishWords(cents));
            sb.append(cents == 1 ? " cent" : " cents");
        }
        return sb.toString();
    }
}
