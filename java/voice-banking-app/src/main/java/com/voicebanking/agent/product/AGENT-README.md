# ProductInformationAgent

**Agent ID:** `product-information`  
**Category:** Category 2 — Voice-Enabled Context-Aware Banking (Read)  
**Architecture Component:** Component E (AI Functional Agents)  
**Status:** ✅ Implemented  
**Related FRs:** FR-002 (Credit Card Product Info), FR-003 (Giro Konto/Debit Info)

---

## Overview

The ProductInformationAgent provides detailed product information for Acme Bank's retail banking products, including Credit Cards, Giro Konto (checking accounts), and Debit Cards. It is designed for voice-first interactions with bilingual support (English and German).

Per the Scope Document (Slide 10), product information inquiries are the **second highest volume intent** after general information, making this agent critical for customer service automation.

## Supported Products

### Credit Cards
| Product ID | Name (EN) | Annual Fee |
|------------|-----------|------------|
| cc-standard | Standard Credit Card | €29 |
| cc-gold | Gold Credit Card | €89 |
| cc-platinum | Platinum Credit Card | €299 |
| cc-business | Business Credit Card | €0 (with conditions) |

### Giro Konto (Checking Accounts)
| Product ID | Name | Monthly Fee |
|------------|------|-------------|
| gk-aktiv | AktivKonto | €4.90 |
| gk-best | BestKonto | €0 (with salary deposit) |
| gk-jung | Junges Konto | €0 (age 18-30) |
| gk-basis | Basiskonto | €6.90 |

### Debit Cards
| Product ID | Name | Network |
|------------|------|---------|
| dc-mastercard | Debit Mastercard | Mastercard |
| dc-girocard | girocard | girocard |

## Tools

### 1. `getCreditCardProducts`

Lists all available credit card products.

**Input:**
```json
{
  "lang": "en",       // Optional: "en" or "de", defaults to "en"
  "detailed": false   // Optional: return full product details
}
```

**Output:**
```json
{
  "success": true,
  "products": [...],
  "count": 4,
  "voiceResponse": "We offer 4 credit cards: Standard Card at 29 euros per year..."
}
```

### 2. `getCreditCardFees`

Gets fee schedule for a specific credit card or all credit cards.

**Input:**
```json
{
  "productId": "cc-gold",  // Optional: specific product, or omit for all
  "lang": "en"
}
```

**Output:**
```json
{
  "success": true,
  "productId": "cc-gold",
  "productName": "Gold Credit Card",
  "fees": { ... },
  "voiceResponse": "The Gold Credit Card has an annual fee of 89 euros..."
}
```

### 3. `getGiroKontoProducts`

Lists all available Giro Konto (checking account) products.

**Input:**
```json
{
  "lang": "de",
  "detailed": true
}
```

**Output:**
```json
{
  "success": true,
  "products": [...],
  "count": 4,
  "voiceResponse": "Wir bieten 4 Konten an: AktivKonto für 4,90 Euro monatlich..."
}
```

### 4. `getGiroKontoFees`

Gets fee schedule for a specific Giro Konto or all accounts.

**Input:**
```json
{
  "productId": "gk-aktiv",
  "lang": "en"
}
```

**Output:**
```json
{
  "success": true,
  "productId": "gk-aktiv",
  "productName": "AktivKonto",
  "fees": { ... },
  "voiceResponse": "The AktivKonto has a monthly fee of 4 euros 90 cents..."
}
```

### 5. `getDebitCardInfo`

Gets information about debit cards, optionally filtered by type.

**Input:**
```json
{
  "cardType": "mastercard",  // Optional: "mastercard" or "girocard"
  "lang": "en"
}
```

**Output:**
```json
{
  "success": true,
  "cards": [...],
  "count": 1,
  "voiceResponse": "The Debit Mastercard allows withdrawals up to 1000 euros per day..."
}
```

### 6. `compareProducts`

