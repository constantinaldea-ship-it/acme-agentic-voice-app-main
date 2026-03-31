package com.voicebanking.controller;

import com.voicebanking.agent.product.domain.*;
import com.voicebanking.agent.product.service.FeeCalculationService;
import com.voicebanking.agent.product.service.ProductCatalogService;
import com.voicebanking.agent.product.service.ProductComparisonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Product Catalog Controller
 * 
 * <p>REST API endpoints for product catalog operations.</p>
 * <p>Provides access to Credit Cards, Giro Kontos, and Debit Cards.</p>
 * 
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>GET /api/products - List all products</li>
 *   <li>GET /api/products/{id} - Get product by ID</li>
 *   <li>GET /api/products/{id}/fees - Get fees for a product</li>
 *   <li>GET /api/products/credit-cards - List credit cards</li>
 *   <li>GET /api/products/giro-kontos - List Giro Konto accounts</li>
 *   <li>GET /api/products/debit-cards - List debit cards</li>
 *   <li>GET /api/products/compare - Compare two products</li>
 * </ul>
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "*")
public class ProductCatalogController {
    
    private static final Logger log = LoggerFactory.getLogger(ProductCatalogController.class);
    
    private final ProductCatalogService catalogService;
    private final FeeCalculationService feeService;
    private final ProductComparisonService comparisonService;
    
    public ProductCatalogController(
            ProductCatalogService catalogService,
            FeeCalculationService feeService,
            ProductComparisonService comparisonService) {
        this.catalogService = catalogService;
        this.feeService = feeService;
        this.comparisonService = comparisonService;
    }
    
    /**
     * Get all products.
     * 
     * GET /api/products
     * 
     * @param category optional filter by category (CREDIT_CARD, GIRO_KONTO, DEBIT_CARD)
     * @param lang language for response (en, de)
     * @return list of all products
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        
        log.info("GET /api/products?category={}&lang={}", category, lang);
        
        Map<String, Object> response = new HashMap<>();
        
        if (category != null) {
            try {
                ProductCategory cat = ProductCategory.valueOf(category.toUpperCase());
                List<Product> products = catalogService.getProductsByCategory(cat);
                response.put("products", products.stream()
                    .map(Product::toMap)
                    .collect(Collectors.toList()));
                response.put("count", products.size());
                response.put("category", category.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid category: " + category,
                    "validCategories", List.of("CREDIT_CARD", "GIRO_KONTO", "DEBIT_CARD")
                ));
            }
        } else {
            // Return all products grouped by category
            List<CreditCardProduct> creditCards = catalogService.getCreditCardProducts();
            List<GiroKontoProduct> giroKontos = catalogService.getGiroKontoProducts();
            List<DebitCardProduct> debitCards = catalogService.getDebitCardProducts();
            
            response.put("creditCards", creditCards.stream()
                .map(CreditCardProduct::toMap)
                .collect(Collectors.toList()));
            response.put("giroKontos", giroKontos.stream()
                .map(GiroKontoProduct::toMap)
                .collect(Collectors.toList()));
            response.put("debitCards", debitCards.stream()
                .map(DebitCardProduct::toMap)
                .collect(Collectors.toList()));
            response.put("totalCount", creditCards.size() + giroKontos.size() + debitCards.size());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get credit cards.
     * 
     * GET /api/products/credit-cards
     * 
     * @param lang language for response (en, de)
     * @return list of credit card products
     */
    @GetMapping("/credit-cards")
    public ResponseEntity<Map<String, Object>> getCreditCards(
            @RequestParam(required = false, defaultValue = "en") String lang) {
        
        log.info("GET /api/products/credit-cards?lang={}", lang);
        
        List<CreditCardProduct> products = catalogService.getCreditCardProducts();
        
        return ResponseEntity.ok(Map.of(
            "products", products.stream()
                .map(CreditCardProduct::toMap)
                .collect(Collectors.toList()),
            "count", products.size(),
            "category", "CREDIT_CARD"
        ));
    }
    
