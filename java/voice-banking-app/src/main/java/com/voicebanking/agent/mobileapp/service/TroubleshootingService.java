package com.voicebanking.agent.mobileapp.service;

import com.voicebanking.agent.mobileapp.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service for troubleshooting mobile app issues.
 * 
 * Provides step-by-step solutions for common app problems
 * and escalation paths when issues cannot be resolved.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class TroubleshootingService {
    private static final Logger log = LoggerFactory.getLogger(TroubleshootingService.class);
    private static final String SUPPORT_PHONE = "+49 800 123-4567";
    private static final String SUPPORT_HOURS = "Monday-Friday, 09:00-18:00 CET";

    private final Map<IssueType, List<TroubleshootingSolution>> solutions = new EnumMap<>(IssueType.class);

    @PostConstruct
    public void init() {
        initializeLoginSolutions();
        initializePerformanceSolutions();
        initializeSecuritySolutions();
        initializeFeatureSolutions();
        initializeUpdateSolutions();
        initializeNetworkSolutions();
        initializeNotificationSolutions();
        
        log.info("Initialized troubleshooting solutions for {} issue types", solutions.size());
    }

    /**
     * Get troubleshooting solution for an issue.
     * 
     * @param issueType the type of issue
     * @param description optional description for better matching
     * @return best matching solution
     */
    public TroubleshootingSolution getTroubleshootingSolution(IssueType issueType, String description) {
        List<TroubleshootingSolution> typeSolutions = solutions.getOrDefault(issueType, List.of());
        
        if (typeSolutions.isEmpty()) {
            return createGenericSolution(issueType, description);
        }

        // If description provided, try to find more specific match
        if (description != null && !description.isBlank()) {
            String lower = description.toLowerCase();
            for (TroubleshootingSolution solution : typeSolutions) {
                if (solution.getIssueDescription() != null && 
                    lower.contains(solution.getIssueDescription().toLowerCase())) {
                    return solution;
                }
            }
        }

        // Return first (most common) solution for this type
        return typeSolutions.get(0);
    }

    /**
     * Get troubleshooting solution by detecting issue type from description.
     */
    public TroubleshootingSolution getTroubleshootingSolution(String description) {
        IssueType detectedType = IssueType.fromDescription(description);
        return getTroubleshootingSolution(detectedType, description);
    }

    /**
     * Get all solutions for an issue type.
     */
    public List<TroubleshootingSolution> getAllSolutions(IssueType issueType) {
        return solutions.getOrDefault(issueType, List.of());
    }

    private TroubleshootingSolution createGenericSolution(IssueType issueType, String description) {
        return TroubleshootingSolution.builder()
                .issueType(issueType)
                .issueDescription(description)
                .addStep(1, "Close the app completely and reopen it")
                .addStep(2, "Check your internet connection")
                .addStep(3, "Make sure your app is updated to the latest version")
                .addStep(4, "If the issue persists, try restarting your phone")
                .escalationMessage("If these steps don't help, please contact our support team.")
                .supportContact(SUPPORT_PHONE)
                .build();
    }

    private void initializeLoginSolutions() {
        List<TroubleshootingSolution> loginSolutions = new ArrayList<>();

        // Wrong PIN / Locked out
        loginSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.LOGIN)
                .issueDescription("locked out")
                .addStep(1, "Wait 30 minutes for the lockout to expire")
                .addStep(2, "Try logging in again with your correct PIN")
                .addStep(3, "If you've forgotten your PIN, tap 'Forgot PIN' on the login screen")
                .addStep(4, "Follow the steps to reset your PIN using your registered email")
                .escalationMessage("If you're still locked out, please contact our support team with your customer ID.")
                .supportContact(SUPPORT_PHONE)
                .helpArticleUrl("https://help.acmebank.example/locked-out")
                .build());

        // Forgot Password
        loginSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.LOGIN)
                .issueDescription("forgot password")
                .addStep(1, "Tap 'Forgot Password' on the login screen")
                .addStep(2, "Enter your registered email address")
                .addStep(3, "Check your email for the reset link")
                .addStep(4, "Follow the link to create a new password")
                .addStep(5, "Log in with your new password")
                .helpArticleUrl("https://help.acmebank.example/reset-password")
                .build());

        // Can't log in general
        loginSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.LOGIN)
                .issueDescription("can't log in")
                .addStep(1, "Check that you're entering the correct username or customer ID")
                .addStep(2, "Make sure Caps Lock is off when entering your password")
                .addStep(3, "Check your internet connection")
                .addStep(4, "Try resetting your password using 'Forgot Password'")
                .escalationMessage("If you still can't log in, contact support with your customer ID ready.")
                .supportContact(SUPPORT_PHONE)
                .build());

        solutions.put(IssueType.LOGIN, loginSolutions);
    }

    private void initializePerformanceSolutions() {
        List<TroubleshootingSolution> perfSolutions = new ArrayList<>();

        // App crashing
        perfSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.PERFORMANCE)
                .issueDescription("crash")
                .addStep(1, "Force close the app completely")
                .addStep(2, "Clear the app cache in Settings → Storage")
                .addStep(3, "Check for app updates in your app store")
                .addStep(4, "If crashing continues, uninstall and reinstall the app")
                .addStep(5, "Make sure your phone has at least 500MB free space")
                .helpArticleUrl("https://help.acmebank.example/app-crashing")
                .build());

        // App slow
        perfSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.PERFORMANCE)
                .issueDescription("slow")
                .addStep(1, "Check your internet connection speed")
                .addStep(2, "Clear the app cache in Settings → Storage")
                .addStep(3, "Close other apps running in the background")
                .addStep(4, "Restart your phone")
                .helpArticleUrl("https://help.acmebank.example/app-slow")
                .build());

        // App freezing
        perfSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.PERFORMANCE)
                .issueDescription("freeze")
                .addStep(1, "Wait 30 seconds to see if it responds")
                .addStep(2, "Force close the app if frozen")
                .addStep(3, "Clear app cache and restart")
                .addStep(4, "If freezing continues, reinstall the app")
                .helpArticleUrl("https://help.acmebank.example/app-frozen")
                .build());

        solutions.put(IssueType.PERFORMANCE, perfSolutions);
    }

    private void initializeSecuritySolutions() {
        List<TroubleshootingSolution> secSolutions = new ArrayList<>();

        // Touch ID / Face ID not working
        secSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.SECURITY)
                .issueDescription("biometric")
                .addStep(1, "Make sure biometrics are enabled in your phone's settings")
                .addStep(2, "Go to app Settings → Security → Biometric Login")
                .addStep(3, "Toggle off and then on again")
                .addStep(4, "Re-authenticate with your PIN")
                .addStep(5, "Try using biometrics to log in again")
                .helpArticleUrl("https://help.acmebank.example/biometric-issues")
                .build());

        // Touch ID specifically
        secSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.SECURITY)
                .issueDescription("touch id")
                .addStep(1, "Clean your finger and the Touch ID sensor")
                .addStep(2, "Re-register your fingerprint in iPhone Settings")
                .addStep(3, "Go to app Settings → Security → Touch ID")
                .addStep(4, "Disable and re-enable Touch ID for the app")
                .helpArticleUrl("https://help.acmebank.example/touch-id-issues")
                .build());

        // Face ID specifically
        secSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.SECURITY)
                .issueDescription("face id")
                .addStep(1, "Make sure nothing is covering the camera or your face")
                .addStep(2, "Try in better lighting conditions")
                .addStep(3, "Re-register Face ID in iPhone Settings")
                .addStep(4, "Disable and re-enable Face ID for the app")
                .helpArticleUrl("https://help.acmebank.example/face-id-issues")
                .build());

        solutions.put(IssueType.SECURITY, secSolutions);
    }

    private void initializeFeatureSolutions() {
        List<TroubleshootingSolution> featureSolutions = new ArrayList<>();

        // Feature not working
        featureSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.FEATURE)
                .issueDescription("not working")
                .addStep(1, "Close and reopen the app")
                .addStep(2, "Check if there's a system maintenance notification")
                .addStep(3, "Make sure your app is up to date")
                .addStep(4, "Try the feature again")
                .escalationMessage("If the feature still doesn't work, please report it through Help → Report Issue.")
                .supportContact(SUPPORT_PHONE)
                .build());

        // Feature missing
        featureSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.FEATURE)
                .issueDescription("missing")
                .addStep(1, "Update your app to the latest version")
                .addStep(2, "Check if the feature requires a minimum app version")
                .addStep(3, "Some features may not be available for your account type")
                .addStep(4, "Contact support if you believe you should have access")
                .helpArticleUrl("https://help.acmebank.example/feature-availability")
                .build());

        solutions.put(IssueType.FEATURE, featureSolutions);
    }

    private void initializeUpdateSolutions() {
        List<TroubleshootingSolution> updateSolutions = new ArrayList<>();

        // Can't update app
        updateSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.UPDATE)
                .issueDescription("update")
                .addStep(1, "Check your internet connection")
                .addStep(2, "Make sure you have enough storage space on your phone")
                .addStep(3, "Restart your phone and try again")
                .addStep(4, "If using Wi-Fi, try switching to mobile data or vice versa")
                .addStep(5, "Clear the app store cache and try again")
                .helpArticleUrl("https://help.acmebank.example/app-update")
                .build());

        // Version too old
        updateSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.UPDATE)
                .issueDescription("version")
                .addStep(1, "Go to your app store (App Store or Google Play)")
                .addStep(2, "Search for Acme Bank app")
                .addStep(3, "Tap Update if available")
                .addStep(4, "If no update shows, try uninstalling and reinstalling")
                .helpArticleUrl("https://help.acmebank.example/minimum-version")
                .build());

        solutions.put(IssueType.UPDATE, updateSolutions);
    }

    private void initializeNetworkSolutions() {
        List<TroubleshootingSolution> networkSolutions = new ArrayList<>();

        // Connection issues
        networkSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.NETWORK)
                .issueDescription("connection")
                .addStep(1, "Check if your Wi-Fi or mobile data is turned on")
                .addStep(2, "Try switching between Wi-Fi and mobile data")
                .addStep(3, "Turn airplane mode on and off")
                .addStep(4, "Restart your router if on Wi-Fi")
                .addStep(5, "Restart your phone")
                .helpArticleUrl("https://help.acmebank.example/connection-issues")
                .build());

        // Offline mode
        networkSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.NETWORK)
                .issueDescription("offline")
                .addStep(1, "The app has limited offline functionality")
                .addStep(2, "Connect to the internet to access all features")
                .addStep(3, "Some cached data may still be visible offline")
                .helpArticleUrl("https://help.acmebank.example/offline-mode")
                .build());

        // Timeout
        networkSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.NETWORK)
                .issueDescription("timeout")
                .addStep(1, "Your connection may be slow or unstable")
                .addStep(2, "Try moving to an area with better signal")
                .addStep(3, "Wait a moment and try again")
                .addStep(4, "If on public Wi-Fi, try using mobile data instead")
                .helpArticleUrl("https://help.acmebank.example/timeout-errors")
                .build());

        solutions.put(IssueType.NETWORK, networkSolutions);
    }

    private void initializeNotificationSolutions() {
        List<TroubleshootingSolution> notifSolutions = new ArrayList<>();

        // Not receiving notifications
        notifSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.NOTIFICATION)
                .issueDescription("notification")
                .addStep(1, "Check that notifications are enabled in your phone's settings")
                .addStep(2, "Go to app Settings → Notifications and enable them")
                .addStep(3, "Make sure the app isn't in power saving mode")
                .addStep(4, "Try disabling and re-enabling notifications")
                .helpArticleUrl("https://help.acmebank.example/notifications")
                .build());

        // Too many notifications
        notifSolutions.add(TroubleshootingSolution.builder()
                .issueType(IssueType.NOTIFICATION)
                .issueDescription("too many")
                .addStep(1, "Go to app Settings → Notifications")
                .addStep(2, "Choose which types of notifications you want")
                .addStep(3, "Disable marketing or promotional notifications")
                .addStep(4, "Keep security alerts enabled for your protection")
                .helpArticleUrl("https://help.acmebank.example/notification-settings")
                .build());

        solutions.put(IssueType.NOTIFICATION, notifSolutions);
    }
}