Compares two products of the same category with factual information only.

**Input:**
```json
{
  "product1": "cc-standard",
  "product2": "cc-gold",
  "lang": "en"
}
```

**Output:**
```json
{
  "success": true,
  "comparison": {
    "product1": "Standard Credit Card",
    "product2": "Gold Credit Card",
    "category": "CREDIT_CARD",
    "comparisonPoints": [...]
  },
  "voiceResponse": "Comparing Standard Credit Card and Gold Credit Card...",
  "disclaimer": "This comparison is for information only. Please consult an advisor for personalized recommendations."
}
```

⚠️ **Policy Compliance:** The agent provides factual comparisons only and does NOT make recommendations. All comparisons include a disclaimer directing customers to advisors.

## Architecture

```
ProductInformationAgent
├── ProductCatalogService     # Product retrieval and voice formatting
├── FeeCalculationService     # Fee schedules and calculations
└── ProductComparisonService  # Factual product comparisons

ProductCatalogClient (Interface)
└── MockProductCatalogClient  # Mock implementation with test data
```

## Package Structure

```
com.voicebanking.agent.product
├── ProductInformationAgent.java          # Main agent class
├── domain/
│   ├── ProductCategory.java              # Enum: CREDIT_CARD, GIRO_KONTO, DEBIT_CARD
│   ├── FeeType.java                      # Enum: ANNUAL_FEE, MONTHLY_FEE, etc.
│   ├── FeeFrequency.java                 # Enum: MONTHLY, YEARLY, PER_TRANSACTION
│   ├── Fee.java                          # Fee with amount and description
│   ├── FeeSchedule.java                  # Collection of fees for a product
│   ├── ProductFeature.java               # Feature with included flag
│   ├── EligibilityCriteria.java          # Eligibility requirements
│   ├── Product.java                      # Base product class
│   ├── CreditCardProduct.java            # Credit card specific fields
│   ├── GiroKontoProduct.java             # Giro Konto specific fields
│   ├── DebitCardProduct.java             # Debit card specific fields
│   └── ProductComparison.java            # Comparison result with disclaimer
├── integration/
│   ├── ProductCatalogClient.java         # Interface for catalog operations
│   └── MockProductCatalogClient.java     # Mock with Acme Bank products
└── service/
    ├── ProductCatalogService.java        # Product retrieval service
    ├── FeeCalculationService.java        # Fee calculation service
    └── ProductComparisonService.java     # Comparison service
```

## Voice Response Examples

### English
> "We offer 4 credit cards. The Standard Credit Card costs 29 euros per year and offers a credit limit up to 5000 euros. The Gold Credit Card costs 89 euros per year and includes travel insurance."

### German (Deutsch)
> "Wir bieten 4 Kreditkarten an. Die Standardkarte kostet 29 Euro pro Jahr mit einem Kreditrahmen bis zu 5000 Euro. Die Goldkarte kostet 89 Euro pro Jahr und beinhaltet eine Reiseversicherung."

## Testing

Run unit tests:
```bash
cd java/voice-banking-app
mvn test -Dtest=ProductInformationAgentTest
```

Tests cover:
- All 6 tools with valid inputs
- Language switching (en/de)
- Error handling for unknown products
- Comparison validation (same category requirement)
- Exception handling for service failures

## Error Handling

| Error | Voice Response |
|-------|---------------|
| Product not found | "I couldn't find a credit card with that name. Would you like me to list all available credit cards?" |
| Products not comparable | "These products are in different categories and cannot be directly compared." |
| Service unavailable | "I'm having trouble accessing product information. Please try again." |

## Future Enhancements

1. **Live Product Catalog Integration** — Replace MockProductCatalogClient with real API client
2. **Eligibility Checking** — Evaluate customer eligibility based on profile
3. **Promotional Pricing** — Support for time-limited offers
4. **Product Recommendations** — With advisor handover for compliance

---

*Last updated: 2026-01-25 by *
