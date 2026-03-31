package com.voicebanking.bfa.gateway.auth;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves Google-signed identity tokens for private Cloud Run adapter calls.
 */
@Component
public class CloudRunIdTokenService {

    private static final Logger log = LoggerFactory.getLogger(CloudRunIdTokenService.class);
    public static final String SERVERLESS_AUTHORIZATION_HEADER = "X-Serverless-Authorization";

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();
    private final TokenFactory tokenFactory;

    public CloudRunIdTokenService() {
        this(new GoogleAuthTokenFactory());
    }

    CloudRunIdTokenService(TokenFactory tokenFactory) {
        this.tokenFactory = tokenFactory;
    }

    public Optional<String> serverlessAuthorizationHeaderValue(String url) {
        return audienceFor(url)
                .flatMap(this::tokenForAudience)
                .map(token -> "Bearer " + token);
    }

    static Optional<String> audienceFor(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !host.endsWith(".run.app")) {
                return Optional.empty();
            }
            String scheme = uri.getScheme() == null || uri.getScheme().isBlank()
                    ? "https"
                    : uri.getScheme();
            return Optional.of(scheme + "://" + host);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<String> tokenForAudience(String audience) {
        try {
            CachedToken cached = cache.get(audience);
            if (cached != null && cached.isUsable()) {
                return Optional.of(cached.value());
            }

            CachedToken refreshed = tokenFactory.fetch(audience);
            cache.put(audience, refreshed);
            return Optional.of(refreshed.value());
        } catch (IOException ex) {
            log.warn("Unable to mint Cloud Run identity token for {}: {}", audience, ex.getMessage());
            return Optional.empty();
        }
    }

    record CachedToken(String value, Instant expiresAt) {
        boolean isUsable() {
            return expiresAt == null || expiresAt.isAfter(Instant.now().plusSeconds(60));
        }
    }

    interface TokenFactory {
        CachedToken fetch(String audience) throws IOException;
    }

    static final class GoogleAuthTokenFactory implements TokenFactory {
        @Override
        public CachedToken fetch(String audience) throws IOException {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            if (!(credentials instanceof IdTokenProvider provider)) {
                throw new IOException("Application default credentials do not support ID tokens");
            }

            IdTokenCredentials idTokenCredentials = IdTokenCredentials.newBuilder()
                    .setIdTokenProvider(provider)
                    .setTargetAudience(audience)
                    .build();
            AccessToken token = idTokenCredentials.refreshAccessToken();
            Instant expiresAt = token.getExpirationTime() == null
                    ? Instant.now().plusSeconds(300)
                    : token.getExpirationTime().toInstant();
            return new CachedToken(token.getTokenValue(), expiresAt);
        }
    }
}
