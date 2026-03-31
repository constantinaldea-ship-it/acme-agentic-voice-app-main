package com.voicebanking.agent.mobileapp.service;

import com.voicebanking.agent.mobileapp.catalog.AppFeatureCatalog;
import com.voicebanking.agent.mobileapp.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service for generating step-by-step guides for mobile app features.
 * 
 * Provides platform-specific guides with voice-optimized formatting.
 * Guides are limited to 5 steps for voice comprehension.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class FeatureGuideService {
    private static final Logger log = LoggerFactory.getLogger(FeatureGuideService.class);
    private static final int MAX_STEPS = 5;

    private final AppFeatureCatalog featureCatalog;
    private final Map<String, StepGuide> iosGuides = new HashMap<>();
    private final Map<String, StepGuide> androidGuides = new HashMap<>();
    private final Map<String, StepGuide> genericGuides = new HashMap<>();

    public FeatureGuideService(AppFeatureCatalog featureCatalog) {
        this.featureCatalog = featureCatalog;
    }

    @PostConstruct
    public void init() {
        initializeSecurityGuides();
        initializeTransferGuides();
        initializeCardGuides();
        initializeAccountGuides();
        initializeSettingsGuides();
        
        log.info("Initialized {} iOS guides, {} Android guides, {} generic guides",
                iosGuides.size(), androidGuides.size(), genericGuides.size());
    }

    /**
     * Get a step-by-step guide for a feature.
     * Returns platform-specific guide if available, otherwise generic guide.
     */
    public Optional<StepGuide> getGuide(String featureId, Platform platform) {
        // Check platform-specific guides first
        if (platform == Platform.IOS && iosGuides.containsKey(featureId)) {
            return Optional.of(iosGuides.get(featureId));
        }
        if (platform == Platform.ANDROID && androidGuides.containsKey(featureId)) {
            return Optional.of(androidGuides.get(featureId));
        }
        
        // Fall back to generic guide
        if (genericGuides.containsKey(featureId)) {
            return Optional.of(genericGuides.get(featureId));
        }
        
        // Try to generate a basic guide from feature data
        return featureCatalog.getFeature(featureId)
                .map(f -> generateBasicGuide(f, platform));
    }

    /**
     * Check if a guide exists for the given feature.
     */
    public boolean hasGuide(String featureId) {
        return iosGuides.containsKey(featureId) 
                || androidGuides.containsKey(featureId) 
                || genericGuides.containsKey(featureId)
                || featureCatalog.getFeature(featureId).isPresent();
    }

    private StepGuide generateBasicGuide(AppFeature feature, Platform platform) {
        return StepGuide.builder()
                .featureId(feature.getFeatureId())
                .featureName(feature.getNameEn())
                .platform(platform)
                .addStep(1, "Open the Acme Bank mobile app")
                .addStep(2, "Tap the menu icon in the top left")
                .addStep(3, String.format("Look for '%s' in the menu or settings", feature.getNameEn()))
                .helpArticleUrl("https://help.acmebank.example/app/" + feature.getFeatureId())
                .build();
    }

    private void initializeSecurityGuides() {
        // Touch ID Setup (iOS)
        iosGuides.put("touch-id", StepGuide.builder()
                .featureId("touch-id")
                .featureName("Set up Touch ID")
                .platform(Platform.IOS)
                .prerequisites(List.of("Touch ID enabled on your iPhone"))
                .addStep(1, "Open the Acme Bank app and tap Menu in the top left")
                .addStep(2, "Tap Settings, then Security Settings")
                .addStep(3, "Toggle on Touch ID for Login")
                .addStep(4, "Place your finger on the home button when prompted")
                .addStep(5, "Confirm with your app PIN")
                .helpArticleUrl("https://help.acmebank.example/app/touch-id")
                .build());

        // Face ID Setup (iOS)
        iosGuides.put("face-id", StepGuide.builder()
                .featureId("face-id")
                .featureName("Set up Face ID")
                .platform(Platform.IOS)
                .prerequisites(List.of("Face ID enabled on your iPhone"))
                .addStep(1, "Open the Acme Bank app and tap Menu")
                .addStep(2, "Go to Settings, then Security Settings")
                .addStep(3, "Toggle on Face ID for Login")
                .addStep(4, "Look at your phone when prompted")
                .addStep(5, "Confirm with your app PIN")
                .helpArticleUrl("https://help.acmebank.example/app/face-id")
                .build());

        // Fingerprint Setup (Android)
        androidGuides.put("fingerprint", StepGuide.builder()
                .featureId("fingerprint")
                .featureName("Set up Fingerprint Login")
                .platform(Platform.ANDROID)
                .prerequisites(List.of("Fingerprint enrolled in Android settings"))
                .addStep(1, "Open the Acme Bank app and tap the menu icon")
                .addStep(2, "Select Settings, then Security")
                .addStep(3, "Enable Fingerprint Authentication")
                .addStep(4, "Place your finger on the sensor when prompted")
                .addStep(5, "Enter your app PIN to confirm")
                .helpArticleUrl("https://help.acmebank.example/app/fingerprint")
                .build());

        // Change PIN (Generic)
        genericGuides.put("change-pin", StepGuide.builder()
                .featureId("change-pin")
                .featureName("Change App PIN")
                .platform(Platform.BOTH)
                .addStep(1, "Open the app and go to Menu")
                .addStep(2, "Tap Settings, then Security Settings")
                .addStep(3, "Select Change PIN")
                .addStep(4, "Enter your current PIN")
                .addStep(5, "Enter and confirm your new 6-digit PIN")
                .helpArticleUrl("https://help.acmebank.example/app/change-pin")
                .build());

        // Two-Factor Authentication
        genericGuides.put("two-factor", StepGuide.builder()
                .featureId("two-factor")
                .featureName("Enable Two-Factor Authentication")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Menu, then Settings")
                .addStep(2, "Tap Security Settings")
                .addStep(3, "Select Two-Factor Authentication")
                .addStep(4, "Choose SMS or Authenticator App")
                .addStep(5, "Follow the verification steps")
                .helpArticleUrl("https://help.acmebank.example/app/2fa")
                .build());

        // Logout
        genericGuides.put("logout", StepGuide.builder()
                .featureId("logout")
                .featureName("Log Out")
                .platform(Platform.BOTH)
                .addStep(1, "Tap the Menu icon in the top left")
                .addStep(2, "Scroll down and tap Log Out")
                .addStep(3, "Confirm when prompted")
                .helpArticleUrl("https://help.acmebank.example/app/logout")
                .build());
    }

    private void initializeTransferGuides() {
        // SEPA Transfer
        genericGuides.put("transfer-sepa", StepGuide.builder()
                .featureId("transfer-sepa")
                .featureName("Make a SEPA Transfer")
                .platform(Platform.BOTH)
                .prerequisites(List.of("recipient IBAN"))
                .addStep(1, "Tap Transfer on the home screen or menu")
                .addStep(2, "Select SEPA Transfer")
                .addStep(3, "Enter the recipient IBAN or select from saved")
                .addStep(4, "Enter the amount and optional reference")
                .addStep(5, "Review and confirm with your PIN or biometric")
                .helpArticleUrl("https://help.acmebank.example/app/sepa-transfer")
                .build());

        // Internal Transfer
        genericGuides.put("transfer-internal", StepGuide.builder()
                .featureId("transfer-internal")
                .featureName("Transfer Between Your Accounts")
                .platform(Platform.BOTH)
                .addStep(1, "Tap Transfer on the home screen")
                .addStep(2, "Select Internal Transfer")
                .addStep(3, "Choose the source and destination accounts")
                .addStep(4, "Enter the amount")
                .addStep(5, "Confirm the transfer")
                .helpArticleUrl("https://help.acmebank.example/app/internal-transfer")
                .build());

        // Standing Order
        genericGuides.put("standing-order", StepGuide.builder()
                .featureId("standing-order")
                .featureName("Set Up a Standing Order")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Transfer, then Standing Orders")
                .addStep(2, "Tap Create New Standing Order")
                .addStep(3, "Enter recipient details and amount")
                .addStep(4, "Set the frequency and start date")
                .addStep(5, "Review and confirm with PIN")
                .helpArticleUrl("https://help.acmebank.example/app/standing-order")
                .build());

        // Add Recipient
        genericGuides.put("add-recipient", StepGuide.builder()
                .featureId("add-recipient")
                .featureName("Add a New Recipient")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Transfer, then Manage Recipients")
                .addStep(2, "Tap Add New Recipient")
                .addStep(3, "Enter the recipient name and IBAN")
                .addStep(4, "Optionally add a nickname for easy finding")
                .addStep(5, "Save the recipient")
                .helpArticleUrl("https://help.acmebank.example/app/add-recipient")
                .build());

        // Instant Transfer
        genericGuides.put("instant-transfer", StepGuide.builder()
                .featureId("instant-transfer")
                .featureName("Make an Instant Transfer")
                .platform(Platform.BOTH)
                .prerequisites(List.of("app version 5.0 or higher"))
                .addStep(1, "Tap Transfer, then Instant Transfer")
                .addStep(2, "Enter recipient IBAN or select saved")
                .addStep(3, "Enter the amount")
                .addStep(4, "Note: A small fee may apply for instant transfers")
                .addStep(5, "Confirm with biometric or PIN")
                .helpArticleUrl("https://help.acmebank.example/app/instant-transfer")
                .build());
    }

    private void initializeCardGuides() {
        // Block Card
        genericGuides.put("block-card", StepGuide.builder()
                .featureId("block-card")
                .featureName("Block Your Card")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Cards in the menu")
                .addStep(2, "Select the card you want to block")
                .addStep(3, "Tap Block Card")
                .addStep(4, "Choose Temporary or Permanent block")
                .addStep(5, "Confirm the action")
                .helpArticleUrl("https://help.acmebank.example/app/block-card")
                .build());

        // Unblock Card
        genericGuides.put("unblock-card", StepGuide.builder()
                .featureId("unblock-card")
                .featureName("Unblock Your Card")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Cards in the menu")
                .addStep(2, "Select the blocked card")
                .addStep(3, "Tap Unblock Card")
                .addStep(4, "Confirm with your PIN")
                .helpArticleUrl("https://help.acmebank.example/app/unblock-card")
                .build());

        // View Card PIN
        genericGuides.put("view-card-pin", StepGuide.builder()
                .featureId("view-card-pin")
                .featureName("View Your Card PIN")
                .platform(Platform.BOTH)
                .prerequisites(List.of("app version 5.0 or higher"))
                .addStep(1, "Go to Cards, then select your card")
                .addStep(2, "Tap Show PIN")
                .addStep(3, "Authenticate with biometric or app PIN")
                .addStep(4, "Your card PIN will display for 10 seconds")
                .helpArticleUrl("https://help.acmebank.example/app/view-card-pin")
                .build());

        // Card Limits
        genericGuides.put("card-limits", StepGuide.builder()
                .featureId("card-limits")
                .featureName("Adjust Card Limits")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Cards, select your card")
                .addStep(2, "Tap Card Settings or Limits")
                .addStep(3, "Adjust daily ATM or purchase limits")
                .addStep(4, "Confirm changes with your PIN")
                .helpArticleUrl("https://help.acmebank.example/app/card-limits")
                .build());

        // Add to Wallet
        iosGuides.put("add-to-wallet", StepGuide.builder()
                .featureId("add-to-wallet")
                .featureName("Add Card to Apple Wallet")
                .platform(Platform.IOS)
                .addStep(1, "Go to Cards, select your card")
                .addStep(2, "Tap Add to Apple Wallet")
                .addStep(3, "Follow the Apple Wallet setup prompts")
                .addStep(4, "Verify your card via SMS or call")
                .addStep(5, "Your card is now ready for Apple Pay")
                .helpArticleUrl("https://help.acmebank.example/app/apple-pay")
                .build());

        androidGuides.put("add-to-wallet", StepGuide.builder()
                .featureId("add-to-wallet")
                .featureName("Add Card to Google Wallet")
                .platform(Platform.ANDROID)
                .addStep(1, "Go to Cards, select your card")
                .addStep(2, "Tap Add to Google Wallet")
                .addStep(3, "Follow the Google Pay setup prompts")
                .addStep(4, "Verify your card via SMS or call")
                .addStep(5, "Your card is now ready for Google Pay")
                .helpArticleUrl("https://help.acmebank.example/app/google-pay")
                .build());
    }

    private void initializeAccountGuides() {
        // View Balance
        genericGuides.put("view-balance", StepGuide.builder()
                .featureId("view-balance")
                .featureName("Check Your Balance")
                .platform(Platform.BOTH)
                .addStep(1, "Open the Acme Bank app")
                .addStep(2, "Your balance shows on the home screen")
                .addStep(3, "Tap the account to see more details")
                .helpArticleUrl("https://help.acmebank.example/app/balance")
                .build());

        // Transaction History
        genericGuides.put("transaction-history", StepGuide.builder()
                .featureId("transaction-history")
                .featureName("View Transaction History")
                .platform(Platform.BOTH)
                .addStep(1, "Tap your account on the home screen")
                .addStep(2, "Scroll down to see recent transactions")
                .addStep(3, "Tap a transaction for details")
                .addStep(4, "Use the filter icon to search by date or type")
                .helpArticleUrl("https://help.acmebank.example/app/transactions")
                .build());

        // Download Statement
        genericGuides.put("download-statement", StepGuide.builder()
                .featureId("download-statement")
                .featureName("Download Account Statement")
                .platform(Platform.BOTH)
                .addStep(1, "Go to your account, then Statements")
                .addStep(2, "Select the month or date range")
                .addStep(3, "Tap Download PDF")
                .addStep(4, "The statement will save to your device")
                .helpArticleUrl("https://help.acmebank.example/app/statements")
                .build());

        // Find IBAN
        genericGuides.put("find-iban", StepGuide.builder()
                .featureId("find-iban")
                .featureName("Find Your IBAN")
                .platform(Platform.BOTH)
                .addStep(1, "Tap your account on the home screen")
                .addStep(2, "Tap Account Details or the info icon")
                .addStep(3, "Your IBAN and BIC are shown here")
                .addStep(4, "Tap to copy or share")
                .helpArticleUrl("https://help.acmebank.example/app/iban")
                .build());
    }

    private void initializeSettingsGuides() {
        // Push Notifications
        genericGuides.put("push-notifications", StepGuide.builder()
                .featureId("push-notifications")
                .featureName("Configure Push Notifications")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Menu, then Settings")
                .addStep(2, "Tap Notifications")
                .addStep(3, "Toggle notifications on or off")
                .addStep(4, "Select which types of alerts you want")
                .helpArticleUrl("https://help.acmebank.example/app/notifications")
                .build());

        // Language Settings
        genericGuides.put("language-settings", StepGuide.builder()
                .featureId("language-settings")
                .featureName("Change App Language")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Menu, then Settings")
                .addStep(2, "Tap Language or Sprache")
                .addStep(3, "Select your preferred language")
                .addStep(4, "The app will restart with the new language")
                .helpArticleUrl("https://help.acmebank.example/app/language")
                .build());

        // Appearance Settings
        genericGuides.put("appearance-settings", StepGuide.builder()
                .featureId("appearance-settings")
                .featureName("Change App Appearance")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Settings from the menu")
                .addStep(2, "Tap Appearance or Theme")
                .addStep(3, "Choose Light, Dark, or System Default")
                .helpArticleUrl("https://help.acmebank.example/app/theme")
                .build());

        // Clear Cache
        genericGuides.put("clear-cache", StepGuide.builder()
                .featureId("clear-cache")
                .featureName("Clear App Cache")
                .platform(Platform.BOTH)
                .addStep(1, "Go to Settings from the menu")
                .addStep(2, "Scroll to Storage or Cache")
                .addStep(3, "Tap Clear Cache")
                .addStep(4, "Confirm when prompted")
                .helpArticleUrl("https://help.acmebank.example/app/cache")
                .build());
    }
}
