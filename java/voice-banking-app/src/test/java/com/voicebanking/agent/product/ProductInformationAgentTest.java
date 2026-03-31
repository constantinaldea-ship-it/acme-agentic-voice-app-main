package com.voicebanking.agent.product;

import com.voicebanking.agent.product.domain.*;
import com.voicebanking.agent.product.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductInformationAgent.
 * 
 * Tests all 6 tools with various input scenarios, edge cases, and error handling.
 * Uses Mockito to stub service layer dependencies.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@ExtendWith(MockitoExtension.class)
class ProductInformationAgentTest {
    
    @Mock
    private ProductCatalogService catalogService;
    
    @Mock
    private FeeCalculationService feeService;
    
    @Mock
    private ProductComparisonService comparisonService;
    
    private ProductInformationAgent agent;
    
    @BeforeEach
    void setUp() {
        agent = new ProductInformationAgent(catalogService, feeService, comparisonService);
    }
    
    // === Agent Interface Tests ===
    
    @Test
    @DisplayName("Agent ID should be 'product-information'")
    void agentId_shouldBeProductInformation() {
        assertEquals("product-information", agent.getAgentId());
    }
    
    @Test
    @DisplayName("Agent should provide meaningful description")
    void description_shouldBeMeaningful() {
        String description = agent.getDescription();
        assertNotNull(description);
        assertTrue(description.contains("Credit Card") || description.contains("product"));
    }
    
    @Test
    @DisplayName("Agent should expose exactly 6 tools")
    void toolIds_shouldContainSixTools() {
        List<String> tools = agent.getToolIds();
        assertEquals(6, tools.size());
        assertTrue(tools.contains("getCreditCardProducts"));
        assertTrue(tools.contains("getCreditCardFees"));
        assertTrue(tools.contains("getGiroKontoProducts"));
        assertTrue(tools.contains("getGiroKontoFees"));
        assertTrue(tools.contains("getDebitCardInfo"));
        assertTrue(tools.contains("compareProducts"));
    }
    
    @Test
    @DisplayName("Unknown tool should return error response")
    void executeTool_unknownTool_shouldReturnError() {
        Map<String, Object> result = agent.executeTool("unknownTool", Map.of());
        
        assertFalse((Boolean) result.get("success"));
        assertNotNull(result.get("error"));
        assertTrue(result.get("error").toString().contains("Unknown tool"));
    }
    
    // === getCreditCardProducts Tests ===
    
    @Nested
    @DisplayName("getCreditCardProducts Tool")
    class GetCreditCardProductsTests {
        
        @Test
        @DisplayName("Should return list of credit cards")
        void shouldReturnCreditCardList() {
            List<CreditCardProduct> mockProducts = List.of(
                createMockCreditCard("cc-standard", "Standard Card", "Standardkarte"),
                createMockCreditCard("cc-gold", "Gold Card", "Goldkarte")
            );
            when(catalogService.getCreditCardProducts()).thenReturn(mockProducts);
            when(catalogService.formatProductListForVoice(anyList(), anyString())).thenReturn("We offer 2 cards.");
            
            Map<String, Object> result = agent.executeTool("getCreditCardProducts", Map.of());
            
            assertTrue((Boolean) result.get("success"));
            assertEquals(2, result.get("count"));
            assertNotNull(result.get("products"));
            assertNotNull(result.get("voiceResponse"));
        }
        
        @Test
        @DisplayName("Should support German language")
        void shouldSupportGermanLanguage() {
            List<CreditCardProduct> mockProducts = List.of(
                createMockCreditCard("cc-standard", "Standard Card", "Standardkarte")
            );
            when(catalogService.getCreditCardProducts()).thenReturn(mockProducts);
            when(catalogService.formatProductListForVoice(anyList(), eq("de"))).thenReturn("Wir bieten 1 Karte.");
            
            Map<String, Object> result = agent.executeTool("getCreditCardProducts", Map.of("lang", "de"));
            
            assertTrue((Boolean) result.get("success"));
            verify(catalogService).formatProductListForVoice(anyList(), eq("de"));
        }
        
        @Test
        @DisplayName("Should support detailed mode")
        void shouldSupportDetailedMode() {
            List<CreditCardProduct> mockProducts = List.of(
                createMockCreditCard("cc-standard", "Standard Card", "Standardkarte")
            );
            when(catalogService.getCreditCardProducts()).thenReturn(mockProducts);
            when(catalogService.formatProductListForVoice(anyList(), anyString())).thenReturn("Details.");
            
            Map<String, Object> result = agent.executeTool("getCreditCardProducts", 
                Map.of("detailed", true));
            
            assertTrue((Boolean) result.get("success"));
            // Detailed mode should return full product maps (toMap vs toBriefMap)
            List<?> products = (List<?>) result.get("products");
            assertFalse(products.isEmpty());
        }
    }
    
