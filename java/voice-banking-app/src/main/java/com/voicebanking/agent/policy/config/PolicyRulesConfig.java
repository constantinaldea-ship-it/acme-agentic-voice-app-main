package com.voicebanking.agent.policy.config;

import com.voicebanking.agent.policy.domain.PolicyCategory;
import com.voicebanking.agent.policy.domain.PolicyRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.util.*;

@Component
public class PolicyRulesConfig {
    private static final Logger log = LoggerFactory.getLogger(PolicyRulesConfig.class);
    private final Map<String, PolicyRule> rulesById = new HashMap<>();
    private final Map<PolicyCategory, List<PolicyRule>> rulesByCategory = new EnumMap<>(PolicyCategory.class);
    private final List<String> securityKeywords = new ArrayList<>();

    @PostConstruct
    public void init() {
        initializeAllowedIntents();
        initializeMoneyMovementIntents();
        initializeAdvisoryIntents();
        initializeTradingIntents();
        initializeAccountChangesIntents();
        initializeDisputesIntents();
        initializeNonBankingIntents();
        initializeHarmfulIntents();
        initializeSecurityViolationIntents();
        log.info("PolicyRulesConfig initialized with {} rules across {} categories", rulesById.size(), rulesByCategory.size());
    }

    private void addRule(PolicyRule rule) {
        rulesById.put(rule.id(), rule);
        rulesByCategory.computeIfAbsent(rule.category(), k -> new ArrayList<>()).add(rule);
    }

    private void initializeAllowedIntents() {
        addRule(PolicyRule.of("allowed-balance", PolicyCategory.ALLOWED, List.of("balance_inquiry", "get_balance", "check_balance", "show_balance", "what_is_my_balance")));
        addRule(PolicyRule.of("allowed-accounts", PolicyCategory.ALLOWED, List.of("list_accounts", "show_accounts", "my_accounts")));
        addRule(PolicyRule.of("allowed-transactions", PolicyCategory.ALLOWED, List.of("transaction_history", "recent_transactions", "show_transactions", "query_transactions")));
        addRule(PolicyRule.of("allowed-statements", PolicyCategory.ALLOWED, List.of("account_statement", "get_statement", "monthly_statement")));
        addRule(PolicyRule.of("allowed-product-info", PolicyCategory.ALLOWED, List.of("product_info", "credit_card_info", "account_info", "interest_rates")));
        addRule(PolicyRule.of("allowed-branch", PolicyCategory.ALLOWED, List.of("find_branch", "nearby_branches", "branch_hours", "atm_locations")));
        addRule(PolicyRule.of("allowed-help", PolicyCategory.ALLOWED, List.of("help", "what_can_you_do", "capabilities")));
    }

    private void initializeMoneyMovementIntents() {
        addRule(PolicyRule.of("money-transfer", PolicyCategory.MONEY_MOVEMENT, List.of("transfer_funds", "send_money", "wire_transfer", "make_transfer")));
        addRule(PolicyRule.of("money-payment", PolicyCategory.MONEY_MOVEMENT, List.of("pay_bill", "make_payment", "bill_payment", "pay_credit_card")));
    }

    private void initializeAdvisoryIntents() {
        addRule(PolicyRule.of("advisory-investment", PolicyCategory.ADVISORY, List.of("investment_advice", "should_i_invest", "recommend_investment")));
        addRule(PolicyRule.of("advisory-product", PolicyCategory.ADVISORY, List.of("recommend_product", "which_card_is_best", "best_account_for_me")));
    }

    private void initializeTradingIntents() {
        addRule(PolicyRule.of("trading-buy", PolicyCategory.TRADING, List.of("buy_stock", "purchase_shares", "buy_securities")));
        addRule(PolicyRule.of("trading-sell", PolicyCategory.TRADING, List.of("sell_stock", "sell_shares", "sell_securities")));
        addRule(PolicyRule.of("trading-portfolio", PolicyCategory.TRADING, List.of("portfolio_value", "my_investments", "stock_portfolio")));
    }

    private void initializeAccountChangesIntents() {
        addRule(PolicyRule.of("changes-address", PolicyCategory.ACCOUNT_CHANGES, List.of("change_address", "update_address", "new_address")));
        addRule(PolicyRule.of("changes-email", PolicyCategory.ACCOUNT_CHANGES, List.of("change_email", "update_email", "new_email")));
        addRule(PolicyRule.of("changes-phone", PolicyCategory.ACCOUNT_CHANGES, List.of("change_phone", "update_phone", "new_phone_number")));
    }

    private void initializeDisputesIntents() {
        addRule(PolicyRule.of("disputes-charge", PolicyCategory.DISPUTES, List.of("dispute_charge", "dispute_transaction", "fraudulent_charge")));
        addRule(PolicyRule.of("disputes-complaint", PolicyCategory.DISPUTES, List.of("file_complaint", "make_complaint", "report_issue")));
    }

    private void initializeNonBankingIntents() {
        addRule(PolicyRule.of("nonbanking-weather", PolicyCategory.NON_BANKING, List.of("weather", "whats_the_weather", "weather_forecast")));
        addRule(PolicyRule.of("nonbanking-jokes", PolicyCategory.NON_BANKING, List.of("tell_joke", "joke", "something_funny")));
        addRule(PolicyRule.of("nonbanking-general", PolicyCategory.NON_BANKING, List.of("sports", "news", "entertainment", "music")));
    }

    private void initializeHarmfulIntents() {
        addRule(PolicyRule.of("harmful-abuse", PolicyCategory.HARMFUL, List.of("abuse", "harassment", "threat")));
    }

    private void initializeSecurityViolationIntents() {
        addRule(PolicyRule.of("security-pin", PolicyCategory.SECURITY_VIOLATION, List.of("tell_me_my_pin", "what_is_my_pin", "show_pin")));
        addRule(PolicyRule.of("security-password", PolicyCategory.SECURITY_VIOLATION, List.of("tell_me_my_password", "what_is_my_password", "show_password")));
        securityKeywords.addAll(List.of("my pin", "my password", "my credentials", "tell me pin", "reveal password", "show me my pin", "what is my password"));
    }

    public Optional<PolicyRule> findRuleById(String ruleId) { return Optional.ofNullable(rulesById.get(ruleId)); }
    public List<PolicyRule> getRulesForCategory(PolicyCategory category) { return rulesByCategory.getOrDefault(category, List.of()); }
    public Collection<PolicyRule> getAllRules() { return Collections.unmodifiableCollection(rulesById.values()); }
    public List<String> getSecurityKeywords() { return Collections.unmodifiableList(securityKeywords); }
}
