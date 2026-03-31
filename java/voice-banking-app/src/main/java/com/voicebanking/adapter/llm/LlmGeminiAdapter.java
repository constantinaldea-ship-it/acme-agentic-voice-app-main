package com.voicebanking.adapter.llm;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Gemini LLM Adapter (Legacy - Raw Vertex AI SDK)
 * 
 * <p>Google Vertex AI Gemini model for intent detection and tool calling.
 * Active in 'cloud' profile only when ADK is disabled.</p>
 * 
 * <p><b>DEPRECATED:</b> Use {@link LlmAdkAdapter} instead. This adapter is 
 * kept for backwards compatibility and fallback scenarios.</p>
 * 
 * <p>Requires GOOGLE_APPLICATION_CREDENTIALS environment variable.</p>
 * 
 * @deprecated Use {@link LlmAdkAdapter} for ADK-based processing
 */
@Component("llmGeminiLegacyAdapter")
@Profile("cloud")
@ConditionalOnProperty(name = "voice-banking.adk.enabled", havingValue = "false", matchIfMissing = false)
@Deprecated(since = "0.2.0", forRemoval = false)
public class LlmGeminiAdapter implements LlmProvider {
    
    private static final Logger log = LoggerFactory.getLogger(LlmGeminiAdapter.class);
    
    @Value("${google.cloud.project-id}")
    private String projectId;
    
    @Value("${google.cloud.location:us-central1}")
    private String location;
    
    @Value("${voice-banking.llm.model:gemini-1.5-flash-002}")
    private String modelName;
    
    @Value("${voice-banking.llm.temperature:0.7}")
    private float temperature;
    
    @Value("${voice-banking.llm.max-output-tokens:1024}")
    private int maxOutputTokens;
    
    @Value("${voice-banking.llm.top-p:0.95}")
    private float topP;
    
    @Value("${voice-banking.llm.top-k:40}")
    private int topK;
    
    private final VertexAI vertexAI;
    private final GenerativeModel model;
    
