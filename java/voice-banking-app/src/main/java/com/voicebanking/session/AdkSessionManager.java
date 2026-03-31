package com.voicebanking.session;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages conversational context for multi-turn dialogues.
 * Stores session history, previous intents, and tool execution results.
 * 
 * <p>Replaces TypeScript memory modules from packages/agent-memory/
 * (see ADR-J003 Memory Module Deprecation).
 * 
 * <p><b>Features:</b>
 * <ul>
 *   <li>In-memory session storage with TTL-based expiration</li>
 *   <li>Conversation history (turns with transcript/intent/result)</li>
 *   <li>User preferences (default accountId, language, etc.)</li>
 *   <li>Thread-safe via ConcurrentHashMap</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * sessionManager.createSession("user-123");
 * sessionManager.addTurn("user-123", new ConversationTurn(
 *     "What is my balance?",
 *     "CHECK_BALANCE",
 *     "getBalance",
 *     Map.of("available", 1250.50)
 * ));
 * 
 * List<ConversationTurn> history = sessionManager.getSessionHistory("user-123");
 * // Use history for context in LLM prompt
 * }</pre>
 * 
 * @author Voice Banking Team
 * @version 1.0.0
 * @since Phase 2
 */
@Component
public class AdkSessionManager {

    private static final Duration DEFAULT_SESSION_TTL = Duration.ofMinutes(30);
    
    /**
     * Map of sessionId → SessionContext
     * Thread-safe for concurrent access from multiple requests.
     */
    private final Map<String, SessionContext> sessions = new ConcurrentHashMap<>();

    /**
     * Creates a new session with the given ID.
     * If a session with the same ID already exists, it is replaced.
     *
     * @param sessionId unique identifier for the session
     * @return the newly created SessionContext
     */
    public SessionContext createSession(String sessionId) {
        SessionContext context = new SessionContext(sessionId);
        sessions.put(sessionId, context);
        return context;
    }

    /**
     * Retrieves the SessionContext for the given sessionId.
     * Creates a new session if one doesn't exist.
     *
     * @param sessionId unique identifier for the session
     * @return the SessionContext (never null)
     */
    public SessionContext getOrCreateSession(String sessionId) {
        return sessions.computeIfAbsent(sessionId, SessionContext::new);
    }

    /**
     * Adds a conversation turn to the session history.
     *
     * @param sessionId unique identifier for the session
     * @param turn the conversation turn to add
     * @throws IllegalArgumentException if sessionId is null or session doesn't exist
     */
    public void addTurn(String sessionId, ConversationTurn turn) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        
        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        
        context.addTurn(turn);
        context.updateLastAccessTime();
    }

    /**
     * Retrieves the conversation history for the given session.
     *
     * @param sessionId unique identifier for the session
     * @return list of conversation turns (may be empty)
     * @throws IllegalArgumentException if sessionId is null or session doesn't exist
     */
    public List<ConversationTurn> getSessionHistory(String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId cannot be null");
        }
        
        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            throw new IllegalArgumentException("Session not found: " + sessionId);
        }
        
        context.updateLastAccessTime();
        return List.copyOf(context.getHistory());
    }

    /**
     * Sets a user preference in the session context.
     * Common preferences: "defaultAccountId", "language", "voiceEnabled"
     *
     * @param sessionId unique identifier for the session
     * @param key preference key
     * @param value preference value
     */
    public void setPreference(String sessionId, String key, Object value) {
        SessionContext context = sessions.get(sessionId);
        if (context != null) {
            context.setPreference(key, value);
            context.updateLastAccessTime();
        }
    }

    /**
     * Gets a user preference from the session context.
     *
     * @param sessionId unique identifier for the session
     * @param key preference key
     * @return the preference value, or null if not found
     */
    public Object getPreference(String sessionId, String key) {
        SessionContext context = sessions.get(sessionId);
        if (context != null) {
            context.updateLastAccessTime();
            return context.getPreference(key);
        }
        return null;
    }

    /**
     * Clears the session history and preferences.
     * The session still exists but is reset to initial state.
     *
     * @param sessionId unique identifier for the session
     */
    public void clearSession(String sessionId) {
        SessionContext context = sessions.get(sessionId);
        if (context != null) {
            context.clear();
        }
    }

    /**
     * Removes the session entirely.
     *
     * @param sessionId unique identifier for the session
     */
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Removes all expired sessions based on TTL.
     * Should be called periodically by a scheduled task.
     *
     * @return number of sessions removed
     */
    public int cleanupExpiredSessions() {
        Instant now = Instant.now();
        int removed = 0;
        
        for (Map.Entry<String, SessionContext> entry : sessions.entrySet()) {
            SessionContext context = entry.getValue();
            Duration age = Duration.between(context.getLastAccessTime(), now);
            
            if (age.compareTo(DEFAULT_SESSION_TTL) > 0) {
                sessions.remove(entry.getKey());
                removed++;
            }
        }
        
        return removed;
    }

    /**
     * Returns the number of active sessions.
     *
     * @return session count
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Checks if a session exists.
     *
     * @param sessionId unique identifier for the session
     * @return true if session exists, false otherwise
     */
    public boolean hasSession(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * Represents the context for a single user session.
     * Contains conversation history, preferences, and metadata.
     */
    public static class SessionContext {
        private final String sessionId;
        private final List<ConversationTurn> history = new CopyOnWriteArrayList<>();
        private final Map<String, Object> preferences = new ConcurrentHashMap<>();
        private final Instant createdAt;
        private volatile Instant lastAccessTime;

        public SessionContext(String sessionId) {
            this.sessionId = sessionId;
            this.createdAt = Instant.now();
            this.lastAccessTime = this.createdAt;
        }

        public String getSessionId() {
            return sessionId;
        }

        public List<ConversationTurn> getHistory() {
            return List.copyOf(history);
        }

        public void addTurn(ConversationTurn turn) {
            history.add(turn);
        }

        public void setPreference(String key, Object value) {
            preferences.put(key, value);
        }

        public Object getPreference(String key) {
            return preferences.get(key);
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public Instant getLastAccessTime() {
            return lastAccessTime;
        }

        public void updateLastAccessTime() {
            this.lastAccessTime = Instant.now();
        }

        public void clear() {
            history.clear();
            preferences.clear();
        }

        @Override
        public String toString() {
            return "SessionContext{" +
                    "sessionId='" + sessionId + '\'' +
                    ", turnCount=" + history.size() +
                    ", preferences=" + preferences.size() +
                    ", createdAt=" + createdAt +
                    ", lastAccessTime=" + lastAccessTime +
                    '}';
        }
    }

    /**
     * Represents a single turn in a conversation.
     * Stores user input, detected intent, tool execution, and result.
     */
    public record ConversationTurn(
            String transcript,
            String intent,
            String toolCalled,
            Object toolResult,
            Instant timestamp
    ) {
        public ConversationTurn(String transcript, String intent, String toolCalled, Object toolResult) {
            this(transcript, intent, toolCalled, toolResult, Instant.now());
        }

        /**
         * Formats this turn as a human-readable string for logging/display.
         *
         * @return formatted string
         */
        public String format() {
            return String.format("[%s] User: '%s' → Intent: %s → Tool: %s → Result: %s",
                    timestamp, transcript, intent, toolCalled, toolResult);
        }
    }
}
