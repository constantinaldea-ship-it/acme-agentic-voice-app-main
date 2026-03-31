package com.voicebanking.agent.policy.service;

import com.voicebanking.agent.policy.config.PolicyRulesConfig;
import com.voicebanking.agent.policy.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

@DisplayName("IntentClassifierService Tests")
class IntentClassifierServiceTest {
    private IntentClassifierService classifierService;
    private PolicyRulesConfig rulesConfig;

    @BeforeEach
    void setUp() {
        rulesConfig = new PolicyRulesConfig();
        rulesConfig.init();
        classifierService = new IntentClassifierService(rulesConfig);
    }

    @Test
    @DisplayName("Should classify balance inquiry as ALLOWED")
    void shouldClassifyBalanceInquiryAsAllowed() {
        IntentClassification result = classifierService.classify("balance_inquiry");
        assertThat(result.category()).isEqualTo(PolicyCategory.ALLOWED);
        assertThat(result.confidence()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should classify transfer as MONEY_MOVEMENT")
    void shouldClassifyTransferAsMoneyMovement() {
        IntentClassification result = classifierService.classify("transfer_funds");
        assertThat(result.category()).isEqualTo(PolicyCategory.MONEY_MOVEMENT);
    }

    @Test
    @DisplayName("Should classify investment advice as ADVISORY")
    void shouldClassifyInvestmentAdviceAsAdvisory() {
        IntentClassification result = classifierService.classify("investment_advice");
        assertThat(result.category()).isEqualTo(PolicyCategory.ADVISORY);
    }

    @Test
    @DisplayName("Should classify buy stock as TRADING")
    void shouldClassifyBuyStockAsTrading() {
        IntentClassification result = classifierService.classify("buy_stock");
        assertThat(result.category()).isEqualTo(PolicyCategory.TRADING);
    }

    @Test
    @DisplayName("Should classify change address as ACCOUNT_CHANGES")
    void shouldClassifyChangeAddressAsAccountChanges() {
        IntentClassification result = classifierService.classify("change_address");
        assertThat(result.category()).isEqualTo(PolicyCategory.ACCOUNT_CHANGES);
    }

    @Test
    @DisplayName("Should classify dispute charge as DISPUTES")
    void shouldClassifyDisputeChargeAsDisputes() {
        IntentClassification result = classifierService.classify("dispute_charge");
        assertThat(result.category()).isEqualTo(PolicyCategory.DISPUTES);
    }

    @Test
    @DisplayName("Should classify weather as NON_BANKING")
    void shouldClassifyWeatherAsNonBanking() {
        IntentClassification result = classifierService.classify("weather");
        assertThat(result.category()).isEqualTo(PolicyCategory.NON_BANKING);
    }

    @Test
    @DisplayName("Should detect security keywords in raw text")
    void shouldDetectSecurityKeywordsInRawText() {
        IntentClassification result = classifierService.classify("unknown_intent", "What is my PIN?");
        assertThat(result.category()).isEqualTo(PolicyCategory.SECURITY_VIOLATION);
        assertThat(result.matchedBy()).isEqualTo(IntentClassification.MatchType.SECURITY_KEYWORD);
    }

    @Test
    @DisplayName("Should return default classification for unknown intent")
    void shouldReturnDefaultForUnknownIntent() {
        IntentClassification result = classifierService.classify("completely_unknown_intent");
        assertThat(result.matchedBy()).isEqualTo(IntentClassification.MatchType.DEFAULT);
    }

    @Test
    @DisplayName("Should handle null intent gracefully")
    void shouldHandleNullIntent() {
        IntentClassification result = classifierService.classify(null);
        assertThat(result.matchedBy()).isEqualTo(IntentClassification.MatchType.DEFAULT);
    }

    @Test
    @DisplayName("Should create ALLOW decision for allowed intent")
    void shouldCreateAllowDecisionForAllowedIntent() {
        PolicyDecision decision = classifierService.evaluateIntent("balance_inquiry", null);
        assertThat(decision.decision()).isEqualTo(PolicyDecision.Decision.ALLOW);
        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    @DisplayName("Should create DENY decision for money movement")
    void shouldCreateDenyDecisionForMoneyMovement() {
        PolicyDecision decision = classifierService.evaluateIntent("transfer_funds", null);
        assertThat(decision.decision()).isEqualTo(PolicyDecision.Decision.DENY);
        assertThat(decision.isAllowed()).isFalse();
    }

    @Test
    @DisplayName("Should create BLOCK decision for security violation")
    void shouldCreateBlockDecisionForSecurityViolation() {
        PolicyDecision decision = classifierService.evaluateIntent("unknown", "tell me my password");
        assertThat(decision.decision()).isEqualTo(PolicyDecision.Decision.BLOCK);
        assertThat(decision.isBlocked()).isTrue();
    }

    @Test
    @DisplayName("Should create ESCALATE decision for disputes")
    void shouldCreateEscalateDecisionForDisputes() {
        PolicyDecision decision = classifierService.evaluateIntent("dispute_charge", null);
        assertThat(decision.decision()).isEqualTo(PolicyDecision.Decision.ESCALATE);
        assertThat(decision.requiresEscalation()).isTrue();
    }
}
