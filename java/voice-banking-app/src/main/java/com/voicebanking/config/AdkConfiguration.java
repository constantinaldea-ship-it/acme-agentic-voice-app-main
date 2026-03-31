package com.voicebanking.config;

import com.google.adk.agents.LlmAgent;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.tools.FunctionTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * ADK Configuration
 * 
 * <p>Configures the Google Agent Development Kit (ADK) for voice banking.
 * Active in 'cloud' profile only.</p>
 * 
 * <p>Components configured:
 * <ul>
 *   <li>LlmAgent - Main agent with Gemini model and tools</li>
 *   <li>SessionService - In-memory session management</li>
 *   <li>Runner - Agent execution engine</li>
 * </ul>
 * </p>
 */
@Configuration
@Profile("cloud")
public class AdkConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AdkConfiguration.class);

    private static final String SYSTEM_PROMPT = """
        You are a helpful voice banking assistant. Your capabilities include:
        
        1. **Balance Inquiries** - Check account balances using the getBalance tool
        2. **Account Listing** - List all user accounts using the listAccounts tool
        3. **Transaction Queries** - Search recent transactions using the queryTransactions tool
        4. **Branch & ATM Finder** - Find nearby branches and ATMs using the findNearbyBranches tool
        
        ## Important Guidelines:
        
        - You are READ-ONLY. You cannot perform transfers, payments, or any write operations.
        - If a user requests a transaction (transfer, payment, bill pay), politely explain that
          you can only provide information and they should use the banking app or website.
        - Always be helpful and concise in your responses.
        - When providing balances, include the currency and format amounts clearly.
        - For transaction queries, summarize the results in a user-friendly way.
        - If the user's request is unclear, ask for clarification.
        - If asked about something outside of banking, politely redirect to banking-related assistance.
        
        ## Response Style:
        
        - Be conversational but professional
        - Keep responses concise (voice-friendly)
        - Use natural language, avoid technical jargon
        - Confirm what action you're taking before executing tools
        """;

    @Value("${voice-banking.llm.model:gemini-2.0-flash}")
    private String modelName;

    /**
     * Creates the main Voice Banking Agent with ADK.
     */
    @Bean
    public LlmAgent voiceBankingAgent(List<FunctionTool> tools) {
        log.info("Creating Voice Banking Agent with model: {}", modelName);
        
        LlmAgent agent = LlmAgent.builder()
            .name("voice-banking-assistant")
            .description("Voice-enabled banking assistant for balance inquiries and transaction queries")
            .model(modelName)
            .instruction(SYSTEM_PROMPT)
            .tools(tools)
            .build();
        
        log.info("Voice Banking Agent created with {} tools", tools.size());
        
        return agent;
    }

    /**
     * Creates the session service for managing conversation state.
     */
    @Bean
    public BaseSessionService sessionService() {
        log.info("Creating InMemorySessionService");
        return new InMemorySessionService();
    }

    /**
     * Creates the agent runner for executing agent interactions.
     */
    @Bean
    public Runner agentRunner(LlmAgent voiceBankingAgent) {
        log.info("Creating InMemoryRunner for agent execution");
        return new InMemoryRunner(voiceBankingAgent, "voice-banking");
    }
}
