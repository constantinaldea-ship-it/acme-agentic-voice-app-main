package com.voicebanking.agent.product.integration;

import com.voicebanking.agent.product.domain.*;

import java.util.List;
import java.util.Optional;

/**
 * Interface for product catalog integration.
 * 
 * Production implementations would connect to real product catalog systems.
 * For MVP, MockProductCatalogClient provides static product data.
 * 
 * API Reference: I-08 Product Catalog API
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public interface ProductCatalogClient {
    
    /**
     * Get all products in a category.
     * @param category the product category
     * @return list of products in the category
     */
    List<Product> getProductsByCategory(ProductCategory category);
    
    /**
     * Get all credit card products.
     * @return list of credit card products
     */
    List<CreditCardProduct> getCreditCardProducts();
    
    /**
     * Get all Giro Konto products.
     * @return list of Giro Konto products
     */
    List<GiroKontoProduct> getGiroKontoProducts();
    
    /**
     * Get all debit card products.
     * @return list of debit card products
     */
    List<DebitCardProduct> getDebitCardProducts();
    
    /**
     * Get a product by ID.
     * @param productId the product identifier
     * @return the product if found
     */
    Optional<Product> getProductById(String productId);
    
    /**
     * Get fee schedule for a product.
     * @param productId the product identifier
     * @return the fee schedule if found
     */
    Optional<FeeSchedule> getFeeSchedule(String productId);
    
    /**
     * Get eligibility criteria for a product.
     * @param productId the product identifier
     * @return the eligibility criteria if found
     */
    Optional<EligibilityCriteria> getEligibility(String productId);
    
    /**
     * Check if products are in the same comparison group.
     * @param productId1 first product ID
     * @param productId2 second product ID
     * @return true if products can be compared
     */
    boolean areProductsComparable(String productId1, String productId2);
}
