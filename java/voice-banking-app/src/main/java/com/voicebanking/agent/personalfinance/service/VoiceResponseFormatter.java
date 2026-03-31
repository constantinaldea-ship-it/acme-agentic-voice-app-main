package com.voicebanking.agent.personalfinance.service;

import com.voicebanking.agent.personalfinance.domain.*;
import com.voicebanking.agent.text.domain.VoiceOptions;
import com.voicebanking.agent.text.formatter.CurrencyFormatter;
import com.voicebanking.agent.text.formatter.DateFormatter;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Voice response formatting for personal finance insights.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class VoiceResponseFormatter {
    private final CurrencyFormatter currencyFormatter;
    private final DateFormatter dateFormatter;

    public VoiceResponseFormatter(CurrencyFormatter currencyFormatter, DateFormatter dateFormatter) {
        this.currencyFormatter = currencyFormatter;
        this.dateFormatter = dateFormatter;
    }

    public String formatBreakdown(SpendingBreakdown breakdown, String language) {
        VoiceOptions options = VoiceOptions.forLanguage(language);
        boolean german = options.isGerman();

        List<SpendingCategorySummary> sorted = breakdown.categories().stream()
                .sorted(Comparator.comparing(SpendingCategorySummary::totalAmount).reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(german ? "Ihre Ausgaben im Zeitraum " : "Your spending for the period ");
        sb.append(formatDateRange(breakdown.periodStart(), breakdown.periodEnd(), options));
        sb.append(german ? ": " : ": ");

        if (sorted.isEmpty()) {
            sb.append(german ? "Es liegen keine Ausgaben vor." : "No spending activity was found.");
            return sb.toString();
        }

        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            SpendingCategorySummary summary = sorted.get(i);
            String amount = currencyFormatter.format(summary.totalAmount(), options.withLocale(localeForCurrency(summary.currency(), options))); 
            sb.append(summary.category().getDisplayName(language))
                    .append(" ")
                    .append(amount)
                    .append(i < Math.min(3, sorted.size()) - 1 ? ", " : ". ");
        }

        if (breakdown.totalsByCurrency().size() > 1) {
            sb.append(german
                    ? "Mehrere Waehrungen sind enthalten und wurden nicht umgerechnet."
                    : "Multiple currencies are included and not converted.");
        }

        return sb.toString();
    }

    public String formatTrend(SpendingTrend trend, String language) {
        VoiceOptions options = VoiceOptions.forLanguage(language);
        boolean german = options.isGerman();
        if (trend.points().isEmpty()) {
            return german ? "Es liegen keine Trenddaten vor." : "No trend data is available.";
        }

        TrendPoint latest = trend.points().get(trend.points().size() - 1);
        String amount = currencyFormatter.format(latest.totalSpending(), options.withLocale(localeForCurrency(latest.currency(), options)));
        String month = latest.month().getMonth().getDisplayName(java.time.format.TextStyle.FULL, options.locale());

        String changeText = latest.changeAmount() != null
                ? currencyFormatter.format(latest.changeAmount().abs(), options.withLocale(localeForCurrency(latest.currency(), options)))
                : "";

        if (german) {
            return "Im " + month + " lagen die Ausgaben bei " + amount + ". Aenderung zum Vormonat: " + changeText + ".";
        }
        return "In " + month + ", spending was " + amount + ". Change versus prior month: " + changeText + ".";
    }

    public String formatBudgetStatus(List<BudgetStatus> statuses, String language) {
        VoiceOptions options = VoiceOptions.forLanguage(language);
        boolean german = options.isGerman();
        if (statuses.isEmpty()) {
            return german ? "Es sind keine Budgets hinterlegt." : "No budgets are configured.";
        }

        BudgetStatus first = statuses.get(0);
        String spent = currencyFormatter.format(first.spent(), options.withLocale(localeForCurrency(first.budget().currency(), options)));
        String total = currencyFormatter.format(first.budget().amount(), options.withLocale(localeForCurrency(first.budget().currency(), options)));

        return german
                ? "Budget fuer " + first.budget().category().getDisplayName(language) + ": " + spent + " von " + total + "."
                : "Budget for " + first.budget().category().getDisplayName(language) + ": " + spent + " of " + total + ".";
    }

    public String formatCashFlow(CashFlowSummary summary, String language) {
        VoiceOptions options = VoiceOptions.forLanguage(language);
        boolean german = options.isGerman();

        if (summary.incomeByCurrency().isEmpty() && summary.expensesByCurrency().isEmpty()) {
            return german ? "Keine Cashflow-Daten verfuegbar." : "No cash flow data available.";
        }

        Map.Entry<String, BigDecimal> incomeEntry = summary.incomeByCurrency().entrySet().stream().findFirst().orElse(null);
        Map.Entry<String, BigDecimal> expenseEntry = summary.expensesByCurrency().entrySet().stream().findFirst().orElse(null);

        String income = incomeEntry != null
                ? currencyFormatter.format(incomeEntry.getValue(), options.withLocale(localeForCurrency(incomeEntry.getKey(), options)))
                : currencyFormatter.format(BigDecimal.ZERO, options);
        String expenses = expenseEntry != null
                ? currencyFormatter.format(expenseEntry.getValue(), options.withLocale(localeForCurrency(expenseEntry.getKey(), options)))
                : currencyFormatter.format(BigDecimal.ZERO, options);

        return german
                ? "Einnahmen: " + income + ", Ausgaben: " + expenses + "."
                : "Income: " + income + ", expenses: " + expenses + ".";
    }

    public String formatRecurring(List<RecurringTransaction> recurring, String language) {
        VoiceOptions options = VoiceOptions.forLanguage(language);
        boolean german = options.isGerman();

        if (recurring.isEmpty()) {
            return german ? "Keine wiederkehrenden Zahlungen gefunden." : "No recurring payments were found.";
        }

        RecurringTransaction first = recurring.get(0);
        String amount = currencyFormatter.format(first.averageAmount(), options.withLocale(localeForCurrency(first.currency(), options)));
        return german
                ? "Wiederkehrend: " + first.merchantName() + " etwa " + amount + " pro " + first.frequency() + "."
                : "Recurring: " + first.merchantName() + " about " + amount + " per " + first.frequency() + ".";
    }

    public String formatTopMerchants(List<Map<String, Object>> merchants, String language) {
        boolean german = VoiceOptions.forLanguage(language).isGerman();
        if (merchants.isEmpty()) {
            return german ? "Keine haeufigen Haendler gefunden." : "No top merchants found.";
        }
        Object merchant = merchants.get(0).get("merchantName");
        return german
                ? "Top-Haendler ist " + merchant + "."
                : "Top merchant is " + merchant + ".";
    }

    public String formatUnusual(List<UnusualActivity> unusual, String language) {
        boolean german = VoiceOptions.forLanguage(language).isGerman();
        if (unusual.isEmpty()) {
            return german ? "Keine ungewoehnlichen Ausgaben entdeckt." : "No unusual spending detected.";
        }
        UnusualActivity first = unusual.get(0);
        return german
                ? "Ungewoehnliche Ausgabe bei " + first.merchantName() + "."
                : "Unusual spending detected at " + first.merchantName() + ".";
    }

    private String formatDateRange(LocalDate start, LocalDate end, VoiceOptions options) {
        if (start == null || end == null) {
            return options.isGerman() ? "diesen Zeitraum" : "this period";
        }
        String startText = dateFormatter.format(start, options);
        String endText = dateFormatter.format(end, options);
        return options.isGerman() ? startText + " bis " + endText : startText + " to " + endText;
    }

    private Locale localeForCurrency(String currency, VoiceOptions options) {
        if (currency == null) {
            return options.locale();
        }
        if (currency.equalsIgnoreCase("EUR")) {
            return Locale.GERMANY;
        }
        if (currency.equalsIgnoreCase("USD")) {
            return Locale.US;
        }
        return options.locale();
    }
}
