# BFA Service (Resource-Oriented REST)

## Overview

This module provides a **resource-oriented REST API** as an alternative to the operation-based BFA service (`bfa-service`). 
It addresses the architectural concerns identified in [ADR-BFA-001](../docs/architecture/bfa/adr/ADR-BFA-001-agentic-aware-design.md) 
stakeholder analysis.

## Design Philosophy

| Concern | Operation-Based (`bfa-service`) | Resource-Oriented (this module) |
|---------|--------------------------------|--------------------------------|
| **API Pattern** | Single `/execute` endpoint | RESTful resource endpoints |
| **Type Safety** | `Map<String, Object> params` | Typed DTOs with validation |
| **Security** | Centralized in gateway | Per-endpoint via interceptors |
| **Observability** | Operation-level parsing needed | Path-based metrics out-of-box |
| **API Gateway** | Custom policy enforcement | Standard route-based policies |
| **OpenAPI** | Manual schema maintenance | Auto-generated from DTOs |

## Quick Start

```bash
# Build
cd java/bfa-service-resource
mvn clean package

# Run (port 8082)
mvn spring-boot:run

# Health check
curl http://localhost:8082/api/v1/health

# API Documentation
open http://localhost:8082/swagger-ui.html
```

## Endpoints

### Accounts

| Method | Endpoint | Description | Consent Required |
|--------|----------|-------------|------------------|
| `GET` | `/api/v1/accounts` | List all accounts | `VIEW_ACCOUNTS` |
| `GET` | `/api/v1/accounts/{id}` | Get account details | `VIEW_ACCOUNTS` |
| `GET` | `/api/v1/accounts/{id}/balance` | Get account balance | `VIEW_BALANCE` |
| `GET` | `/api/v1/accounts/{id}/transactions` | Get transactions | `VIEW_TRANSACTIONS` |

### Credit Cards

| Method | Endpoint | Description | Consent Required |
|--------|----------|-------------|------------------|
| `GET` | `/api/v1/cards` | List all cards | `VIEW_CARDS` |
| `GET` | `/api/v1/cards/{id}` | Get card details | `VIEW_CARDS` |
| `GET` | `/api/v1/cards/{id}/balance` | Get card balance | `VIEW_BALANCE` |
| `GET` | `/api/v1/cards/{id}/transactions` | Get card transactions | `VIEW_TRANSACTIONS` |
| `GET` | `/api/v1/cards/{id}/limit` | Get credit limit & utilization | `VIEW_CARD_LIMIT` |
| `GET` | `/api/v1/cards/{id}/rewards` | Get rewards balance & options | `VIEW_REWARDS` |
| `GET` | `/api/v1/cards/{id}/statements/{period}` | Get statement (YYYY-MM) | `VIEW_STATEMENTS` |

### Transfers

| Method | Endpoint | Description | Consent Required |
|--------|----------|-------------|------------------|
| `POST` | `/api/v1/transfers` | Initiate transfer | `INITIATE_TRANSFER` |
| `GET` | `/api/v1/transfers/{id}` | Get transfer status | `VIEW_TRANSFERS` |

### Personal Finance

| Method | Endpoint | Description | Consent Required |
|--------|----------|-------------|------------------|
| `GET` | `/api/v1/finance/spending/breakdown` | Get spending by category | `VIEW_TRANSACTIONS` |
| `GET` | `/api/v1/finance/spending/trend` | Get monthly spending trend | `VIEW_TRANSACTIONS` |
| `GET` | `/api/v1/finance/spending/merchants` | Get top merchants | `VIEW_TRANSACTIONS` |
| `GET` | `/api/v1/finance/cashflow/summary` | Get income vs expenses | `VIEW_TRANSACTIONS`, `VIEW_INCOME` |
| `GET` | `/api/v1/finance/budgets/status` | Get budget status | `VIEW_TRANSACTIONS`, `VIEW_BUDGETS` |
| `GET` | `/api/v1/finance/recurring/detected` | Get recurring transactions | `VIEW_TRANSACTIONS` |
| `GET` | `/api/v1/finance/anomalies/unusual` | Get unusual activity | `VIEW_TRANSACTIONS` |

### Non-Banking Services

| Method | Endpoint | Description | Consent Required |
|--------|----------|-------------|------------------|
| `GET` | `/api/v1/services/benefits` | Get all benefits by card tier | `benefits:read` |
| `GET` | `/api/v1/services/insurance` | Get insurance coverage info | `insurance:read` |
| `GET` | `/api/v1/services/travel` | Get travel benefits & miles | `travel:read` |
| `GET` | `/api/v1/services/offers` | Get partner offers & discounts | `offers:read` |
| `GET` | `/api/v1/services/contacts` | Get service contact info | `contacts:read` |

