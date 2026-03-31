package com.voicebanking.agent.product;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.product.domain.*;
import com.voicebanking.agent.product.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ProductInformationAgent — Credit Card, Giro Konto & Debit Card Product Data
 * 
 * Provides detailed product information for Acme Bank products including:
 * - Credit Cards (Standard, Gold, Platinum, Business)
 * - Giro Konto (AktivKonto, BestKonto, Junges Konto, Basiskonto)
 * - Debit Cards (Debit Mastercard, girocard)
 * 
 * Per Scope Document Slide 10: Second highest volume intent after general information.
 * 
 * Architecture: Component E (AI Functional Agents)
 * Category: Category 2 — Voice-Enabled Context-Aware Banking (Read)
 * Related FRs: FR-002 Credit Card Product Info, FR-003 Giro Konto/Debit Info
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
public class ProductInformationAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(ProductInformationAgent.class);
    
    private static final String AGENT_ID = "product-information";
    private static final List<String> TOOL_IDS = List.of(
            "getCreditCardProducts",
            "getCreditCardFees",
            "getGiroKontoProducts",
            "getGiroKontoFees",
            "getDebitCardInfo",
            "compareProducts"
    );
    
    private final ProductCatalogService catalogService;
    private final FeeCalculationService feeService;
    private final ProductComparisonService comparisonService;
    
    public ProductInformationAgent(
            ProductCatalogService catalogService,
            FeeCalculationService feeService,
            ProductComparisonService comparisonService) {
        this.catalogService = catalogService;
        this.feeService = feeService;
        this.comparisonService = comparisonService;
        log.info("ProductInformationAgent initialized with {} tools", TOOL_IDS.size());
    }
    
    @Override
    public String getAgentId() {
        return AGENT_ID;
    }
    
    @Override
    public String getDescription() {
        return "Provides product information for Credit Cards, Giro Konto, and Debit Cards " +
               "including features, fees, eligibility, and factual comparisons";
    }
    
    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }
    
    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("Executing tool: {} with input: {}", toolId, input);
        
        try {
            return switch (toolId) {
                case "getCreditCardProducts" -> getCreditCardProducts(input);
                case "getCreditCardFees" -> getCreditCardFees(input);
                case "getGiroKontoProducts" -> getGiroKontoProducts(input);
                case "getGiroKontoFees" -> getGiroKontoFees(input);
                case "getDebitCardInfo" -> getDebitCardInfo(input);
                case "compareProducts" -> compareProducts(input);
                default -> Map.of("error", "Unknown tool: " + toolId, "success", false);
            };
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolId, e.getMessage(), e);
            return Map.of("error", e.getMessage(), "success", false);
        }
    }
    
    /**
     * Get list of credit card products.
     * 
     * Input: { "lang": "en" (optional), "detailed": false (optional) }
     * Output: { "success": true, "products": [...], "voiceResponse": "..." }
     */
    private Map<String, Object> getCreditCardProducts(Map<String, Object> input) {
        String lang = (String) input.getOrDefault("lang", "en");
        boolean detailed = Boolean.TRUE.equals(input.get("detailed"));
        
        List<CreditCardProduct> products = catalogService.getCreditCardProducts();
        
        String voiceResponse = catalogService.formatProductListForVoice(products, lang);
        
        List<Map<String, Object>> productMaps = products.stream()
            .map(p -> detailed ? p.toMap() : p.toBriefMap())
            .collect(Collectors.toList());
        
        log.info("Retrieved {} credit card products", products.size());
        
        return Map.of(
            "success", true,
            "products", productMaps,
            "count", products.size(),
            "voiceResponse", voiceResponse
        );
    }
    
    /**
     * Get fee schedule for a credit card product.
     * 
     * Input: { "productId": "cc-gold" (optional, lists all if omitted), "lang": "en" (optional) }
     * Output: { "success": true, "fees": {...}, "voiceResponse": "..." }
     */
    private Map<String, Object> getCreditCardFees(Map<String, Object> input) {
        String productId = (String) input.get("productId");
        String lang = (String) input.getOrDefault("lang", "en");
        
        // If no specific product, return fees for all credit cards
        if (productId == null || productId.isBlank()) {
            List<CreditCardProduct> products = catalogService.getCreditCardProducts();
            List<Map<String, Object>> allFees = new ArrayList<>();
            StringBuilder voiceBuilder = new StringBuilder("Here are the credit card fees: ");
            
            for (CreditCardProduct product : products) {
                Optional<FeeSchedule> schedule = feeService.getFeeSchedule(product.getId());
                if (schedule.isPresent()) {
                    Map<String, Object> feeInfo = new HashMap<>();
                    feeInfo.put("productId", product.getId());
                    feeInfo.put("productName", product.getName(lang));
                    feeInfo.put("fees", schedule.get().toMap());
                    allFees.add(feeInfo);
                    
                    Fee primary = schedule.get().getPrimaryFee();
                    if (primary != null) {
                        voiceBuilder.append(product.getName(lang))
                            .append(" has an annual fee of €").append(primary.getAmount())
                            .append(". ");
                    }
                }
            }
            voiceBuilder.append("Would you like details on a specific card?");
            
            return Map.of(
                "success", true,
                "fees", allFees,
                "count", allFees.size(),
                "voiceResponse", voiceBuilder.toString()
            );
        }
        
        // Get fees for specific product
        Optional<CreditCardProduct> productOpt = catalogService.getCreditCardById(productId);
        if (productOpt.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "Credit card product not found: " + productId,
                "voiceResponse", "I couldn't find a credit card with that name. " +
                                "Would you like me to list all available credit cards?"
            );
        }
        
        Optional<FeeSchedule> scheduleOpt = feeService.getFeeSchedule(productId);
        if (scheduleOpt.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "Fee schedule not found for product: " + productId,
                "voiceResponse", "I couldn't find fee information for this credit card."
            );
        }
        
        String voiceResponse = feeService.getDetailedFeesForVoice(productId, lang);
        
        log.info("Retrieved fees for credit card: {}", productId);
        
        return Map.of(
            "success", true,
            "productId", productId,
            "productName", productOpt.get().getName(lang),
            "fees", scheduleOpt.get().toMap(),
            "voiceResponse", voiceResponse
        );
    }
    
    /**
     * Get list of Giro Konto products.
     * 
     * Input: { "lang": "en" (optional), "detailed": false (optional) }
     * Output: { "success": true, "products": [...], "voiceResponse": "..." }
     */
    private Map<String, Object> getGiroKontoProducts(Map<String, Object> input) {
        String lang = (String) input.getOrDefault("lang", "en");
        boolean detailed = Boolean.TRUE.equals(input.get("detailed"));
        
        List<GiroKontoProduct> products = catalogService.getGiroKontoProducts();
        
        String voiceResponse = catalogService.formatProductListForVoice(products, lang);
        
        List<Map<String, Object>> productMaps = products.stream()
            .map(p -> detailed ? p.toMap() : p.toBriefMap())
            .collect(Collectors.toList());
        
        log.info("Retrieved {} Giro Konto products", products.size());
        
        return Map.of(
            "success", true,
            "products", productMaps,
            "count", products.size(),
            "voiceResponse", voiceResponse
        );
    }
    
    /**
     * Get fee schedule for a Giro Konto product.
     * 
     * Input: { "productId": "gk-aktiv" (optional, lists all if omitted), "lang": "en" (optional) }
     * Output: { "success": true, "fees": {...}, "voiceResponse": "..." }
     */
    private Map<String, Object> getGiroKontoFees(Map<String, Object> input) {
        String productId = (String) input.get("productId");
        String lang = (String) input.getOrDefault("lang", "en");
        
        // If no specific product, return fees for all Giro Kontos
        if (productId == null || productId.isBlank()) {
            List<GiroKontoProduct> products = catalogService.getGiroKontoProducts();
            List<Map<String, Object>> allFees = new ArrayList<>();
            StringBuilder voiceBuilder = new StringBuilder("Here are the Giro Konto fees: ");
            
            for (GiroKontoProduct product : products) {
                Optional<FeeSchedule> schedule = feeService.getFeeSchedule(product.getId());
                if (schedule.isPresent()) {
                    Map<String, Object> feeInfo = new HashMap<>();
                    feeInfo.put("productId", product.getId());
                    feeInfo.put("productName", product.getName(lang));
                    feeInfo.put("fees", schedule.get().toMap());
                    allFees.add(feeInfo);
                    
                    Fee primary = schedule.get().getPrimaryFee();
                    if (primary != null) {
                        if (primary.getAmount().doubleValue() == 0) {
                            voiceBuilder.append(product.getName(lang))
                                .append(" has no monthly fee. ");
                        } else {
                            voiceBuilder.append(product.getName(lang))
                                .append(" has a monthly fee of €").append(primary.getAmount())
                                .append(". ");
                        }
                    }
                }
            }
            voiceBuilder.append("Would you like details on a specific account?");
            
            return Map.of(
                "success", true,
                "fees", allFees,
                "count", allFees.size(),
                "voiceResponse", voiceBuilder.toString()
            );
        }
        
        // Get fees for specific product
        Optional<GiroKontoProduct> productOpt = catalogService.getGiroKontoById(productId);
        if (productOpt.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "Giro Konto product not found: " + productId,
                "voiceResponse", "I couldn't find a Giro Konto with that name. " +
                                "Would you like me to list all available accounts?"
            );
        }
        
        Optional<FeeSchedule> scheduleOpt = feeService.getFeeSchedule(productId);
        if (scheduleOpt.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "Fee schedule not found for product: " + productId,
                "voiceResponse", "I couldn't find fee information for this account."
            );
        }
        
        String voiceResponse = feeService.getDetailedFeesForVoice(productId, lang);
        
        log.info("Retrieved fees for Giro Konto: {}", productId);
        
        return Map.of(
            "success", true,
            "productId", productId,
            "productName", productOpt.get().getName(lang),
            "fees", scheduleOpt.get().toMap(),
            "voiceResponse", voiceResponse
        );
    }
    
    /**
     * Get debit card information.
     * 
     * Input: { "cardType": "mastercard" or "girocard" (optional), "lang": "en" (optional) }
     * Output: { "success": true, "cards": [...], "voiceResponse": "..." }
     */
    private Map<String, Object> getDebitCardInfo(Map<String, Object> input) {
        String cardType = (String) input.get("cardType");
        String lang = (String) input.getOrDefault("lang", "en");
        
        List<DebitCardProduct> allCards = catalogService.getDebitCardProducts();
        List<DebitCardProduct> filteredCards;
        
        if (cardType != null && !cardType.isBlank()) {
            // Filter by card type
            String typeNormalized = cardType.toLowerCase();
            filteredCards = allCards.stream()
                .filter(c -> c.getCardNetwork() != null && 
                            c.getCardNetwork().toLowerCase().contains(typeNormalized))
                .toList();
            
            if (filteredCards.isEmpty()) {
                return Map.of(
                    "success", false,
                    "error", "No debit card found matching: " + cardType,
                    "voiceResponse", "I couldn't find a debit card of that type. " +
                                    "We offer Debit Mastercard and girocard options."
                );
            }
        } else {
            filteredCards = allCards;
        }
        
        StringBuilder voiceBuilder = new StringBuilder();
        if (filteredCards.size() == 1) {
            voiceBuilder.append(filteredCards.get(0).formatForVoice(lang));
        } else {
            voiceBuilder.append("We offer ").append(filteredCards.size()).append(" debit cards: ");
            for (int i = 0; i < filteredCards.size(); i++) {
                if (i > 0) voiceBuilder.append(" ");
                voiceBuilder.append(filteredCards.get(i).formatForVoice(lang));
            }
        }
        
        List<Map<String, Object>> cardMaps = filteredCards.stream()
            .map(DebitCardProduct::toMap)
            .collect(Collectors.toList());
        
        log.info("Retrieved {} debit card(s)", filteredCards.size());
        
        return Map.of(
            "success", true,
            "cards", cardMaps,
            "count", filteredCards.size(),
            "voiceResponse", voiceBuilder.toString()
        );
    }
    
    /**
     * Compare two products (factual comparison, no recommendations).
     * 
     * Input: { "product1": "cc-standard", "product2": "cc-gold", "lang": "en" (optional) }
     * Output: { "success": true, "comparison": {...}, "voiceResponse": "...", "disclaimer": "..." }
     */
    private Map<String, Object> compareProducts(Map<String, Object> input) {
        String productId1 = (String) input.get("product1");
        String productId2 = (String) input.get("product2");
        String lang = (String) input.getOrDefault("lang", "en");
        
        if (productId1 == null || productId1.isBlank() || productId2 == null || productId2.isBlank()) {
            return Map.of(
                "success", false,
                "error", "Both product1 and product2 are required",
                "voiceResponse", "Please specify two products to compare."
            );
        }
        
        // Check if products can be compared
        if (!comparisonService.canCompare(productId1, productId2)) {
            String errorMessage = comparisonService.getNotComparableMessage(productId1, productId2, lang);
            return Map.of(
                "success", false,
                "error", "Products are not comparable (different categories)",
                "voiceResponse", errorMessage
            );
        }
        
        Optional<ProductComparison> comparisonOpt = comparisonService.compareProducts(productId1, productId2);
        
        if (comparisonOpt.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "Could not compare products",
                "voiceResponse", "I couldn't find one or both of those products."
            );
        }
        
        ProductComparison comparison = comparisonOpt.get();
        String voiceResponse = comparisonService.formatComparisonForVoice(comparison, lang);
        
        log.info("Compared products: {} vs {}", productId1, productId2);
        
        return Map.of(
            "success", true,
            "comparison", comparison.toMap(),
            "voiceResponse", voiceResponse,
            "disclaimer", ProductComparison.getDisclaimer(lang)
        );
    }
}
