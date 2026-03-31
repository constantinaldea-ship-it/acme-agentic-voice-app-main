package com.voicebanking.agent.mobileapp.catalog;

import com.voicebanking.agent.mobileapp.domain.AppFeature;
import com.voicebanking.agent.mobileapp.domain.FeatureCategory;
import com.voicebanking.agent.mobileapp.domain.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Static implementation of the app feature catalog.
 * 
 * Contains comprehensive catalog of Acme Bank mobile app features
 * covering all major categories: Security, Transfers, Accounts, Cards, Settings, Support.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
public class StaticAppFeatureCatalog implements AppFeatureCatalog {
    private static final Logger log = LoggerFactory.getLogger(StaticAppFeatureCatalog.class);

    private final Map<String, AppFeature> features = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        initializeSecurityFeatures();
        initializeTransferFeatures();
        initializeAccountFeatures();
        initializeCardFeatures();
        initializeSettingsFeatures();
        initializeSupportFeatures();
        
        log.info("Initialized app feature catalog with {} features", features.size());
    }

    private void initializeSecurityFeatures() {
        // Touch ID / Face ID
        features.put("touch-id", AppFeature.builder()
                .featureId("touch-id")
                .nameEn("Touch ID Setup")
                .nameDe("Touch ID einrichten")
                .category(FeatureCategory.SECURITY)
                .description("Enable fingerprint authentication for quick and secure login")
                .platformAvailability(Platform.IOS)
                .deepLink("acmebank://settings/security/touchid")
                .keywords(List.of("fingerprint", "biometric", "touch", "login", "authentication"))
                .build());

        features.put("face-id", AppFeature.builder()
                .featureId("face-id")
                .nameEn("Face ID Setup")
                .nameDe("Face ID einrichten")
                .category(FeatureCategory.SECURITY)
                .description("Enable facial recognition for quick and secure login")
                .platformAvailability(Platform.IOS)
                .minAppVersion("4.0.0")
                .deepLink("acmebank://settings/security/faceid")
                .keywords(List.of("face", "facial", "biometric", "login", "authentication"))
                .build());

        features.put("fingerprint", AppFeature.builder()
                .featureId("fingerprint")
                .nameEn("Fingerprint Setup")
                .nameDe("Fingerabdruck einrichten")
                .category(FeatureCategory.SECURITY)
                .description("Enable fingerprint authentication for quick and secure login")
                .platformAvailability(Platform.ANDROID)
                .deepLink("acmebank://settings/security/fingerprint")
                .keywords(List.of("fingerprint", "biometric", "touch", "login", "authentication"))
                .build());

        features.put("change-pin", AppFeature.builder()
                .featureId("change-pin")
                .nameEn("Change App PIN")
                .nameDe("App-PIN ändern")
                .category(FeatureCategory.SECURITY)
                .description("Change your 6-digit app PIN for login")
                .deepLink("acmebank://settings/security/pin")
                .keywords(List.of("PIN", "password", "code", "change", "reset"))
                .build());

        features.put("reset-password", AppFeature.builder()
                .featureId("reset-password")
                .nameEn("Reset Password")
                .nameDe("Passwort zurücksetzen")
                .category(FeatureCategory.SECURITY)
                .description("Reset your online banking password")
                .keywords(List.of("password", "forgot", "reset", "recover", "access"))
                .build());

        features.put("two-factor", AppFeature.builder()
                .featureId("two-factor")
                .nameEn("Two-Factor Authentication")
                .nameDe("Zwei-Faktor-Authentifizierung")
                .category(FeatureCategory.SECURITY)
                .description("Enable additional security with SMS or authenticator app")
                .deepLink("acmebank://settings/security/2fa")
                .keywords(List.of("2FA", "two-factor", "MFA", "authenticator", "security"))
                .build());

        features.put("logout", AppFeature.builder()
                .featureId("logout")
                .nameEn("Log Out")
                .nameDe("Abmelden")
                .category(FeatureCategory.SECURITY)
                .description("Sign out of the mobile app")
                .keywords(List.of("logout", "sign out", "exit", "log off"))
                .build());

        features.put("session-timeout", AppFeature.builder()
                .featureId("session-timeout")
                .nameEn("Session Timeout Settings")
                .nameDe("Sitzungs-Timeout-Einstellungen")
                .category(FeatureCategory.SECURITY)
                .description("Configure automatic logout timer")
                .deepLink("acmebank://settings/security/timeout")
                .keywords(List.of("timeout", "session", "auto logout", "inactivity"))
                .build());
    }

    private void initializeTransferFeatures() {
        features.put("transfer-internal", AppFeature.builder()
                .featureId("transfer-internal")
                .nameEn("Internal Transfer")
                .nameDe("Interne Überweisung")
                .category(FeatureCategory.TRANSFERS)
                .description("Transfer money between your own Acme Bank accounts")
                .deepLink("acmebank://transfer/internal")
                .keywords(List.of("transfer", "send", "move money", "own account", "internal"))
                .build());

        features.put("transfer-sepa", AppFeature.builder()
                .featureId("transfer-sepa")
                .nameEn("SEPA Transfer")
                .nameDe("SEPA-Überweisung")
                .category(FeatureCategory.TRANSFERS)
                .description("Send money to any European bank account")
                .deepLink("acmebank://transfer/sepa")
                .keywords(List.of("SEPA", "transfer", "send money", "Europe", "IBAN", "payment"))
                .build());

        features.put("standing-order", AppFeature.builder()
                .featureId("standing-order")
                .nameEn("Standing Order")
                .nameDe("Dauerauftrag")
                .category(FeatureCategory.TRANSFERS)
                .description("Set up recurring automatic transfers")
                .deepLink("acmebank://transfer/standing")
                .keywords(List.of("standing order", "recurring", "automatic", "scheduled", "regular"))
                .build());

        features.put("add-recipient", AppFeature.builder()
                .featureId("add-recipient")
                .nameEn("Add New Recipient")
                .nameDe("Neuen Empfänger hinzufügen")
                .category(FeatureCategory.TRANSFERS)
                .description("Save a new payment recipient for quick transfers")
                .deepLink("acmebank://transfer/recipients/add")
                .keywords(List.of("recipient", "beneficiary", "contact", "save", "payee"))
                .build());

        features.put("instant-transfer", AppFeature.builder()
                .featureId("instant-transfer")
                .nameEn("Instant Transfer")
                .nameDe("Sofortüberweisung")
                .category(FeatureCategory.TRANSFERS)
                .description("Send money instantly 24/7 with SEPA Instant")
                .minAppVersion("5.0.0")
                .deepLink("acmebank://transfer/instant")
                .keywords(List.of("instant", "immediate", "fast", "quick", "real-time"))
                .build());

        features.put("request-money", AppFeature.builder()
                .featureId("request-money")
                .nameEn("Request Money")
                .nameDe("Geld anfordern")
                .category(FeatureCategory.TRANSFERS)
                .description("Send a payment request to someone")
                .minAppVersion("5.2.0")
                .deepLink("acmebank://transfer/request")
                .keywords(List.of("request", "ask for money", "payment request", "collect"))
                .build());
    }

    private void initializeAccountFeatures() {
        features.put("view-balance", AppFeature.builder()
                .featureId("view-balance")
                .nameEn("View Balance")
                .nameDe("Kontostand anzeigen")
                .category(FeatureCategory.ACCOUNTS)
                .description("Check your current account balance")
                .deepLink("acmebank://accounts/balance")
                .keywords(List.of("balance", "amount", "how much", "money", "funds"))
                .build());

        features.put("transaction-history", AppFeature.builder()
                .featureId("transaction-history")
                .nameEn("Transaction History")
                .nameDe("Transaktionsverlauf")
                .category(FeatureCategory.ACCOUNTS)
                .description("View past transactions and payments")
                .deepLink("acmebank://accounts/transactions")
                .keywords(List.of("transactions", "history", "past", "payments", "movements"))
                .build());

        features.put("download-statement", AppFeature.builder()
                .featureId("download-statement")
                .nameEn("Download Statement")
                .nameDe("Kontoauszug herunterladen")
                .category(FeatureCategory.ACCOUNTS)
                .description("Download PDF account statements")
                .deepLink("acmebank://accounts/statements")
                .keywords(List.of("statement", "PDF", "download", "export", "document"))
                .build());

        features.put("find-iban", AppFeature.builder()
                .featureId("find-iban")
                .nameEn("Find IBAN")
                .nameDe("IBAN finden")
                .category(FeatureCategory.ACCOUNTS)
                .description("View your account IBAN and BIC")
                .deepLink("acmebank://accounts/details")
                .keywords(List.of("IBAN", "BIC", "account number", "bank details", "routing"))
                .build());

        features.put("account-details", AppFeature.builder()
                .featureId("account-details")
                .nameEn("Account Details")
                .nameDe("Kontodetails")
                .category(FeatureCategory.ACCOUNTS)
                .description("View full account information and settings")
                .deepLink("acmebank://accounts/details")
                .keywords(List.of("details", "information", "account info", "settings"))
                .build());
    }

    private void initializeCardFeatures() {
        features.put("block-card", AppFeature.builder()
                .featureId("block-card")
                .nameEn("Block Card")
                .nameDe("Karte sperren")
                .category(FeatureCategory.CARDS)
                .description("Temporarily or permanently block your card")
                .deepLink("acmebank://cards/block")
                .keywords(List.of("block", "freeze", "stop", "disable", "lost", "stolen"))
                .build());

        features.put("unblock-card", AppFeature.builder()
                .featureId("unblock-card")
                .nameEn("Unblock Card")
                .nameDe("Karte entsperren")
                .category(FeatureCategory.CARDS)
                .description("Reactivate a temporarily blocked card")
                .deepLink("acmebank://cards/unblock")
                .keywords(List.of("unblock", "reactivate", "enable", "activate"))
                .build());

        features.put("view-card-pin", AppFeature.builder()
                .featureId("view-card-pin")
                .nameEn("View Card PIN")
                .nameDe("Karten-PIN anzeigen")
                .category(FeatureCategory.CARDS)
                .description("View your card PIN securely in the app")
                .minAppVersion("5.0.0")
                .deepLink("acmebank://cards/pin")
                .keywords(List.of("PIN", "card PIN", "ATM", "forgot PIN", "show PIN"))
                .build());

        features.put("card-limits", AppFeature.builder()
                .featureId("card-limits")
                .nameEn("Card Limits")
                .nameDe("Kartenlimits")
                .category(FeatureCategory.CARDS)
                .description("View and adjust your card spending limits")
                .deepLink("acmebank://cards/limits")
                .keywords(List.of("limit", "spending", "maximum", "daily", "ATM limit"))
                .build());

        features.put("contactless-settings", AppFeature.builder()
                .featureId("contactless-settings")
                .nameEn("Contactless Settings")
                .nameDe("Kontaktlos-Einstellungen")
                .category(FeatureCategory.CARDS)
                .description("Enable or disable contactless payments")
                .deepLink("acmebank://cards/contactless")
                .keywords(List.of("contactless", "NFC", "tap to pay", "wireless", "touch"))
                .build());

        features.put("online-payments", AppFeature.builder()
                .featureId("online-payments")
                .nameEn("Online Payments")
                .nameDe("Online-Zahlungen")
                .category(FeatureCategory.CARDS)
                .description("Enable or disable online/e-commerce transactions")
                .deepLink("acmebank://cards/online")
                .keywords(List.of("online", "e-commerce", "internet", "shopping", "web"))
                .build());

        features.put("add-to-wallet", AppFeature.builder()
                .featureId("add-to-wallet")
                .nameEn("Add to Apple/Google Wallet")
                .nameDe("Zu Apple/Google Wallet hinzufügen")
                .category(FeatureCategory.CARDS)
                .description("Add your card to mobile wallet for contactless payments")
                .minAppVersion("4.5.0")
                .deepLink("acmebank://cards/wallet")
                .keywords(List.of("Apple Pay", "Google Pay", "wallet", "mobile payment", "phone payment"))
                .build());
    }

    private void initializeSettingsFeatures() {
        features.put("push-notifications", AppFeature.builder()
                .featureId("push-notifications")
                .nameEn("Push Notifications")
                .nameDe("Push-Benachrichtigungen")
                .category(FeatureCategory.SETTINGS)
                .description("Configure which notifications you receive")
                .deepLink("acmebank://settings/notifications")
                .keywords(List.of("notification", "push", "alert", "message", "reminder"))
                .build());

        features.put("transaction-alerts", AppFeature.builder()
                .featureId("transaction-alerts")
                .nameEn("Transaction Alerts")
                .nameDe("Transaktionsbenachrichtigungen")
                .category(FeatureCategory.SETTINGS)
                .description("Get notified about account activity")
                .deepLink("acmebank://settings/alerts")
                .keywords(List.of("alert", "transaction", "activity", "notification", "spending"))
                .build());

        features.put("language-settings", AppFeature.builder()
                .featureId("language-settings")
                .nameEn("Language Settings")
                .nameDe("Spracheinstellungen")
                .category(FeatureCategory.SETTINGS)
                .description("Change the app display language")
                .deepLink("acmebank://settings/language")
                .keywords(List.of("language", "German", "English", "Deutsch", "display"))
                .build());

        features.put("profile-settings", AppFeature.builder()
                .featureId("profile-settings")
                .nameEn("Profile Settings")
                .nameDe("Profilseinstellungen")
                .category(FeatureCategory.SETTINGS)
                .description("Update your personal information and preferences")
                .deepLink("acmebank://settings/profile")
                .keywords(List.of("profile", "personal", "name", "email", "phone", "address"))
                .build());

        features.put("appearance-settings", AppFeature.builder()
                .featureId("appearance-settings")
                .nameEn("Appearance Settings")
                .nameDe("Darstellungseinstellungen")
                .category(FeatureCategory.SETTINGS)
                .description("Choose light, dark, or system theme")
                .deepLink("acmebank://settings/appearance")
                .keywords(List.of("dark mode", "light mode", "theme", "appearance", "display"))
                .build());

        features.put("data-privacy", AppFeature.builder()
                .featureId("data-privacy")
                .nameEn("Data & Privacy")
                .nameDe("Daten & Datenschutz")
                .category(FeatureCategory.SETTINGS)
                .description("Manage your data sharing and privacy preferences")
                .deepLink("acmebank://settings/privacy")
                .keywords(List.of("privacy", "data", "GDPR", "consent", "tracking"))
                .build());

        features.put("quick-balance", AppFeature.builder()
                .featureId("quick-balance")
                .nameEn("Quick Balance Widget")
                .nameDe("Schnellkontostand-Widget")
                .category(FeatureCategory.SETTINGS)
                .description("Enable balance display on lock screen or widget")
                .deepLink("acmebank://settings/widget")
                .keywords(List.of("widget", "quick balance", "lock screen", "home screen"))
                .build());

        features.put("default-account", AppFeature.builder()
                .featureId("default-account")
                .nameEn("Default Account")
                .nameDe("Standardkonto")
                .category(FeatureCategory.SETTINGS)
                .description("Set your default account for transactions")
                .deepLink("acmebank://settings/default-account")
                .keywords(List.of("default", "primary", "main account", "preferred"))
                .build());

        features.put("app-icon", AppFeature.builder()
                .featureId("app-icon")
                .nameEn("App Icon")
                .nameDe("App-Symbol")
                .category(FeatureCategory.SETTINGS)
                .description("Choose a custom app icon")
                .platformAvailability(Platform.IOS)
                .minAppVersion("5.5.0")
                .keywords(List.of("icon", "app icon", "customize", "personalize"))
                .build());

        features.put("clear-cache", AppFeature.builder()
                .featureId("clear-cache")
                .nameEn("Clear Cache")
                .nameDe("Cache leeren")
                .category(FeatureCategory.SETTINGS)
                .description("Clear app cache to free up storage")
                .deepLink("acmebank://settings/storage")
                .keywords(List.of("cache", "storage", "clear", "memory", "space"))
                .build());
    }

    private void initializeSupportFeatures() {
        features.put("contact-support", AppFeature.builder()
                .featureId("contact-support")
                .nameEn("Contact Support")
                .nameDe("Support kontaktieren")
                .category(FeatureCategory.SUPPORT)
                .description("Reach our customer support team")
                .deepLink("acmebank://support/contact")
                .keywords(List.of("support", "help", "contact", "call", "phone", "email"))
                .build());

        features.put("in-app-chat", AppFeature.builder()
                .featureId("in-app-chat")
                .nameEn("Chat with Support")
                .nameDe("Mit Support chatten")
                .category(FeatureCategory.SUPPORT)
                .description("Start a live chat with our support team")
                .deepLink("acmebank://support/chat")
                .keywords(List.of("chat", "message", "live chat", "instant", "text"))
                .build());

        features.put("faq", AppFeature.builder()
                .featureId("faq")
                .nameEn("FAQs")
                .nameDe("Häufige Fragen")
                .category(FeatureCategory.SUPPORT)
                .description("Browse frequently asked questions")
                .deepLink("acmebank://support/faq")
                .keywords(List.of("FAQ", "questions", "help", "answers", "how to"))
                .build());

        features.put("report-issue", AppFeature.builder()
                .featureId("report-issue")
                .nameEn("Report an Issue")
                .nameDe("Problem melden")
                .category(FeatureCategory.SUPPORT)
                .description("Report a bug or technical issue")
                .deepLink("acmebank://support/report")
                .keywords(List.of("report", "bug", "issue", "problem", "error", "feedback"))
                .build());
    }

    @Override
    public Optional<AppFeature> getFeature(String featureId) {
        return Optional.ofNullable(features.get(featureId));
    }

    @Override
    public List<AppFeature> listByCategory(FeatureCategory category) {
        return features.values().stream()
                .filter(f -> f.getCategory() == category)
                .collect(Collectors.toList());
    }

    @Override
    public List<AppFeature> listByPlatform(Platform platform) {
        return features.values().stream()
                .filter(f -> f.isAvailableOn(platform))
                .collect(Collectors.toList());
    }

    @Override
    public List<AppFeature> search(String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>(features.values());
        }
        return features.values().stream()
                .filter(f -> f.matchesQuery(query))
                .collect(Collectors.toList());
    }

    @Override
    public List<AppFeature> search(String query, Platform platform) {
        return search(query).stream()
                .filter(f -> f.isAvailableOn(platform))
                .collect(Collectors.toList());
    }

    @Override
    public List<AppFeature> listAll() {
        return new ArrayList<>(features.values());
    }

    @Override
    public List<AppFeature> listWithDeepLinks() {
        return features.values().stream()
                .filter(f -> f.getDeepLink() != null)
                .collect(Collectors.toList());
    }

    @Override
    public int getFeatureCount() {
        return features.size();
    }
}
