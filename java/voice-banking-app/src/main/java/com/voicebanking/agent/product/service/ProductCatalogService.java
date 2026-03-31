package com.voicebanking.agent.product.service;

import com.voicebanking.agent.product.domain.*;
import com.voicebanking.agent.product.integration.ProductCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for retrieving product information from the catalog.
 * 
 * Provides product listing, detail retrieval, and filtering.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class ProductCatalogService {
    private static final Logger log = LoggerFactory.getLogger(ProductCatalogService.class);
    
    private final ProductCatalogClient catalogClient;
    
    public ProductCatalogService(ProductCatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }
    
    /**
     * Get all credit card products.
     * @return list of credit card products
     */
    public List<CreditCardProduct> getCreditCardProducts() {
        log.debug("Retrieving credit card products");
        return catalogClient.getCreditCardProducts();
    }
    
    /**
     * Get all Giro Konto products.
     * @return list of Giro Konto products
     */
    public List<GiroKontoProduct> getGiroKontoProducts() {
        log.debug("Retrieving Giro Konto products");
        return catalogClient.getGiroKontoProducts();
    }
    
    /**
     * Get all debit card products.
     * @return list of debit card products
     */
    public List<DebitCardProduct> getDebitCardProducts() {
        log.debug("Retrieving debit card products");
        return catalogClient.getDebitCardProducts();
    }
    
    /**
     * Get products by category.
     * @param category the product category
     * @return list of products
     */
    public List<Product> getProductsByCategory(ProductCategory category) {
        log.debug("Retrieving products for category: {}", category);
        return catalogClient.getProductsByCategory(category);
    }
    
    /**
     * Get a product by ID.
     * @param productId the product identifier
     * @return the product if found
     */
    public Optional<Product> getProductById(String productId) {
        log.debug("Retrieving product: {}", productId);
        return catalogClient.getProductById(productId);
    }
    
    /**
     * Get a credit card product by ID.
     * @param productId the product identifier
     * @return the credit card product if found
     */
    public Optional<CreditCardProduct> getCreditCardById(String productId) {
        return getProductById(productId)
            .filter(p -> p instanceof CreditCardProduct)
            .map(p -> (CreditCardProduct) p);
    }
    
    /**
     * Get a Giro Konto product by ID.
     * @param productId the product identifier
     * @return the Giro Konto product if found
     */
    public Optional<GiroKontoProduct> getGiroKontoById(String productId) {
        return getProductById(productId)
            .filter(p -> p instanceof GiroKontoProduct)
            .map(p -> (GiroKontoProduct) p);
    }
    
    /**
     * Get a debit card product by ID.
     * @param productId the product identifier
     * @return the debit card product if found
     */
    public Optional<DebitCardProduct> getDebitCardById(String productId) {
        return getProductById(productId)
            .filter(p -> p instanceof DebitCardProduct)
            .map(p -> (DebitCardProduct) p);
    }
    
    /**
     * Format product list for voice output.
     * @param products list of products
     * @param lang language code
     * @return voice-friendly product list
     */
    public String formatProductListForVoice(List<? extends Product> products, String lang) {
        if (products.isEmpty()) {
            return "No products found in this category.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("We offer ").append(products.size()).append(" products: ");
        
        for (int i = 0; i < products.size(); i++) {
            if (i > 0 && i == products.size() - 1) {
                sb.append(", and ");
            } else if (i > 0) {
                sb.append(", ");
            }
            sb.append(products.get(i).formatAsBriefListing(lang));
        }
        sb.append(". Would you like more details on any of these?");
        
        return sb.toString();
    }
}