    /**
     * Get Giro Konto products.
     * 
     * GET /api/products/giro-kontos
     * 
     * @param lang language for response (en, de)
     * @return list of Giro Konto products
     */
    @GetMapping("/giro-kontos")
    public ResponseEntity<Map<String, Object>> getGiroKontos(
            @RequestParam(required = false, defaultValue = "en") String lang) {
        
        log.info("GET /api/products/giro-kontos?lang={}", lang);
        
        List<GiroKontoProduct> products = catalogService.getGiroKontoProducts();
        
        return ResponseEntity.ok(Map.of(
            "products", products.stream()
                .map(GiroKontoProduct::toMap)
                .collect(Collectors.toList()),
            "count", products.size(),
            "category", "GIRO_KONTO"
        ));
    }
    
    /**
     * Get debit cards.
     * 
     * GET /api/products/debit-cards
     * 
     * @param lang language for response (en, de)
     * @return list of debit card products
     */
    @GetMapping("/debit-cards")
    public ResponseEntity<Map<String, Object>> getDebitCards(
            @RequestParam(required = false, defaultValue = "en") String lang) {
        
        log.info("GET /api/products/debit-cards?lang={}", lang);
        
        List<DebitCardProduct> products = catalogService.getDebitCardProducts();
        
        return ResponseEntity.ok(Map.of(
            "products", products.stream()
                .map(DebitCardProduct::toMap)
                .collect(Collectors.toList()),
            "count", products.size(),
            "category", "DEBIT_CARD"
        ));
    }
    
    /**
     * Get product by ID.
     * 
     * GET /api/products/{id}
     * 
     * @param id product identifier
     * @param lang language for response (en, de)
     * @return product details
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProductById(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        
        log.info("GET /api/products/{}?lang={}", id, lang);
        
        Optional<Product> productOpt = catalogService.getProductById(id);
        
        if (productOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Product product = productOpt.get();
        Map<String, Object> response = new HashMap<>(product.toMap());
        
        // Add fee information if available
        feeService.getFeeSchedule(id).ifPresent(schedule -> {
            response.put("feeSchedule", schedule.toMap());
        });
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get fees for a product.
     * 
     * GET /api/products/{id}/fees
     * 
     * @param id product identifier
     * @param lang language for response (en, de)
     * @return fee schedule
     */
    @GetMapping("/{id}/fees")
    public ResponseEntity<Map<String, Object>> getProductFees(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        
        log.info("GET /api/products/{}/fees?lang={}", id, lang);
        
        Optional<Product> productOpt = catalogService.getProductById(id);
        if (productOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Optional<FeeSchedule> scheduleOpt = feeService.getFeeSchedule(id);
        if (scheduleOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "productId", id,
                "productName", productOpt.get().getName(lang),
                "fees", List.of(),
                "message", "No fee schedule available for this product"
            ));
        }
        
        FeeSchedule schedule = scheduleOpt.get();
        
        Map<String, Object> response = new HashMap<>();
        response.put("productId", id);
        response.put("productName", productOpt.get().getName(lang));
        response.put("fees", schedule.toMap());
        response.put("voiceDescription", feeService.getDetailedFeesForVoice(id, lang));
        
        // Add annual cost estimate
        java.math.BigDecimal annualCost = feeService.calculateAnnualCost(id);
        if (annualCost != null && annualCost.compareTo(java.math.BigDecimal.ZERO) > 0) {
            response.put("estimatedAnnualCost", annualCost);
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Compare two products.
     * 
     * GET /api/products/compare?product1=cc-standard&product2=cc-gold
     * 
     * @param product1 first product ID
     * @param product2 second product ID
     * @param lang language for response (en, de)
     * @return comparison result
     */
    @GetMapping("/compare")
    public ResponseEntity<Map<String, Object>> compareProducts(
            @RequestParam String product1,
            @RequestParam String product2,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        
        log.info("GET /api/products/compare?product1={}&product2={}&lang={}", product1, product2, lang);
        
        // Check if products can be compared
        if (!comparisonService.canCompare(product1, product2)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Products cannot be compared (different categories)",
                "message", comparisonService.getNotComparableMessage(product1, product2, lang),
                "product1", product1,
                "product2", product2
            ));
        }
        
        Optional<ProductComparison> comparisonOpt = comparisonService.compareProducts(product1, product2);
        
        if (comparisonOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        ProductComparison comparison = comparisonOpt.get();
        
        Map<String, Object> response = new HashMap<>(comparison.toMap());
        response.put("voiceDescription", comparisonService.formatComparisonForVoice(comparison, lang));
        response.put("disclaimer", ProductComparison.getDisclaimer(lang));
        
        return ResponseEntity.ok(response);
    }
}