    public LlmGeminiAdapter(
            @Value("${google.cloud.project-id}") String projectId,
            @Value("${google.cloud.location:us-central1}") String location,
            @Value("${voice-banking.llm.model:gemini-1.5-flash-002}") String modelName,
            @Value("${voice-banking.llm.temperature:0.7}") float temperature,
            @Value("${voice-banking.llm.max-output-tokens:1024}") int maxOutputTokens,
            @Value("${voice-banking.llm.top-p:0.95}") float topP,
            @Value("${voice-banking.llm.top-k:40}") int topK) 
            throws IOException {
        
        this.projectId = projectId;
        this.location = location;
        this.modelName = modelName;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.topP = topP;
        this.topK = topK;
        
        this.vertexAI = new VertexAI(projectId, location);
        
        // Configure generation parameters
        GenerationConfig generationConfig = GenerationConfig.newBuilder()
            .setTemperature(this.temperature)
            .setMaxOutputTokens(this.maxOutputTokens)
            .setTopP(this.topP)
            .setTopK(this.topK)
            .build();
        
        // Configure safety settings
        List<SafetySetting> safetySettings = Arrays.asList(
            SafetySetting.newBuilder()
                .setCategory(HarmCategory.HARM_CATEGORY_HARASSMENT)
                .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH)
                .build(),
            SafetySetting.newBuilder()
                .setCategory(HarmCategory.HARM_CATEGORY_HATE_SPEECH)
                .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH)
                .build(),
            SafetySetting.newBuilder()
                .setCategory(HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT)
                .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH)
                .build(),
            SafetySetting.newBuilder()
                .setCategory(HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT)
                .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_ONLY_HIGH)
                .build()
        );
        
        // Define tools for function calling
        List<Tool> tools = createTools();
        
        // Build model
        this.model = new GenerativeModel.Builder()
            .setModelName(modelName)
            .setVertexAi(vertexAI)
            .setGenerationConfig(generationConfig)
            .setSafetySettings(safetySettings)
            .setTools(tools)
            .build();
        
        log.info("LlmGeminiAdapter initialized with model: {} (project: {}, location: {})", 
            modelName, projectId, location);
    }
    
    @Override
    public LlmResponse process(String transcript, Map<String, Object> context) {
        log.debug("Gemini LLM: processing transcript '{}' (context size: {})", 
            transcript, context.size());
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Build system instruction
            String systemInstruction = buildSystemInstruction(context);
            
            // Create content
            Content userContent = ContentMaker.fromString(transcript);
            
            // Generate response with tool calling
            var response = model.generateContent(userContent);
            
            long durationMs = System.currentTimeMillis() - startTime;
            
            // Process response
            if (response.getCandidatesCount() == 0) {
                log.warn("Gemini LLM: no candidates in response");
                return LlmResponse.refusal("I'm unable to process your request at this time.");
            }
            
            var candidate = response.getCandidates(0);
            var content = candidate.getContent();
            
            // Check for function calls (tool calls)
            if (content.getPartsCount() > 0) {
                for (var part : content.getPartsList()) {
                    if (part.hasFunctionCall()) {
                        var functionCall = part.getFunctionCall();
                        String toolName = functionCall.getName();
                        Map<String, Object> args = extractFunctionArgs(functionCall);
                        
                        log.info("Gemini LLM: detected tool call '{}' (args: {}, duration: {}ms)", 
                            toolName, args, durationMs);
                        
                        return LlmResponse.toolCall(
                            new ToolCall(toolName, args),
                            "I'll " + getToolDescription(toolName) + " for you."
                        );
                    }
                    
                    if (part.hasText()) {
                        String text = part.getText();
                        log.info("Gemini LLM: generated text response (duration: {}ms)", durationMs);
                        return new LlmResponse(LlmResponse.ResponseType.DIRECT_RESPONSE, null, text, null);
                    }
                }
            }
            
            log.warn("Gemini LLM: unexpected response format");
            return LlmResponse.clarification(
                "I'm not sure what you'd like to do. Could you please rephrase your request?"
            );
            
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("Gemini LLM: processing failed after {}ms", durationMs, e);
            throw new RuntimeException("LLM processing failed", e);
        }
    }
    
    private String buildSystemInstruction(Map<String, Object> context) {
        return """
            You are a helpful banking assistant. You can help users with:
            - Checking account balances
            - Viewing account lists
            - Querying recent transactions
            
            You are read-only and cannot perform transfers, payments, or any write operations.
            
            If a user asks for something you cannot help with, politely explain your limitations.
            If a user's request is ambiguous, ask for clarification.
            
            Use function calling to execute user requests.
            """;
    }
    
    private List<Tool> createTools() {
        // All function declarations must be grouped under a single Tool object
        // to avoid "Multiple tools are supported only when they are all search tools" error
        Tool tool = Tool.newBuilder()
            // getBalance function
            .addFunctionDeclarations(FunctionDeclaration.newBuilder()
                .setName("getBalance")
                .setDescription("Get the current balance for an account")
                .setParameters(Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties("accountId", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("Account ID (default: acc-checking-001)")
                        .build())
                    .build())
                .build())
            // listAccounts function
            .addFunctionDeclarations(FunctionDeclaration.newBuilder()
                .setName("listAccounts")
                .setDescription("List all user accounts")
                .build())
            // queryTransactions function
            .addFunctionDeclarations(FunctionDeclaration.newBuilder()
                .setName("queryTransactions")
                .setDescription("Query recent transactions for an account")
                .setParameters(Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putProperties("accountId", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("Account ID (default: acc-checking-001)")
                        .build())
                    .putProperties("limit", Schema.newBuilder()
                        .setType(Type.INTEGER)
                        .setDescription("Maximum number of transactions to return (default: 10)")
                        .build())
                    .putProperties("text", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("Optional filter text (e.g., merchant name)")
                        .build())
                    .build())
                .build())
            // findNearbyBranches function
            .addFunctionDeclarations(FunctionDeclaration.newBuilder()
                .setName("findNearbyBranches")
                .setDescription("Find Acme Bank branches and ATMs near a location")
                .setParameters(Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .addRequired("latitude")
                    .addRequired("longitude")
                    .putProperties("latitude", Schema.newBuilder()
                        .setType(Type.NUMBER)
                        .setDescription("User's latitude coordinate (e.g., 50.1109 for Frankfurt)")
                        .build())
                    .putProperties("longitude", Schema.newBuilder()
                        .setType(Type.NUMBER)
                        .setDescription("User's longitude coordinate (e.g., 8.6821 for Frankfurt)")
                        .build())
                    .putProperties("radiusKm", Schema.newBuilder()
                        .setType(Type.NUMBER)
                        .setDescription("Search radius in kilometers (default: 5)")
                        .build())
                    .putProperties("limit", Schema.newBuilder()
                        .setType(Type.INTEGER)
                        .setDescription("Maximum number of results (default: 5)")
                        .build())
                    .putProperties("type", Schema.newBuilder()
                        .setType(Type.STRING)
                        .setDescription("Filter by type: 'branch', 'atm', 'flagship', or 'all' (default: all)")
                        .build())
                    .build())
                .build())
            .build();
        
        return List.of(tool);
    }
    
    private Map<String, Object> extractFunctionArgs(FunctionCall functionCall) {
        // Convert Protobuf Struct to Map
        var argsStruct = functionCall.getArgs();
        return argsStruct.getFieldsMap().entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> convertValue(e.getValue())
            ));
    }
    
    private Object convertValue(com.google.protobuf.Value value) {
        switch (value.getKindCase()) {
            case STRING_VALUE:
                return value.getStringValue();
            case NUMBER_VALUE:
                return value.getNumberValue();
            case BOOL_VALUE:
                return value.getBoolValue();
            case NULL_VALUE:
                return null;
            default:
                return value.toString();
        }
    }
    
    private String getToolDescription(String toolName) {
        return switch (toolName) {
            case "getBalance" -> "check your balance";
            case "listAccounts" -> "list your accounts";
            case "queryTransactions" -> "get your recent transactions";
            case "findNearbyBranches" -> "find branches and ATMs near you";
            default -> "help you with that";
        };
    }
    
    public void shutdown() {
        if (vertexAI != null) {
            vertexAI.close();
            log.info("LlmGeminiAdapter shut down");
        }
    }
}
