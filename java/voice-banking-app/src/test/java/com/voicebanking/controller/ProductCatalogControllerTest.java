package com.voicebanking.controller;

import com.voicebanking.agent.product.domain.*;
import com.voicebanking.agent.product.service.FeeCalculationService;
import com.voicebanking.agent.product.service.ProductCatalogService;
import com.voicebanking.agent.product.service.ProductComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProductCatalogController.
 * 
 * Tests REST API endpoints for product catalog operations.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@ExtendWith(MockitoExtension.class)
class ProductCatalogControllerTest {
    
    @Mock
    private ProductCatalogService catalogService;
    
    @Mock
    private FeeCalculationService feeService;
    
    @Mock
    private ProductComparisonService comparisonService;
    
    private ProductCatalogController controller;
    
    @BeforeEach
    void setUp() {
        controller = new ProductCatalogController(catalogService, feeService, comparisonService);
    }
    
    // === GET /api/products Tests ===
    
    @Nested
    @DisplayName("GET /api/products")
    class GetAllProductsTests {
        
        @Test
        @DisplayName("Should return all products grouped by category")
        void shouldReturnAllProducts() {
            List<CreditCardProduct> creditCards = List.of(createMockCreditCard("cc-1"));
            List<GiroKontoProduct> giroKontos = List.of(createMockGiroKonto("gk-1"));
            List<DebitCardProduct> debitCards = List.of(createMockDebitCard("dc-1"));
            
            when(catalogService.getCreditCardProducts()).thenReturn(creditCards);
            when(catalogService.getGiroKontoProducts()).thenReturn(giroKontos);
            when(catalogService.getDebitCardProducts()).thenReturn(debitCards);
            
            ResponseEntity<Map<String, Object>> response = controller.getAllProducts(null, "en");
            
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(3, response.getBody().get("totalCount"));
        }
        
        @Test
        @DisplayName("Should filter by category")
        void shouldFilterByCategory() {
            List<Product> products = List.of(createMockCreditCard("cc-1"));
            when(catalogService.getProductsByCategory(ProductCategory.CREDIT_CARD)).thenReturn(products);
            
            ResponseEntity<Map<String, Object>> response = controller.getAllProducts("CREDIT_CARD", "en");
            
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().get("count"));
        }
        
