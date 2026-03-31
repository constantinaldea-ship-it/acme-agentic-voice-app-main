package com.voicebanking.agent.product.service;

import com.voicebanking.agent.product.domain.*;
import com.voicebanking.agent.product.integration.ProductCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service for fee retrieval and calculation.
 * 
 * Provides fee schedules, annual cost calculations, and voice formatting.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class FeeCalculationService {
    private static final Logger log = LoggerFactory.getLogger(FeeCalculationService.class);
    
    private final ProductCatalogClient catalogClient;
    
    public FeeCalculationService(ProductCatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }
    
    /**
     * Get fee schedule for a product.
     * @param productId the product identifier
     * @return the fee schedule if found
     */
    public Optional<FeeSchedule> getFeeSchedule(String productId) {
        log.debug("Retrieving fee schedule for product: {}", productId);
        return catalogClient.getFeeSchedule(productId);
    }
    
    /**
     * Get fees by type for a product.
     * @param productId the product identifier
     * @param feeType the type of fees to retrieve
     * @return list of fees of the specified type
     */
    public List<Fee> getFeesByType(String productId, FeeType feeType) {
        return getFeeSchedule(productId)
            .map(schedule -> schedule.getFeesByType(feeType))
            .orElse(List.of());
    }
    
    /**
     * Get the primary fee (main periodic fee) for a product.
     * @param productId the product identifier
     * @return the primary fee if found
     */
    public Optional<Fee> getPrimaryFee(String productId) {
        return getFeeSchedule(productId)
            .map(FeeSchedule::getPrimaryFee);
    }
    
    /**
     * Calculate estimated annual cost for a product.
     * @param productId the product identifier
     * @return the annual cost or null if cannot be calculated
     */
    public BigDecimal calculateAnnualCost(String productId) {
        return getFeeSchedule(productId)
            .map(FeeSchedule::calculateAnnualCost)
            .orElse(BigDecimal.ZERO);
    }
    
    /**
     * Format fee schedule for voice output.
     * @param productId the product identifier
     * @param lang language code
     * @return voice-friendly fee description
     */
    public String formatFeesForVoice(String productId, String lang) {
        Optional<FeeSchedule> schedule = getFeeSchedule(productId);
        
        if (schedule.isEmpty()) {
            return "I couldn't find fee information for this product.";
        }
        
        return schedule.get().formatForVoice(lang);
    }
    
    /**
     * Format a single fee for voice output.
     * @param fee the fee to format
     * @param lang language code
     * @return voice-friendly fee description
     */
    public String formatFeeForVoice(Fee fee, String lang) {
        return fee.formatForVoice(lang);
    }
    
    /**
     * Get all fees for a product formatted for voice.
     * @param productId the product identifier
     * @param lang language code
     * @return detailed fee breakdown for voice
     */
    public String getDetailedFeesForVoice(String productId, String lang) {
        Optional<FeeSchedule> scheduleOpt = getFeeSchedule(productId);
        
        if (scheduleOpt.isEmpty()) {
            return "I couldn't find fee information for this product.";
        }
        
        FeeSchedule schedule = scheduleOpt.get();
        List<Fee> fees = schedule.getFees();
        
        if (fees.isEmpty()) {
            return "There are no fees for " + schedule.getProductName() + ".";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("Here are all fees for ").append(schedule.getProductName()).append(": ");
        
        for (int i = 0; i < fees.size(); i++) {
            Fee fee = fees.get(i);
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(fee.formatForVoice(lang)).append(".");
        }
        
        BigDecimal annualCost = schedule.calculateAnnualCost();
        if (annualCost.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(" The estimated annual cost from periodic fees is €").append(annualCost).append(".");
        }
        
        return sb.toString();
    }
}