## Security Architecture

### Request Flow

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  BfaSecurityFilter (Servlet Filter)                     │
│  - Extract & validate Authorization header               │
│  - Set SecurityContext with authenticated principal      │
│  - Reject unauthenticated requests (401)                 │
└─────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  ConsentInterceptor (HandlerInterceptor)                │
│  - Read @RequiresConsent annotation from handler        │
│  - Check session consent state (server-side)            │
│  - Reject if consent missing (403)                      │
└─────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  LegitimationInterceptor (HandlerInterceptor)           │
│  - Extract resource ID from path variables              │
│  - Check AcmeLegi authorization for resource            │
│  - Reject if not authorized (403)                       │
└─────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  Controller Handler Method                              │
│  - Business logic execution                              │
│  - Return typed DTO response                             │
└─────────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────────┐
│  AuditInterceptor (HandlerInterceptor.afterCompletion)  │
│  - Log request/response with correlation ID             │
│  - Record success/failure metrics                        │
└─────────────────────────────────────────────────────────┘
```

### Key Security Principles

1. **Server-Derived Identity**: User identity comes from the validated JWT token, NOT from request body
2. **Registry-Defined Consents**: Consent requirements are declared via `@RequiresConsent` annotations
3. **Resource-Level Legitimation**: Each resource access is verified against AcmeLegi
4. **Fail-Closed Behavior**: Any security check failure results in rejection

## Configuration

### Application Properties

```properties
# Server
server.port=8082

# Security
bfa.security.enabled=true
bfa.security.jwt.issuer=https://auth.acmebank.example
bfa.security.mTLS.required=false

# Legitimation
bfa.legitimation.cache.ttl=300
bfa.legitimation.fail-open=false

# Audit
bfa.audit.enabled=true
bfa.audit.include-request-body=false
bfa.audit.include-response-body=false
```

## Module Structure

```
src/main/java/com/voicebanking/bfa/
├── BfaResourceApplication.java       # Spring Boot main class
├── config/
│   ├── SecurityConfig.java           # Security filter registration
│   ├── InterceptorConfig.java        # Interceptor registration
│   └── OpenApiConfig.java            # Swagger/OpenAPI config
├── controller/
│   ├── AccountController.java        # /api/v1/accounts
│   ├── CardController.java           # /api/v1/cards
│   └── TransferController.java       # /api/v1/transfers
├── dto/
│   ├── AccountDto.java
│   ├── BalanceDto.java
│   ├── TransactionDto.java
│   ├── CardDto.java
│   ├── TransferRequestDto.java
│   └── TransferResponseDto.java
├── filter/
│   └── BfaSecurityFilter.java        # Authentication filter
├── interceptor/
│   ├── ConsentInterceptor.java       # Consent verification
│   ├── LegitimationInterceptor.java  # Resource authorization
│   └── AuditInterceptor.java         # Audit logging
├── annotation/
│   ├── RequiresConsent.java          # Consent annotation
│   └── RequiresLegitimation.java     # Legitimation annotation
├── service/
│   ├── AccountService.java
│   ├── CardService.java
│   ├── TransferService.java
│   ├── ConsentService.java
│   ├── LegitimationService.java
│   └── AuditService.java
├── exception/
│   ├── BfaException.java
│   ├── ConsentRequiredException.java
│   ├── LegitimationDeniedException.java
│   └── GlobalExceptionHandler.java
└── model/
    ├── Account.java
    ├── Card.java
    ├── Transaction.java
    └── Transfer.java
```

## Trade-offs vs Operation-Based Design

### Advantages

1. **Reduced Blast Radius**: Vulnerability in one endpoint doesn't affect others
2. **Standard API Gateway Support**: Route-based rate limits, WAF rules, quotas
3. **Compile-Time Type Safety**: Refactoring catches breaking changes
4. **Built-in Observability**: Metrics per endpoint without custom parsing
5. **Self-Documenting API**: OpenAPI generated from code annotations

### Disadvantages

1. **Agent Context Distribution**: Agent/tool context must flow via headers
2. **Multiple Enforcement Points**: Each interceptor must be correctly configured
3. **More Boilerplate**: Separate DTOs and controllers per resource
4. **Semantic Caching Complexity**: Cache keys based on paths rather than operations

## Related Documentation

- [ADR-BFA-001: Agentic-Aware Design](../docs/architecture/bfa/adr/ADR-BFA-001-agentic-aware-design.md)
- [ADR-BFA-002: Resource-Oriented Alternative](../docs/architecture/bfa/adr/ADR-BFA-002-resource-oriented-rest.md)
- [ADR-J008: Backend For Agents Analysis](../docs/adr/ADR-J008-backend-for-agents-analysis.md)
