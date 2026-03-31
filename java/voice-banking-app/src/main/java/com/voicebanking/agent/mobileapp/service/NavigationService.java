package com.voicebanking.agent.mobileapp.service;

import com.voicebanking.agent.mobileapp.catalog.AppFeatureCatalog;
import com.voicebanking.agent.mobileapp.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service for providing navigation paths to mobile app features.
 * 
 * Generates paths showing how to navigate through the app menu
 * to reach specific features.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class NavigationService {
    private static final Logger log = LoggerFactory.getLogger(NavigationService.class);

    private final AppFeatureCatalog featureCatalog;
    private final Map<String, NavigationPath> iosNavigation = new HashMap<>();
    private final Map<String, NavigationPath> androidNavigation = new HashMap<>();
    private final Map<String, NavigationPath> genericNavigation = new HashMap<>();

    public NavigationService(AppFeatureCatalog featureCatalog) {
        this.featureCatalog = featureCatalog;
    }

    @PostConstruct
    public void init() {
        initializeSecurityPaths();
        initializeTransferPaths();
        initializeCardPaths();
        initializeAccountPaths();
        initializeSettingsPaths();
        initializeSupportPaths();
        
        log.info("Initialized {} iOS paths, {} Android paths, {} generic paths",
                iosNavigation.size(), androidNavigation.size(), genericNavigation.size());
    }

    /**
     * Get navigation path to a feature.
     */
    public Optional<NavigationPath> getNavigationPath(String featureId, Platform platform) {
        // Check platform-specific paths first
        if (platform == Platform.IOS && iosNavigation.containsKey(featureId)) {
            return Optional.of(iosNavigation.get(featureId));
        }
        if (platform == Platform.ANDROID && androidNavigation.containsKey(featureId)) {
            return Optional.of(androidNavigation.get(featureId));
        }
        
        // Fall back to generic path
        if (genericNavigation.containsKey(featureId)) {
            return Optional.of(genericNavigation.get(featureId));
        }
        
        // Generate basic path from feature data
        return featureCatalog.getFeature(featureId)
                .map(f -> generateBasicPath(f, platform));
    }

    /**
     * Get the deep link for a feature if available.
     */
    public Optional<String> getDeepLink(String featureId) {
        return featureCatalog.getFeature(featureId)
                .map(AppFeature::getDeepLink)
                .filter(link -> link != null && !link.isBlank());
    }

    private NavigationPath generateBasicPath(AppFeature feature, Platform platform) {
        NavigationPath.Builder builder = NavigationPath.builder()
                .featureId(feature.getFeatureId())
                .platform(platform)
                .startingPoint("Home");

        // Add category-based path
        switch (feature.getCategory()) {
            case SECURITY -> builder.pathSegments(List.of("Menu", "Settings", "Security", feature.getNameEn()));
            case TRANSFERS -> builder.pathSegments(List.of("Transfer", feature.getNameEn()));
            case ACCOUNTS -> builder.pathSegments(List.of("Accounts", feature.getNameEn()));
            case CARDS -> builder.pathSegments(List.of("Cards", feature.getNameEn()));
            case SETTINGS -> builder.pathSegments(List.of("Menu", "Settings", feature.getNameEn()));
            case SUPPORT -> builder.pathSegments(List.of("Menu", "Help & Support", feature.getNameEn()));
        }

        if (feature.getDeepLink() != null) {
            builder.deepLink(feature.getDeepLink());
        }

        return builder.build();
    }

    private void initializeSecurityPaths() {
        // Touch ID (iOS)
        iosNavigation.put("touch-id", NavigationPath.builder()
                .featureId("touch-id")
                .platform(Platform.IOS)
                .pathSegments(List.of("Menu", "Settings", "Security Settings", "Touch ID"))
                .deepLink("acmebank://settings/security/touchid")
                .build());

        // Face ID (iOS)
        iosNavigation.put("face-id", NavigationPath.builder()
                .featureId("face-id")
                .platform(Platform.IOS)
                .pathSegments(List.of("Menu", "Settings", "Security Settings", "Face ID"))
                .deepLink("acmebank://settings/security/faceid")
                .build());

        // Fingerprint (Android)
        androidNavigation.put("fingerprint", NavigationPath.builder()
                .featureId("fingerprint")
                .platform(Platform.ANDROID)
                .pathSegments(List.of("Menu", "Settings", "Security", "Fingerprint"))
                .deepLink("acmebank://settings/security/fingerprint")
                .build());

        // Change PIN
        genericNavigation.put("change-pin", NavigationPath.builder()
                .featureId("change-pin")
                .pathSegments(List.of("Menu", "Settings", "Security Settings", "Change PIN"))
                .deepLink("acmebank://settings/security/pin")
                .build());

        // Two-Factor Auth
        genericNavigation.put("two-factor", NavigationPath.builder()
                .featureId("two-factor")
                .pathSegments(List.of("Menu", "Settings", "Security Settings", "Two-Factor Authentication"))
                .deepLink("acmebank://settings/security/2fa")
                .build());

        // Logout
        genericNavigation.put("logout", NavigationPath.builder()
                .featureId("logout")
                .pathSegments(List.of("Menu", "Log Out"))
                .build());
    }

    private void initializeTransferPaths() {
        genericNavigation.put("transfer-internal", NavigationPath.builder()
                .featureId("transfer-internal")
                .pathSegments(List.of("Transfer", "Internal Transfer"))
                .deepLink("acmebank://transfer/internal")
                .build());

        genericNavigation.put("transfer-sepa", NavigationPath.builder()
                .featureId("transfer-sepa")
                .pathSegments(List.of("Transfer", "SEPA Transfer"))
                .deepLink("acmebank://transfer/sepa")
                .build());

        genericNavigation.put("standing-order", NavigationPath.builder()
                .featureId("standing-order")
                .pathSegments(List.of("Transfer", "Standing Orders"))
                .deepLink("acmebank://transfer/standing")
                .build());

        genericNavigation.put("add-recipient", NavigationPath.builder()
                .featureId("add-recipient")
                .pathSegments(List.of("Transfer", "Manage Recipients", "Add New"))
                .deepLink("acmebank://transfer/recipients/add")
                .build());

        genericNavigation.put("instant-transfer", NavigationPath.builder()
                .featureId("instant-transfer")
                .pathSegments(List.of("Transfer", "Instant Transfer"))
                .deepLink("acmebank://transfer/instant")
                .build());
    }

    private void initializeCardPaths() {
        genericNavigation.put("block-card", NavigationPath.builder()
                .featureId("block-card")
                .pathSegments(List.of("Cards", "[Select Card]", "Block Card"))
                .deepLink("acmebank://cards/block")
                .build());

        genericNavigation.put("unblock-card", NavigationPath.builder()
                .featureId("unblock-card")
                .pathSegments(List.of("Cards", "[Select Card]", "Unblock Card"))
                .deepLink("acmebank://cards/unblock")
                .build());

        genericNavigation.put("view-card-pin", NavigationPath.builder()
                .featureId("view-card-pin")
                .pathSegments(List.of("Cards", "[Select Card]", "Show PIN"))
                .deepLink("acmebank://cards/pin")
                .build());

        genericNavigation.put("card-limits", NavigationPath.builder()
                .featureId("card-limits")
                .pathSegments(List.of("Cards", "[Select Card]", "Card Settings", "Limits"))
                .deepLink("acmebank://cards/limits")
                .build());

        genericNavigation.put("contactless-settings", NavigationPath.builder()
                .featureId("contactless-settings")
                .pathSegments(List.of("Cards", "[Select Card]", "Card Settings", "Contactless"))
                .deepLink("acmebank://cards/contactless")
                .build());

        iosNavigation.put("add-to-wallet", NavigationPath.builder()
                .featureId("add-to-wallet")
                .platform(Platform.IOS)
                .pathSegments(List.of("Cards", "[Select Card]", "Add to Apple Wallet"))
                .deepLink("acmebank://cards/wallet")
                .build());

        androidNavigation.put("add-to-wallet", NavigationPath.builder()
                .featureId("add-to-wallet")
                .platform(Platform.ANDROID)
                .pathSegments(List.of("Cards", "[Select Card]", "Add to Google Wallet"))
                .deepLink("acmebank://cards/wallet")
                .build());
    }

    private void initializeAccountPaths() {
        genericNavigation.put("view-balance", NavigationPath.builder()
                .featureId("view-balance")
                .pathSegments(List.of())
                .startingPoint("Home (Balance shown)")
                .deepLink("acmebank://accounts/balance")
                .build());

        genericNavigation.put("transaction-history", NavigationPath.builder()
                .featureId("transaction-history")
                .pathSegments(List.of("[Tap Account]", "Transactions"))
                .deepLink("acmebank://accounts/transactions")
                .build());

        genericNavigation.put("download-statement", NavigationPath.builder()
                .featureId("download-statement")
                .pathSegments(List.of("[Tap Account]", "Statements", "Download"))
                .deepLink("acmebank://accounts/statements")
                .build());

        genericNavigation.put("find-iban", NavigationPath.builder()
                .featureId("find-iban")
                .pathSegments(List.of("[Tap Account]", "Account Details"))
                .deepLink("acmebank://accounts/details")
                .build());
    }

    private void initializeSettingsPaths() {
        genericNavigation.put("push-notifications", NavigationPath.builder()
                .featureId("push-notifications")
                .pathSegments(List.of("Menu", "Settings", "Notifications"))
                .deepLink("acmebank://settings/notifications")
                .build());

        genericNavigation.put("language-settings", NavigationPath.builder()
                .featureId("language-settings")
                .pathSegments(List.of("Menu", "Settings", "Language"))
                .deepLink("acmebank://settings/language")
                .build());

        genericNavigation.put("appearance-settings", NavigationPath.builder()
                .featureId("appearance-settings")
                .pathSegments(List.of("Menu", "Settings", "Appearance"))
                .deepLink("acmebank://settings/appearance")
                .build());

        genericNavigation.put("profile-settings", NavigationPath.builder()
                .featureId("profile-settings")
                .pathSegments(List.of("Menu", "Settings", "Profile"))
                .deepLink("acmebank://settings/profile")
                .build());

        genericNavigation.put("data-privacy", NavigationPath.builder()
                .featureId("data-privacy")
                .pathSegments(List.of("Menu", "Settings", "Privacy & Data"))
                .deepLink("acmebank://settings/privacy")
                .build());

        genericNavigation.put("clear-cache", NavigationPath.builder()
                .featureId("clear-cache")
                .pathSegments(List.of("Menu", "Settings", "Storage", "Clear Cache"))
                .deepLink("acmebank://settings/storage")
                .build());
    }

    private void initializeSupportPaths() {
        genericNavigation.put("contact-support", NavigationPath.builder()
                .featureId("contact-support")
                .pathSegments(List.of("Menu", "Help & Support", "Contact Us"))
                .deepLink("acmebank://support/contact")
                .build());

        genericNavigation.put("in-app-chat", NavigationPath.builder()
                .featureId("in-app-chat")
                .pathSegments(List.of("Menu", "Help & Support", "Chat"))
                .deepLink("acmebank://support/chat")
                .build());

        genericNavigation.put("faq", NavigationPath.builder()
                .featureId("faq")
                .pathSegments(List.of("Menu", "Help & Support", "FAQs"))
                .deepLink("acmebank://support/faq")
                .build());

        genericNavigation.put("report-issue", NavigationPath.builder()
                .featureId("report-issue")
                .pathSegments(List.of("Menu", "Help & Support", "Report an Issue"))
                .deepLink("acmebank://support/report")
                .build());
    }
}