    // === getCreditCardFees Tests ===
    
    @Nested
    @DisplayName("getCreditCardFees Tool")
    class GetCreditCardFeesTests {
        
        @Test
        @DisplayName("Should return fees for specific product")
        void shouldReturnFeesForSpecificProduct() {
            CreditCardProduct mockCard = createMockCreditCard("cc-gold", "Gold Card", "Goldkarte");
            FeeSchedule mockSchedule = createMockFeeSchedule();
            
            when(catalogService.getCreditCardById("cc-gold")).thenReturn(Optional.of(mockCard));
            when(feeService.getFeeSchedule("cc-gold")).thenReturn(Optional.of(mockSchedule));
            when(feeService.getDetailedFeesForVoice("cc-gold", "en")).thenReturn("Gold Card has annual fee.");
            
            Map<String, Object> result = agent.executeTool("getCreditCardFees", 
                Map.of("productId", "cc-gold"));
            
            assertTrue((Boolean) result.get("success"));
            assertEquals("cc-gold", result.get("productId"));
            assertNotNull(result.get("fees"));
            assertNotNull(result.get("voiceResponse"));
        }
        
        @Test
        @DisplayName("Should return all fees when no productId specified")
        void shouldReturnAllFeesWhenNoProductId() {
            List<CreditCardProduct> mockProducts = List.of(
                createMockCreditCard("cc-standard", "Standard", "Standard"),
                createMockCreditCard("cc-gold", "Gold", "Gold")
            );
            FeeSchedule mockSchedule = createMockFeeSchedule();
            
            when(catalogService.getCreditCardProducts()).thenReturn(mockProducts);
            when(feeService.getFeeSchedule(anyString())).thenReturn(Optional.of(mockSchedule));
            
            Map<String, Object> result = agent.executeTool("getCreditCardFees", Map.of());
            
            assertTrue((Boolean) result.get("success"));
            assertEquals(2, result.get("count"));
        }
        
        @Test
        @DisplayName("Should return error for unknown product")
        void shouldReturnErrorForUnknownProduct() {
            when(catalogService.getCreditCardById("cc-unknown")).thenReturn(Optional.empty());
            
            Map<String, Object> result = agent.executeTool("getCreditCardFees", 
                Map.of("productId", "cc-unknown"));
            
            assertFalse((Boolean) result.get("success"));
            assertNotNull(result.get("error"));
            assertNotNull(result.get("voiceResponse"));
        }
    }
    
    // === getGiroKontoProducts Tests ===
    
    @Nested
    @DisplayName("getGiroKontoProducts Tool")
    class GetGiroKontoProductsTests {
        
        @Test
        @DisplayName("Should return list of Giro Konto products")
        void shouldReturnGiroKontoList() {
            List<GiroKontoProduct> mockProducts = List.of(
                createMockGiroKonto("gk-aktiv", "AktivKonto", "AktivKonto"),
                createMockGiroKonto("gk-best", "BestKonto", "BestKonto")
            );
            when(catalogService.getGiroKontoProducts()).thenReturn(mockProducts);
            when(catalogService.formatProductListForVoice(anyList(), anyString())).thenReturn("We offer 2 accounts.");
            
            Map<String, Object> result = agent.executeTool("getGiroKontoProducts", Map.of());
            
            assertTrue((Boolean) result.get("success"));
            assertEquals(2, result.get("count"));
            assertNotNull(result.get("products"));
        }
    }
    
    // === getGiroKontoFees Tests ===
    
    @Nested
    @DisplayName("getGiroKontoFees Tool")
    class GetGiroKontoFeesTests {
        
        @Test
        @DisplayName("Should return fees for specific Giro Konto")
        void shouldReturnFeesForSpecificGiroKonto() {
            GiroKontoProduct mockAccount = createMockGiroKonto("gk-aktiv", "AktivKonto", "AktivKonto");
            FeeSchedule mockSchedule = createMockFeeSchedule();
            
            when(catalogService.getGiroKontoById("gk-aktiv")).thenReturn(Optional.of(mockAccount));
            when(feeService.getFeeSchedule("gk-aktiv")).thenReturn(Optional.of(mockSchedule));
            when(feeService.getDetailedFeesForVoice("gk-aktiv", "en")).thenReturn("AktivKonto has monthly fee.");
            
            Map<String, Object> result = agent.executeTool("getGiroKontoFees", 
                Map.of("productId", "gk-aktiv"));
            
            assertTrue((Boolean) result.get("success"));
            assertEquals("gk-aktiv", result.get("productId"));
            assertNotNull(result.get("fees"));
        }
        
