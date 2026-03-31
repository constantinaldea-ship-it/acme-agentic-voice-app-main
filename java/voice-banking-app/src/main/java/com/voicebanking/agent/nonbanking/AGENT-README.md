# NonBankingServicesAgent

## Overview

The **NonBankingServicesAgent** provides information about bank-offered third-party services and ancillary products. This includes insurance partnerships, travel services, lifestyle benefits, and exclusive offers for Acme Bank customers.

**Category:** 2 - Voice-Enabled Context-Aware Banking (Read)

**Agent ID:** `non-banking-services`

## Key Responsibilities

| Responsibility | Description |
|----------------|-------------|
| Insurance Info | Provide details on insurance partnerships (travel, purchase protection, etc.) |
| Travel Benefits | Describe travel services (Miles & More, airport lounges, concierge) |
| Lifestyle Perks | Explain lifestyle benefits (events, personal shopping, golf) |
| Partner Offers | List exclusive partner discounts and cashback offers |
| Service Contacts | Provide contact information for claims and support |
| Out-of-Scope Clarification | Clarify what services are NOT available |

## Tools Provided

### 1. `getMyBenefits`

Lists all benefits available to the customer based on their card tier.

**Input:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `cardTier` | String | No | STANDARD, GOLD, PLATINUM, BLACK (defaults to STANDARD) |
| `language` | String | No | "en" or "de" (defaults to "en") |
| `category` | String | No | Filter by INSURANCE, TRAVEL, LIFESTYLE, PARTNERS, DIGITAL |

**Output:**
| Field | Type | Description |
|-------|------|-------------|
| `benefits` | Object | Benefits organized by category |
| `totalCount` | Number | Total number of available benefits |
| `voiceResponse` | String | Voice-friendly summary |
| `upgradeSuggestion` | String | What they'd get with card upgrade |

**Example:**
```json
{
  "cardTier": "GOLD",
  "language": "en"
}
```

---

### 2. `getInsuranceInfo`

Gets detailed information about insurance coverage.

**Input:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `insuranceType` | String | No | TRAVEL_MEDICAL, TRIP_CANCELLATION, PURCHASE_PROTECTION, RENTAL_CAR_CDW |
| `cardTier` | String | No | Customer's card tier for eligibility check |
| `language` | String | No | "en" or "de" (defaults to "en") |

**Output:**
| Field | Type | Description |
|-------|------|-------------|
| `insurance` | Object | Detailed insurance coverage info |
| `eligible` | Boolean | Whether customer is eligible |
| `claimContact` | Object | How to file a claim |
| `voiceResponse` | String | Voice-friendly description |

**Example:**
```json
{
  "insuranceType": "TRAVEL_MEDICAL",
  "cardTier": "PLATINUM",
  "language": "de"
}
```

---

### 3. `getTravelBenefits`

Gets travel-related benefits and services.

**Input:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `benefitType` | String | No | AIRPORT_LOUNGE, MILES_AND_MORE, TRAVEL_CONCIERGE, etc. |
| `cardTier` | String | No | Customer's card tier |
| `language` | String | No | "en" or "de" (defaults to "en") |
| `customerId` | String | No | For Miles & More balance lookup |

**Output:**
| Field | Type | Description |
|-------|------|-------------|
| `travelBenefit` | Object | Detailed benefit info |
| `eligible` | Boolean | Whether customer is eligible |
| `accessInstructions` | String | How to use the benefit |
| `milesBalance` | Number | Miles balance (if Miles & More + customerId) |
| `voiceResponse` | String | Voice-friendly description |

**Example:**
```json
{
  "benefitType": "MILES_AND_MORE",
  "cardTier": "PLATINUM",
  "customerId": "C123456"
}
```

---

### 4. `getPartnerOffers`

Gets current partner offers and discounts.

**Input:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `offerType` | String | No | DISCOUNT, CASHBACK, BONUS_POINTS, EXCLUSIVE_ACCESS |
| `cardTier` | String | No | Customer's card tier |
| `language` | String | No | "en" or "de" (defaults to "en") |
| `validOnly` | Boolean | No | Only show currently valid offers (default: true) |

**Output:**
| Field | Type | Description |
|-------|------|-------------|
| `offers` | Array | List of partner offers |
| `count` | Number | Number of offers |
| `voiceResponse` | String | Voice-friendly offer listing |

**Example:**
```json
{
  "cardTier": "GOLD",
  "validOnly": true,
  "language": "en"
}
```

---

### 5. `getServiceContact`

Gets contact information for services.

**Input:**
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `serviceId` | String | No | Specific service ID |
| `insuranceType` | String | No | Insurance type for claim contact |
| `contactType` | String | No | EMERGENCY, CARD_SERVICES, CONCIERGE |
| `language` | String | No | "en" or "de" (defaults to "en") |