        @Test
        @DisplayName("Should return error for invalid category")
        void shouldReturnErrorForInvalidCategory() {
            ResponseEntity<Map<String, Object>> response = controller.getAllProducts("INVALID", "en");
            
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().containsKey("error"));
        }
    }
    
    // === GET /api/products/credit-cards Tests ===
    
    @Nested
    @DisplayName("GET /api/products/credit-cards")
    class GetCreditCardsTests {
        
        @Test
        @DisplayName("Should return credit cards")
        void shouldReturnCreditCards() {
            List<CreditCardProduct> products = List.of(
                createMockCreditCard("cc-standard"),
                createMockCreditCard("cc-gold")
            );
            when(catalogService.getCreditCardProducts()).thenReturn(products);
            
            ResponseEntity<Map<String, Object>> response = controller.getCreditCards("en");
            
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(2, response.getBody().get("count"));
            assertEquals("CREDIT_CARD", response.getBody().get("category"));
        }
    }
    
    // === GET /api/products/giro-kontos Tests ===
    
    @Nested
    @DisplayName("GET /api/products/giro-kontos")
    class GetGiroKontosTests {
        
        @Test
        @DisplayName("Should return Giro Kontos")
        void shouldReturnGiroKontos() {
            List<GiroKontoProduct> products = List.of(createMockGiroKonto("gk-aktiv"));
            when(catalogService.getGiroKontoProducts()).thenReturn(products);
            
            ResponseEntity<Map<String, Object>> response = controller.getGiroKontos("en");
            
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().get("count"));
        }
    }
    
    // === GET /api/products/{id} Tests ===
    
    @Nested
    @DisplayName("GET /api/products/{id}")
    class GetProductByIdTests {
        
        @Test
        @DisplayName("Should return product by ID")
        void shouldReturnProductById() {
            CreditCardProduct product = createMockCreditCard("cc-gold");
            when(catalogService.getProductById("cc-gold")).thenReturn(Optional.of(product));
            when(feeService.getFeeSchedule("cc-gold")).thenReturn(Optional.empty());
            
            ResponseEntity<Map<String, Object>> response = controller.getProductById("cc-gold", "en");
            
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("cc-gold", response.getBody().get("id"));
        }
        
        @Test
        @DisplayName("Should return 404 for unknown product")
        void shouldReturn404ForUnknownProduct() {
            when(catalogService.getProductById("unknown")).thenReturn(Optional.empty());
            
            ResponseEntity<Map<String, Object>> response = controller.getProductById("unknown", "en");
            
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }
    
    // === GET /api/products/{id}/fees Tests ===
    
    @Nested
    @DisplayName("GET /api/products/{id}/fees")
    class GetProductFeesTests {
        
        @Test
        @DisplayName("Should return fees for product")
        void shouldReturnFeesForProduct() {
            CreditCardProduct product = createMockCreditCard("cc-gold");
            FeeSchedule schedule = createMockFeeSchedule("cc-gold");
            
            when(catalogService.getProductById("cc-gold")).thenReturn(Optional.of(product));
            when(feeService.getFeeSchedule("cc-gold")).thenReturn(Optional.of(schedule));
            when(feeService.getDetailedFeesForVoice("cc-gold", "en")).thenReturn("Annual fee is €89.");
            when(feeService.calculateAnnualCost("cc-gold")).thenReturn(new BigDecimal("89.00"));
            
            ResponseEntity<Map<String, Object>> response = controller.getProductFees("cc-gold", "en");
            
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("cc-gold", response.getBody().get("productId"));
            assertNotNull(response.getBody().get("voiceDescription"));
        }
        
        @Test
        @DisplayName("Should return 404 for unknown product")
        void shouldReturn404ForUnknownProductFees() {
            when(catalogService.getProductById("unknown")).thenReturn(Optional.empty());
            
            ResponseEntity<Map<String, Object>> response = controller.getProductFees("unknown", "en");
            
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }
    
    // === GET /api/products/compare Tests ===
    
    @Nested
    @DisplayName("GET /api/products/compare")
    class CompareProductsTests {
        
        @Test
        @DisplayName("Should compare two products")
        void shouldCompareTwoProducts() {
            ProductComparison comparison = createMockComparison();
            
            when(comparisonService.canCompare("cc-standard", "cc-gold")).thenReturn(true);
            when(comparisonService.compareProducts("cc-standard", "cc-gold")).thenReturn(Optional.of(comparison));
            when(comparisonService.formatComparisonForVoice(any(), eq("en"))).thenReturn("Comparing cards...");
            
            ResponseEntity<Map<String, Object>> response = controller.compareProducts("cc-standard", "cc-gold", "en");
            
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertNotNull(response.getBody().get("disclaimer"));
        }
        
        @Test
        @DisplayName("Should return error for incompatible categories")
        void shouldReturnErrorForIncompatibleCategories() {
            when(comparisonService.canCompare("cc-standard", "gk-aktiv")).thenReturn(false);
            when(comparisonService.getNotComparableMessage("cc-standard", "gk-aktiv", "en"))
                .thenReturn("Cannot compare credit card with checking account.");
            
            ResponseEntity<Map<String, Object>> response = controller.compareProducts("cc-standard", "gk-aktiv", "en");
            
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertTrue(response.getBody().containsKey("error"));
        }
    }
    
    // === Helper Methods ===
    
    private CreditCardProduct createMockCreditCard(String id) {
        return CreditCardProduct.creditCardBuilder()
            .id(id)
            .nameEn("Credit Card " + id)
            .nameDe("Kreditkarte " + id)
            .descriptionEn("Description")
            .descriptionDe("Beschreibung")
            .isActive(true)
            .creditLimitMin(1000.0)
            .creditLimitMax(10000.0)
            .interestRate(12.0)
            .build();
    }
    
    private GiroKontoProduct createMockGiroKonto(String id) {
        return GiroKontoProduct.giroKontoBuilder()
            .id(id)
            .nameEn("Giro Konto " + id)
            .nameDe("Girokonto " + id)
            .descriptionEn("Description")
            .descriptionDe("Beschreibung")
            .isActive(true)
            .includedTransactionsPerMonth(null)
            .overdraftInterestRate(10.0)
            .debitCardIncluded(true)
            .build();
    }
    
    private DebitCardProduct createMockDebitCard(String id) {
        return DebitCardProduct.debitCardBuilder()
            .id(id)
            .nameEn("Debit Card " + id)
            .nameDe("Debitkarte " + id)
            .descriptionEn("Description")
            .descriptionDe("Beschreibung")
            .isActive(true)
            .cardNetwork("Mastercard")
            .dailyWithdrawalLimit(1000.0)
            .contactlessLimit(50)
            .build();
    }
    
    private FeeSchedule createMockFeeSchedule(String productId) {
        Fee annualFee = Fee.builder()
            .id("fee-annual")
            .nameEn("Annual Fee")
            .nameDe("Jahresgebühr")
            .type(FeeType.PERIODIC)
            .amount(new BigDecimal("89.00"))
            .frequency(FeeFrequency.ANNUAL)
            .build();
        
        return FeeSchedule.builder()
            .productId(productId)
            .productName("Test Product")
            .fees(List.of(annualFee))
            .build();
    }
    
    private ProductComparison createMockComparison() {
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
            .comparisonPoints(List.of())
            .build();
    }
}
