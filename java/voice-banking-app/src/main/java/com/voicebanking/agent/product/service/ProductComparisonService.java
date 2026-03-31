package com.voicebanking.agent.product.service;

import com.voicebanking.agent.product.domain.*;
import com.voicebanking.agent.product.integration.ProductCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for comparing products.
 * 
 * Provides factual comparison without recommendations.
 * Always includes disclaimer about consulting an advisor.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class ProductComparisonService {
    private static final Logger log = LoggerFactory.getLogger(ProductComparisonService.class);
    
    private final ProductCatalogClient catalogClient;
    
    public ProductComparisonService(ProductCatalogClient catalogClient) {
        this.catalogClient = catalogClient;
    }
    
    /**
     * Check if two products can be compared.
     * @param productId1 first product ID
     * @param productId2 second product ID
     * @return true if products are in the same comparison group
     */
    public boolean canCompare(String productId1, String productId2) {
        return catalogClient.areProductsComparable(productId1, productId2);
    }
    
    /**
     * Compare two products.
     * @param productId1 first product ID
     * @param productId2 second product ID
     * @return comparison result, or empty if products cannot be compared
     */
    public Optional<ProductComparison> compareProducts(String productId1, String productId2) {
        log.debug("Comparing products: {} and {}", productId1, productId2);
        
        Optional<Product> p1Opt = catalogClient.getProductById(productId1);
        Optional<Product> p2Opt = catalogClient.getProductById(productId2);
        
        if (p1Opt.isEmpty() || p2Opt.isEmpty()) {
            log.warn("One or both products not found: {} or {}", productId1, productId2);
            return Optional.empty();
        }
        
        Product p1 = p1Opt.get();
        Product p2 = p2Opt.get();
        
        if (!canCompare(productId1, productId2)) {
            log.warn("Products are not comparable: {} and {}", productId1, productId2);
            return Optional.empty();
        }
        
        List<ProductComparison.ComparisonPoint> points = new ArrayList<>();
        List<String> p1AdvantagesEn = new ArrayList<>();
        List<String> p1AdvantagesDe = new ArrayList<>();
        List<String> p2AdvantagesEn = new ArrayList<>();
        List<String> p2AdvantagesDe = new ArrayList<>();
        
        // Compare primary fee
        compareFees(p1, p2, points, p1AdvantagesEn, p1AdvantagesDe, p2AdvantagesEn, p2AdvantagesDe);
        
        // Compare features
        compareFeatures(p1, p2, points, p1AdvantagesEn, p1AdvantagesDe, p2AdvantagesEn, p2AdvantagesDe);
        
        // Compare category-specific attributes
        if (p1 instanceof CreditCardProduct cc1 && p2 instanceof CreditCardProduct cc2) {
            compareCreditCards(cc1, cc2, points, p1AdvantagesEn, p1AdvantagesDe, p2AdvantagesEn, p2AdvantagesDe);
        } else if (p1 instanceof GiroKontoProduct gk1 && p2 instanceof GiroKontoProduct gk2) {
            compareGiroKontos(gk1, gk2, points, p1AdvantagesEn, p1AdvantagesDe, p2AdvantagesEn, p2AdvantagesDe);
        }
        
        // Calculate annual cost difference
        BigDecimal annualCostDiff = calculateAnnualCostDifference(p1, p2);
        
        ProductComparison comparison = ProductComparison.builder()
            .product1(p1)
            .product2(p2)
            .comparisonPoints(points)
            .product1AdvantagesEn(p1AdvantagesEn)
            .product1AdvantagesDe(p1AdvantagesDe)
            .product2AdvantagesEn(p2AdvantagesEn)
            .product2AdvantagesDe(p2AdvantagesDe)
            .annualCostDifference(annualCostDiff)
            .build();
        
        return Optional.of(comparison);
    }
    
    private void compareFees(Product p1, Product p2, 
            List<ProductComparison.ComparisonPoint> points,
            List<String> p1AdvEn, List<String> p1AdvDe,
            List<String> p2AdvEn, List<String> p2AdvDe) {
        
        Fee fee1 = p1.getFeeSchedule() != null ? p1.getFeeSchedule().getPrimaryFee() : null;
        Fee fee2 = p2.getFeeSchedule() != null ? p2.getFeeSchedule().getPrimaryFee() : null;
        
        if (fee1 != null && fee2 != null) {
            String period = fee1.getFrequency() == FeeFrequency.MONTHLY ? "Monthly Fee" : "Annual Fee";
            String periodDe = fee1.getFrequency() == FeeFrequency.MONTHLY ? "Monatliche Gebühr" : "Jahresgebühr";
            
            points.add(ProductComparison.ComparisonPoint.pointBuilder()
                .attributeEn(period)
                .attributeDe(periodDe)
                .product1ValueEn("€" + fee1.getAmount())
                .product1ValueDe("€" + fee1.getAmount())
                .product2ValueEn("€" + fee2.getAmount())
                .product2ValueDe("€" + fee2.getAmount())
                .significantDifference(fee1.getAmount().compareTo(fee2.getAmount()) != 0)
                .build());
            
            if (fee1.getAmount().compareTo(fee2.getAmount()) < 0) {
                p1AdvEn.add("Lower " + period.toLowerCase());
                p1AdvDe.add("Niedrigere " + periodDe.toLowerCase());
            } else if (fee2.getAmount().compareTo(fee1.getAmount()) < 0) {
                p2AdvEn.add("Lower " + period.toLowerCase());
                p2AdvDe.add("Niedrigere " + periodDe.toLowerCase());
            }
        }
    }
    
    private void compareFeatures(Product p1, Product p2,
            List<ProductComparison.ComparisonPoint> points,
            List<String> p1AdvEn, List<String> p1AdvDe,
            List<String> p2AdvEn, List<String> p2AdvDe) {
        
        List<ProductFeature> p1Highlights = p1.getHighlightFeatures();
        List<ProductFeature> p2Highlights = p2.getHighlightFeatures();
        
        // Find features unique to p1
        for (ProductFeature f : p1Highlights) {
            boolean hasInP2 = p2Highlights.stream()
                .anyMatch(f2 -> f2.getId().equals(f.getId()));
            if (!hasInP2) {
                p1AdvEn.add(f.getNameEn() + (f.getValueEn() != null ? ": " + f.getValueEn() : ""));
                p1AdvDe.add(f.getNameDe() + (f.getValueDe() != null ? ": " + f.getValueDe() : ""));
            }
        }
        
        // Find features unique to p2
        for (ProductFeature f : p2Highlights) {
            boolean hasInP1 = p1Highlights.stream()
                .anyMatch(f1 -> f1.getId().equals(f.getId()));
            if (!hasInP1) {
                p2AdvEn.add(f.getNameEn() + (f.getValueEn() != null ? ": " + f.getValueEn() : ""));
                p2AdvDe.add(f.getNameDe() + (f.getValueDe() != null ? ": " + f.getValueDe() : ""));
            }
        }
    }
    
    private void compareCreditCards(CreditCardProduct cc1, CreditCardProduct cc2,
            List<ProductComparison.ComparisonPoint> points,
            List<String> p1AdvEn, List<String> p1AdvDe,
            List<String> p2AdvEn, List<String> p2AdvDe) {
        
        // Compare credit limits
        if (cc1.getCreditLimitMax() != null && cc2.getCreditLimitMax() != null) {
            points.add(ProductComparison.ComparisonPoint.pointBuilder()
                .attributeEn("Maximum Credit Limit")
                .attributeDe("Maximales Kreditlimit")
                .product1ValueEn("€" + cc1.getCreditLimitMax())
                .product1ValueDe("€" + cc1.getCreditLimitMax())
                .product2ValueEn("€" + cc2.getCreditLimitMax())
                .product2ValueDe("€" + cc2.getCreditLimitMax())
                .significantDifference(cc1.getCreditLimitMax().compareTo(cc2.getCreditLimitMax()) != 0)
                .build());
            
            if (cc1.getCreditLimitMax().compareTo(cc2.getCreditLimitMax()) > 0) {
                p1AdvEn.add("Higher credit limit");
                p1AdvDe.add("Höheres Kreditlimit");
            } else if (cc2.getCreditLimitMax().compareTo(cc1.getCreditLimitMax()) > 0) {
                p2AdvEn.add("Higher credit limit");
                p2AdvDe.add("Höheres Kreditlimit");
            }
        }
        
        // Compare insurance
        if (cc1.getInsurancePackage() != null && cc2.getInsurancePackage() == null) {
            p1AdvEn.add("Travel insurance included");
            p1AdvDe.add("Reiseversicherung inklusive");
        } else if (cc2.getInsurancePackage() != null && cc1.getInsurancePackage() == null) {
            p2AdvEn.add("Travel insurance included");
            p2AdvDe.add("Reiseversicherung inklusive");
        }
    }
    
    private void compareGiroKontos(GiroKontoProduct gk1, GiroKontoProduct gk2,
            List<ProductComparison.ComparisonPoint> points,
            List<String> p1AdvEn, List<String> p1AdvDe,
            List<String> p2AdvEn, List<String> p2AdvDe) {
        
        // Compare free ATM withdrawals
        Integer atm1 = gk1.getFreeAtmWithdrawals();
        Integer atm2 = gk2.getFreeAtmWithdrawals();
        
        String atm1Str = atm1 == null ? "Unlimited" : atm1 + " per month";
        String atm2Str = atm2 == null ? "Unlimited" : atm2 + " per month";
        String atm1StrDe = atm1 == null ? "Unbegrenzt" : atm1 + " pro Monat";
        String atm2StrDe = atm2 == null ? "Unbegrenzt" : atm2 + " pro Monat";
        
        points.add(ProductComparison.ComparisonPoint.pointBuilder()
            .attributeEn("Free ATM Withdrawals")
            .attributeDe("Kostenlose Geldautomaten-Abhebungen")
            .product1ValueEn(atm1Str)
            .product1ValueDe(atm1StrDe)
            .product2ValueEn(atm2Str)
            .product2ValueDe(atm2StrDe)
            .significantDifference(!atm1Str.equals(atm2Str))
            .build());
        
        if (atm1 == null && atm2 != null) {
            p1AdvEn.add("Unlimited ATM withdrawals");
            p1AdvDe.add("Unbegrenzte Geldautomaten-Abhebungen");
        } else if (atm2 == null && atm1 != null) {
            p2AdvEn.add("Unlimited ATM withdrawals");
            p2AdvDe.add("Unbegrenzte Geldautomaten-Abhebungen");
        }
    }
    
    private BigDecimal calculateAnnualCostDifference(Product p1, Product p2) {
        BigDecimal cost1 = p1.getFeeSchedule() != null ? 
            p1.getFeeSchedule().calculateAnnualCost() : BigDecimal.ZERO;
        BigDecimal cost2 = p2.getFeeSchedule() != null ? 
            p2.getFeeSchedule().calculateAnnualCost() : BigDecimal.ZERO;
        return cost1.subtract(cost2);
    }
    
    /**
     * Format comparison for voice output.
     * @param comparison the comparison result
     * @param lang language code
     * @return voice-friendly comparison
     */
    public String formatComparisonForVoice(ProductComparison comparison, String lang) {
        return comparison.formatForVoice(lang);
    }
    
    /**
     * Get error message for non-comparable products.
     * @param productId1 first product ID
     * @param productId2 second product ID
     * @param lang language code
     * @return error message
     */
    public String getNotComparableMessage(String productId1, String productId2, String lang) {
        if ("de".equalsIgnoreCase(lang)) {
            return "Diese Produkte können nicht verglichen werden, da sie unterschiedlichen Kategorien angehören. " +
                   "Bitte wählen Sie zwei Produkte aus derselben Kategorie.";
        }
        return "These products cannot be compared as they belong to different categories. " +
               "Please choose two products from the same category.";
    }
}