**Output:**
| Field | Type | Description |
|-------|------|-------------|
| `contact` | Object | Service contact details |
| `claimContact` | Object | Claim filing details (for insurance) |
| `voiceResponse` | String | Voice-friendly contact info |

**Example:**
```json
{
  "contactType": "EMERGENCY",
  "language": "de"
}
```

---

## Card Tier Hierarchy

| Tier | Level | Description |
|------|-------|-------------|
| STANDARD | 0 | Basic card - limited benefits |
| GOLD | 1 | Mid-tier - enhanced benefits |
| PLATINUM | 2 | Premium - comprehensive benefits |
| BLACK | 3 | Elite - all benefits included |

Higher tiers include all benefits of lower tiers plus additional exclusive services.

## Service Categories

| Category | Examples |
|----------|----------|
| INSURANCE | Travel medical, Trip cancellation, Purchase protection, Rental car CDW |
| TRAVEL | Airport lounges, Miles & More, Concierge, Fast track, Hotel benefits |
| LIFESTYLE | Premium events, Personal shopper, Golf access |
| PARTNERS | Zalando, MediaMarkt, Booking.com, Lufthansa Shop discounts |
| DIGITAL | Identity protection, Cyber security |

## Architecture

```
NonBankingServicesAgent
├── domain/
│   ├── ServiceCategory.java (enum)
│   ├── CardTier.java (enum)
│   ├── ServiceEligibility.java
│   ├── NonBankingService.java (base)
│   ├── InsuranceCoverage.java (extends base)
│   ├── TravelBenefit.java (extends base)
│   └── PartnerOffer.java (extends base)
├── catalog/
│   ├── ServicesCatalog.java (interface)
│   └── StaticServicesCatalog.java (impl)
├── integration/
│   ├── PartnerIntegration.java (interface)
│   ├── MilesAndMoreStub.java
│   └── InsurancePartnerStub.java
├── service/
│   ├── BenefitsService.java
│   ├── EligibilityCheckerService.java
│   └── ServiceContactService.java
└── NonBankingServicesAgent.java
```

## Dependencies

- **ServicesCatalog:** Provides service data
- **BenefitsService:** Benefit retrieval and formatting
- **EligibilityCheckerService:** Eligibility verification
- **ServiceContactService:** Contact information
- **MilesAndMoreStub:** Miles & More integration (stub)
- **InsurancePartnerStub:** Insurance partner integration (stub)

## Bilingual Support

All responses support English (`en`) and German (`de`) via the `language` parameter:

```java
// English response
{
  "language": "en",
  "voiceResponse": "You have access to 15 benefits..."
}

// German response
{
  "language": "de",
  "voiceResponse": "Sie haben Zugang zu 15 Vorteilen..."
}
```

## Example Voice Interactions

### Balance Inquiry
**User:** "What benefits do I have with my Platinum card?"  
**Agent:** "With your Platinum card, you have access to 20 benefits. Insurance: 4 services. Travel: 6 services. Lifestyle: 3 services. Partners: 7 services. Would you like details on a specific category?"

### Insurance Query
**User:** "Tell me about travel medical insurance"  
**Agent:** "Your Platinum card includes travel medical insurance with coverage up to €1,000,000 for emergency medical expenses abroad. Coverage includes hospital stays, emergency evacuation, and repatriation. To file a claim, contact AXA Assistance at +49 800 100-2500."

### Partner Offers
**User:** "What discounts do I have?"  
**Agent:** "You have access to 7 partner offers. 2 expiring soon. Zalando: 15% discount on fashion. MediaMarkt: 10% off electronics. Booking.com: 5% cashback on hotel bookings. Would you like to hear more offers?"

## Error Handling

| Error Case | Response |
|------------|----------|
| Unknown insurance type | "Sorry, I don't recognize that insurance type." |
| Ineligible for service | "This service requires a Gold card or higher. Would you like information about upgrading?" |
| Service not found | "I don't have information about that service." |
| No contact specified | "Please specify which service you need contact information for." |

## Testing

```bash
cd java/voice-banking-app
mvn test -Dtest=NonBankingServicesAgentTest
```

## Related Documentation

- [FR-008 Non-Banking Services](../../../docs/functional-requirements/fr-008-non-banking-services.md)
- [AGENT-008 Implementation Plan](../../../docs/implementation-plan/AGENT-008-non-banking-services.md)
- [Agent Architecture Master](../../../docs/architecture/AGENT-ARCHITECTURE-MASTER.md)

---

**Author:**   
**Created:** 2026-01-25  
**Last Updated:** 2026-01-25