        @Test
        @DisplayName("Should return error for unknown Giro Konto")
        void shouldReturnErrorForUnknownGiroKonto() {
            when(catalogService.getGiroKontoById("gk-unknown")).thenReturn(Optional.empty());
            
            Map<String, Object> result = agent.executeTool("getGiroKontoFees", 
                Map.of("productId", "gk-unknown"));
            
            assertFalse((Boolean) result.get("success"));
            assertNotNull(result.get("error"));
        }
    }
    
    // === getDebitCardInfo Tests ===
    
    @Nested
    @DisplayName("getDebitCardInfo Tool")
    class GetDebitCardInfoTests {
        
        @Test
        @DisplayName("Should return all debit cards when no type specified")
        void shouldReturnAllDebitCards() {
            List<DebitCardProduct> mockCards = List.of(
                createMockDebitCard("dc-mastercard", "Debit Mastercard", "Mastercard"),
                createMockDebitCard("dc-girocard", "girocard", "girocard")
            );
            when(catalogService.getDebitCardProducts()).thenReturn(mockCards);
            
            Map<String, Object> result = agent.executeTool("getDebitCardInfo", Map.of());
            
            assertTrue((Boolean) result.get("success"));
            assertEquals(2, result.get("count"));
        }
        
        @Test
        @DisplayName("Should filter by card type")
        void shouldFilterByCardType() {
            List<DebitCardProduct> mockCards = List.of(
                createMockDebitCard("dc-mastercard", "Debit Mastercard", "Mastercard"),
                createMockDebitCard("dc-girocard", "girocard", "girocard")
            );
            when(catalogService.getDebitCardProducts()).thenReturn(mockCards);
            
            Map<String, Object> result = agent.executeTool("getDebitCardInfo", 
                Map.of("cardType", "mastercard"));
            
            assertTrue((Boolean) result.get("success"));
            assertEquals(1, result.get("count"));
        }
        
        @Test
        @DisplayName("Should return error for unknown card type")
        void shouldReturnErrorForUnknownCardType() {
            List<DebitCardProduct> mockCards = List.of(
                createMockDebitCard("dc-mastercard", "Debit Mastercard", "Mastercard")
            );
            when(catalogService.getDebitCardProducts()).thenReturn(mockCards);
            
            Map<String, Object> result = agent.executeTool("getDebitCardInfo", 
                Map.of("cardType", "visa"));
            
            assertFalse((Boolean) result.get("success"));
            assertNotNull(result.get("voiceResponse"));
        }
    }
    
    // === compareProducts Tests ===
    
    @Nested
    @DisplayName("compareProducts Tool")
    class CompareProductsTests {
        
        @Test
        @DisplayName("Should compare two products successfully")
        void shouldCompareTwoProducts() {
            ProductComparison mockComparison = createMockComparison();
            
            when(comparisonService.canCompare("cc-standard", "cc-gold")).thenReturn(true);
            when(comparisonService.compareProducts("cc-standard", "cc-gold")).thenReturn(Optional.of(mockComparison));
            when(comparisonService.formatComparisonForVoice(any(), eq("en"))).thenReturn("Comparing cards...");
            
            Map<String, Object> result = agent.executeTool("compareProducts", 
                Map.of("product1", "cc-standard", "product2", "cc-gold"));
            
            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("comparison"));
            assertNotNull(result.get("disclaimer"));
        }
        
        @Test
        @DisplayName("Should return error when products missing")
        void shouldReturnErrorWhenProductsMissing() {
            Map<String, Object> result = agent.executeTool("compareProducts", Map.of());
            
            assertFalse((Boolean) result.get("success"));
            assertTrue(result.get("error").toString().contains("required"));
        }
        
        @Test
        @DisplayName("Should return error when products not comparable")
        void shouldReturnErrorWhenProductsNotComparable() {
            when(comparisonService.canCompare("cc-standard", "gk-aktiv")).thenReturn(false);
            when(comparisonService.getNotComparableMessage("cc-standard", "gk-aktiv", "en"))
                .thenReturn("Cannot compare credit card with checking account.");
            
            Map<String, Object> result = agent.executeTool("compareProducts", 
                Map.of("product1", "cc-standard", "product2", "gk-aktiv"));
            
            assertFalse((Boolean) result.get("success"));
            assertTrue(result.get("error").toString().contains("not comparable"));
        }
        
