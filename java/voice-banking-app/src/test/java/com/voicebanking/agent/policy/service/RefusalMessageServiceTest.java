package com.voicebanking.agent.policy.service;

import com.voicebanking.agent.policy.domain.PolicyCategory;
import com.voicebanking.agent.policy.domain.RefusalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

@DisplayName("RefusalMessageService Tests")
class RefusalMessageServiceTest {
    private RefusalMessageService refusalMessageService;

    @BeforeEach
    void setUp() {
        refusalMessageService = new RefusalMessageService();
        refusalMessageService.init();
    }

    @Test
    @DisplayName("Should return money movement refusal with alternatives")
    void shouldReturnMoneyMovementRefusal() {
        RefusalResult result = refusalMessageService.getRefusalMessage(PolicyCategory.MONEY_MOVEMENT);
        assertThat(result.message()).contains("transfer");
        assertThat(result.alternatives()).isNotEmpty();
        assertThat(result.templateId()).isEqualTo("money-movement-001");
    }

    @Test
    @DisplayName("Should return advisory refusal with advisor guidance")
    void shouldReturnAdvisoryRefusal() {
        RefusalResult result = refusalMessageService.getRefusalMessage(PolicyCategory.ADVISORY);
        assertThat(result.message()).contains("advice");
        assertThat(result.alternatives()).contains("Speak with a financial advisor");
    }

    @Test
    @DisplayName("Should return trading refusal")
    void shouldReturnTradingRefusal() {
        RefusalResult result = refusalMessageService.getRefusalMessage(PolicyCategory.TRADING);
        assertThat(result.message()).contains("trade");
        assertThat(result.templateId()).isEqualTo("trading-001");
    }

    @Test
    @DisplayName("Should return disputes refusal with handover offered")
    void shouldReturnDisputesRefusalWithHandover() {
        RefusalResult result = refusalMessageService.getRefusalMessage(PolicyCategory.DISPUTES);
        assertThat(result.message()).contains("dispute");
        assertThat(result.handoverOffered()).isTrue();
    }

    @Test
    @DisplayName("Should return security violation refusal")
    void shouldReturnSecurityViolationRefusal() {
        RefusalResult result = refusalMessageService.getRefusalMessage(PolicyCategory.SECURITY_VIOLATION);
        assertThat(result.message()).contains("security");
        assertThat(result.templateId()).isEqualTo("security-001");
    }

    @Test
    @DisplayName("Should return non-banking refusal")
    void shouldReturnNonBankingRefusal() {
        RefusalResult result = refusalMessageService.getRefusalMessage(PolicyCategory.NON_BANKING);
        assertThat(result.message()).contains("banking");
    }

    @Test
    @DisplayName("Should return alternatives for category")
    void shouldReturnAlternativesForCategory() {
        var alternatives = refusalMessageService.getAlternativesForCategory(PolicyCategory.MONEY_MOVEMENT);
        assertThat(alternatives).isNotEmpty();
        assertThat(alternatives).anyMatch(a -> a.contains("mobile app") || a.contains("online"));
    }

    @Test
    @DisplayName("Should return default refusal for ALLOWED category")
    void shouldReturnDefaultForAllowedCategory() {
        RefusalResult result = refusalMessageService.getRefusalMessage(PolicyCategory.ALLOWED);
        assertThat(result.templateId()).isEqualTo("default-001");
    }
}
