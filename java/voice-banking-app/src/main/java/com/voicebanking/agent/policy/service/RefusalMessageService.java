package com.voicebanking.agent.policy.service;

import com.voicebanking.agent.policy.domain.PolicyCategory;
import com.voicebanking.agent.policy.domain.RefusalResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class RefusalMessageService {
    private static final Logger log = LoggerFactory.getLogger(RefusalMessageService.class);
    private final Map<PolicyCategory, RefusalTemplate> templates = new EnumMap<>(PolicyCategory.class);

    @PostConstruct
    public void init() {
        templates.put(PolicyCategory.MONEY_MOVEMENT, new RefusalTemplate(
            "money-movement-001",
            "I am not able to make transfers at this time. To send money, you can use the Acme Bank mobile app or log in to online banking.",
            List.of("Use the Acme Bank mobile app - tap Transfers on the home screen", "Log in to online banking at deutsche-bank.de")));
        templates.put(PolicyCategory.ADVISORY, new RefusalTemplate(
            "advisory-001",
            "I am not able to give personalized advice, as that requires understanding your personal financial situation and goals. For personalized guidance, I recommend speaking with one of our advisors.",
            List.of("Speak with a financial advisor", "Visit a branch for personalized consultation")));
        templates.put(PolicyCategory.TRADING, new RefusalTemplate(
            "trading-001",
            "I am not able to execute trades or access investment portfolios. For trading operations, please use the trading platform.",
            List.of("Use the Acme Bank trading platform", "Contact your investment advisor")));
        templates.put(PolicyCategory.ACCOUNT_CHANGES, new RefusalTemplate(
            "account-changes-001",
            "I am not able to make changes to your account settings. You can update your information in the mobile app settings or online banking.",
            List.of("Go to Settings in the mobile app", "Log in to online banking to update your profile")));
        templates.put(PolicyCategory.DISPUTES, new RefusalTemplate(
            "disputes-001",
            "I understand you would like to dispute a transaction. Disputes need to be reviewed by our specialized team who can investigate and help resolve the issue.",
            List.of("Connect with a disputes specialist now", "Submit a dispute through the mobile app secure message feature")));
        templates.put(PolicyCategory.NON_BANKING, new RefusalTemplate(
            "non-banking-001",
            "I focus on banking-related questions, so I am not able to help with that. But I would be happy to help with your accounts, products, or transactions.",
            List.of("Ask about your account balance", "View recent transactions", "Get product information")));
        templates.put(PolicyCategory.HARMFUL, new RefusalTemplate(
            "harmful-001",
            "I am here to help with your banking needs. How can I assist you with your accounts today?",
            List.of()));
        templates.put(PolicyCategory.SECURITY_VIOLATION, new RefusalTemplate(
            "security-001",
            "For your security, I never provide PINs, passwords, or other login credentials. If you have forgotten your PIN, I can explain how to reset it.",
            List.of("Learn how to reset your PIN", "Contact customer service for account security help")));
        log.info("RefusalMessageService initialized with {} templates", templates.size());
    }

    public RefusalResult getRefusalMessage(PolicyCategory category) {
        RefusalTemplate template = templates.get(category);
        if (template == null) {
            log.warn("No refusal template found for category {}, using default", category);
            return RefusalResult.of(category, "I am not able to help with that request. Is there something else I can assist you with?", List.of("Ask about your account balance", "View recent transactions"), "default-001");
        }
        return RefusalResult.of(category, template.message(), template.alternatives(), template.templateId());
    }

    public List<String> getAlternativesForCategory(PolicyCategory category) {
        RefusalTemplate template = templates.get(category);
        return template != null ? template.alternatives() : List.of();
    }

    private record RefusalTemplate(String templateId, String message, List<String> alternatives) {}
}