        @Test
        @DisplayName("Should return error when comparison fails")
        void shouldReturnErrorWhenComparisonFails() {
            when(comparisonService.canCompare("cc-standard", "cc-unknown")).thenReturn(true);
            when(comparisonService.compareProducts("cc-standard", "cc-unknown")).thenReturn(Optional.empty());
            
            Map<String, Object> result = agent.executeTool("compareProducts", 
                Map.of("product1", "cc-standard", "product2", "cc-unknown"));
            
            assertFalse((Boolean) result.get("success"));
        }
    }
    
    // === Exception Handling Tests ===
    
    @Nested
    @DisplayName("Exception Handling")
    class ExceptionHandlingTests {
        
        @Test
        @DisplayName("Should handle service exceptions gracefully")
        void shouldHandleServiceExceptions() {
            when(catalogService.getCreditCardProducts()).thenThrow(new RuntimeException("Service unavailable"));
            
            Map<String, Object> result = agent.executeTool("getCreditCardProducts", Map.of());
            
            assertFalse((Boolean) result.get("success"));
            assertNotNull(result.get("error"));
            assertTrue(result.get("error").toString().contains("Service unavailable"));
        }
    }
    
    // === Helper Methods ===
    
    private CreditCardProduct createMockCreditCard(String id, String nameEn, String nameDe) {
        return CreditCardProduct.creditCardBuilder()
            .id(id)
            .nameEn(nameEn)
            .nameDe(nameDe)
            .descriptionEn("Description")
            .descriptionDe("Beschreibung")
            .isActive(true)
            .creditLimitMin(new BigDecimal("1000"))
            .creditLimitMax(new BigDecimal("10000"))
            .interestRate(new BigDecimal("12.0"))
            .build();
    }
    
    private GiroKontoProduct createMockGiroKonto(String id, String nameEn, String nameDe) {
        return GiroKontoProduct.giroKontoBuilder()
            .id(id)
            .nameEn(nameEn)
            .nameDe(nameDe)
            .descriptionEn("Description")
            .descriptionDe("Beschreibung")
            .isActive(true)
            .includedTransactionsPerMonth(-1)
            .overdraftInterestRate(new BigDecimal("10.0"))
            .debitCardIncluded(true)
            .build();
    }
    
    private DebitCardProduct createMockDebitCard(String id, String nameEn, String network) {
        return DebitCardProduct.debitCardBuilder()
            .id(id)
            .nameEn(nameEn)
            .nameDe(nameEn)
            .descriptionEn("Description")
            .descriptionDe("Beschreibung")
            .isActive(true)
            .cardNetwork(network)
            .dailyWithdrawalLimit(new BigDecimal("1000"))
            .contactlessLimit(50)
            .build();
    }
    
    private FeeSchedule createMockFeeSchedule() {
        Fee annualFee = Fee.builder()
            .id("fee-annual")
            .nameEn("Annual fee")
            .nameDe("Jahresgebühr")
            .type(FeeType.PERIODIC)
            .amount(new BigDecimal("50.00"))
            .frequency(FeeFrequency.ANNUAL)
            .build();
        
        return FeeSchedule.builder()
            .productId("product-id")
            .fees(List.of(annualFee))
            .build();
    }
    
    private ProductComparison createMockComparison() {
        ProductComparison.ComparisonPoint point = ProductComparison.ComparisonPoint.pointBuilder()
            .attributeEn("Annual Fee")
            .attributeDe("Jahresgebühr")
            .product1ValueEn("€50")
            .product1ValueDe("50 €")
            .product2ValueEn("€100")
            .product2ValueDe("100 €")
            .significantDifference(true)
            .build();
        
        // Create mock products for the comparison
        Product product1 = Product.builder()
            .id("cc-standard")
            .nameEn("Standard Card")
            .nameDe("Standardkarte")
            .category(ProductCategory.CREDIT_CARD)
            .build();
        
        Product product2 = Product.builder()
            .id("cc-gold")
            .nameEn("Gold Card")
            .nameDe("Goldkarte")
            .category(ProductCategory.CREDIT_CARD)
            .build();
        
        return ProductComparison.builder()
            .product1(product1)
            .product2(product2)
            .comparisonPoints(List.of(point))
            .build();
    }
}
