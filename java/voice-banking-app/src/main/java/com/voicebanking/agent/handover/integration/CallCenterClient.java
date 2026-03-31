package com.voicebanking.agent.handover.integration;

import com.voicebanking.agent.handover.domain.HandoverContext;
import com.voicebanking.agent.handover.domain.HandoverResult;
import com.voicebanking.agent.handover.domain.QueueStatus;

/**
 * Interface for call center integration.
 * Production implementations would connect to real call center systems
 * (Genesys, Amazon Connect, Avaya, etc.)
 */
public interface CallCenterClient {

    /**
     * Check if human agents are available in the specified queue.
     * @param queueId The queue identifier (e.g., "general", "private-banking")
     * @return true if at least one agent is available
     */
    boolean checkAvailability(String queueId);

    /**
     * Get current status of a queue including agent count and wait time.
     * @param queueId The queue identifier
     * @return Current queue status
     */
    QueueStatus getQueueStatus(String queueId);

    /**
     * Route a handover request to the specified queue.
     * @param queueId The target queue
     * @param context The handover context with conversation details
     * @return Result of the routing operation
     */
    HandoverResult routeHandover(String queueId, HandoverContext context);

    /**
     * Send context payload to the agent desktop system.
     * @param ticketId The handover ticket ID
     * @param context The full context payload
     * @return true if context was successfully sent
     */
    boolean sendContext(String ticketId, HandoverContext context);

    /**
     * Cancel a pending handover request.
     * @param ticketId The handover ticket ID to cancel
     * @return true if cancellation was successful
     */
    boolean cancelHandover(String ticketId);
}
