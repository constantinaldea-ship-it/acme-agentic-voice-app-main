package com.voicebanking.agent.creditcard.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Credit card statement domain model.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class CreditCardStatement {
    private String statementId;
    private String cardId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal openingBalance;
    private BigDecimal closingBalance;
    private BigDecimal purchasesTotal;
    private BigDecimal paymentsTotal;
    private BigDecimal feesTotal;
    private BigDecimal interestTotal;
    private BigDecimal cashAdvancesTotal;
    private BigDecimal minimumPayment;
    private LocalDate paymentDueDate;
    private int transactionCount;
    private List<CreditCardTransaction> transactions = new ArrayList<>();
    private String currency;
    private LocalDate statementDate;

    private CreditCardStatement() {}

    public String getStatementId() { return statementId; }
    public String getCardId() { return cardId; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public BigDecimal getPurchasesTotal() { return purchasesTotal; }
    public BigDecimal getPaymentsTotal() { return paymentsTotal; }
    public BigDecimal getFeesTotal() { return feesTotal; }
    public BigDecimal getInterestTotal() { return interestTotal; }
    public BigDecimal getCashAdvancesTotal() { return cashAdvancesTotal; }
    public BigDecimal getMinimumPayment() { return minimumPayment; }
    public LocalDate getPaymentDueDate() { return paymentDueDate; }
    public int getTransactionCount() { return transactionCount; }
    public List<CreditCardTransaction> getTransactions() { return List.copyOf(transactions); }
    public String getCurrency() { return currency; }
    public LocalDate getStatementDate() { return statementDate; }

    /**
     * Get the largest transaction in this statement.
     */
    public CreditCardTransaction getLargestPurchase() {
        return transactions.stream()
                .filter(CreditCardTransaction::isPurchase)
                .max((a, b) -> a.getAmount().abs().compareTo(b.getAmount().abs()))
                .orElse(null);
    }

    /**
     * Get spending breakdown by category.
     */
    public Map<SpendingCategory, BigDecimal> getSpendingByCategory() {
        return transactions.stream()
                .filter(CreditCardTransaction::isPurchase)
                .collect(Collectors.groupingBy(
                        CreditCardTransaction::getCategory,
                        Collectors.reducing(BigDecimal.ZERO, 
                                t -> t.getAmount().abs(), 
                                BigDecimal::add)
                ));
    }

    /**
     * Format statement summary for voice response.
     */
    public String formatSummaryForVoice(CreditCard card) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Your %s statement for %s. ", 
                card.getFriendlyName(), formatPeriod()));
        sb.append(String.format("You had %d transactions totaling €%.0f in purchases. ", 
                transactionCount, purchasesTotal.doubleValue()));
        
        if (paymentsTotal.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("Payments received: €%.0f. ", paymentsTotal.doubleValue()));
        }
        
        sb.append(String.format("Your closing balance is €%.0f.", closingBalance.doubleValue()));
        
        CreditCardTransaction largest = getLargestPurchase();
        if (largest != null) {
            sb.append(String.format(" Your largest purchase was €%.0f at %s.", 
                    largest.getAmount().abs().doubleValue(), largest.getMerchantName()));
        }
        
        return sb.toString();
    }

    private String formatPeriod() {
        String startMonth = periodStart.getMonth().name().substring(0, 1) + 
                           periodStart.getMonth().name().substring(1).toLowerCase();
        String endMonth = periodEnd.getMonth().name().substring(0, 1) + 
                         periodEnd.getMonth().name().substring(1).toLowerCase();
        
        if (startMonth.equals(endMonth)) {
            return startMonth + " " + periodStart.getYear();
        }
        return startMonth + " to " + endMonth + " " + periodEnd.getYear();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("statementId", statementId);
        map.put("cardId", cardId);
        map.put("periodStart", periodStart != null ? periodStart.toString() : null);
        map.put("periodEnd", periodEnd != null ? periodEnd.toString() : null);
        map.put("openingBalance", openingBalance);
        map.put("closingBalance", closingBalance);
        map.put("purchasesTotal", purchasesTotal);
        map.put("paymentsTotal", paymentsTotal);
        map.put("feesTotal", feesTotal);
        map.put("interestTotal", interestTotal);
        map.put("cashAdvancesTotal", cashAdvancesTotal);
        map.put("minimumPayment", minimumPayment);
        map.put("paymentDueDate", paymentDueDate != null ? paymentDueDate.toString() : null);
        map.put("transactionCount", transactionCount);
        map.put("currency", currency);
        map.put("statementDate", statementDate != null ? statementDate.toString() : null);
        map.put("transactions", transactions.stream()
                .map(CreditCardTransaction::toMap)
                .collect(Collectors.toList()));
        return map;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final CreditCardStatement stmt = new CreditCardStatement();

        public Builder statementId(String s) { stmt.statementId = s; return this; }
        public Builder cardId(String s) { stmt.cardId = s; return this; }
        public Builder periodStart(LocalDate d) { stmt.periodStart = d; return this; }
        public Builder periodEnd(LocalDate d) { stmt.periodEnd = d; return this; }
        public Builder openingBalance(BigDecimal b) { stmt.openingBalance = b; return this; }
        public Builder closingBalance(BigDecimal b) { stmt.closingBalance = b; return this; }
        public Builder purchasesTotal(BigDecimal b) { stmt.purchasesTotal = b; return this; }
        public Builder paymentsTotal(BigDecimal b) { stmt.paymentsTotal = b; return this; }
        public Builder feesTotal(BigDecimal b) { stmt.feesTotal = b; return this; }
        public Builder interestTotal(BigDecimal b) { stmt.interestTotal = b; return this; }
        public Builder cashAdvancesTotal(BigDecimal b) { stmt.cashAdvancesTotal = b; return this; }
        public Builder minimumPayment(BigDecimal b) { stmt.minimumPayment = b; return this; }
        public Builder paymentDueDate(LocalDate d) { stmt.paymentDueDate = d; return this; }
        public Builder transactionCount(int i) { stmt.transactionCount = i; return this; }
        public Builder transactions(List<CreditCardTransaction> l) { 
            stmt.transactions = new ArrayList<>(l); 
            return this; 
        }
        public Builder addTransaction(CreditCardTransaction t) { 
            stmt.transactions.add(t); 
            return this; 
        }
        public Builder currency(String s) { stmt.currency = s; return this; }
        public Builder statementDate(LocalDate d) { stmt.statementDate = d; return this; }

        public CreditCardStatement build() { return stmt; }
    }
}
