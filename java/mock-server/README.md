# Demo Account Mock Server

A Spring Boot application with embedded WireMock server for mocking the Demo Account API.

## Overview

This mock server provides a simulated endpoint for the Demo Account personal data API. It uses WireMock to serve predefined responses based on request matching rules.

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Docker (for Cloud Run deployment)
- Google Cloud SDK (for Cloud Run deployment)

## Project Structure

```
java/mock-server/
├── pom.xml
├── README.md
├── Dockerfile
├── deploy.sh
├── run.sh
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/acme/banking/demoaccount/
    │   │       └── DemoAccountApplication.java
    │   └── resources/
    │       ├── application.properties
    │       └── wiremock/
    │           └── mappings/
    │               └── mobile.api/
    │                   └── customers/
    │                       └── #{partnerId}#personal-data/
    │                           ├── max-mustermann.json  # now Hans Müller payload (id: 1234567891)
    │                           └── maria-musterfrau.json (id: 1234567890)
    └── test/
        └── java/
            └── com/acme/banking/demoaccount/
                └── DemoAccountApplicationIntegrationTest.java
```

## How to Run

### Local Development

#### Using Spring Boot Maven Plugin

```bash
cd java/mock-server
mvn spring-boot:run
```

#### Using the run script

```bash
cd java/mock-server
./run.sh
```

#### Building and Running the JAR

```bash
cd java/mock-server

# Build the project
mvn clean package

# Run the JAR
java -jar target/mock-server-1.0.0-SNAPSHOT.jar
```

The server will start on **port 8080** (or whatever is provided by the PORT environment variable in Cloud Run).

### Cloud Run Deployment

Deploy to Google Cloud Run:

```bash
# Ensure you're logged in and have a project set
gcloud auth login
gcloud config set project YOUR_PROJECT_ID

# Optional: protect the upstream with an API key
export MOCK_API_KEY="replace-me"

# Deploy via the shared Cloud Run suite from the repository root
./scripts/cloud/deploy.sh service mock-server YOUR_PROJECT_ID

# Or use the compatibility wrapper from this module
cd java/mock-server
./deploy.sh YOUR_PROJECT_ID
```

The shared deployment suite will:
1. Build the module with Maven from the unified Java reactor
2. Build a Docker image from `java/mock-server/Dockerfile`
3. Push the image to `gcr.io/<PROJECT_ID>/mock-server`
4. Deploy the Cloud Run service with bounded scaling defaults

**Example deployed URL (yours will differ):** `https://mock-server-1041912723804.us-central1.run.app`

## Available Endpoints

For the CES `customer_details` toolset, use the full three-step flow documented
in [docs/idp-token-flow.md](./docs/idp-token-flow.md). If the mock server is
deployed with `MOCK_API_KEY`, the same key must also be configured in
`ces-agent/acme_voice_agent/environment.json` so CES injects `X-API-Key` on all
toolset operations.

### GET /customers/{partnerId}/personal-data

Returns personal data for a customer.

**Required Headers:**
- `Authorization`: `Bearer <authz_token>` from `POST /authz/authorize`
- `DB-ID`: Any non-empty value (for example `acme-banking-db-01`)
- `deuba-client-id`: Must contain `-banking` (e.g., `pb-banking`, `mobile-banking`, `app-banking`)
- `X-API-Key`: Required on all endpoints only when the service is deployed with `MOCK_API_KEY`

**URL Pattern:**
- `/customers/.*/personal-data` (regex pattern, any partnerId is accepted)

**Example Requests:**

Hans Müller (explicit partner id):
```bash
curl -X GET http://localhost:8080/customers/1234567891/personal-data \
  -H "Authorization: Bearer <authz_token>" \
  -H "DB-ID: acme-banking-db-01" \
  -H "deuba-client-id: pb-banking" \
  -H "Accept: application/json"
```

Maria Musterfrau (explicit partner id):
```bash
curl -X GET http://localhost:8080/customers/1234567890/personal-data \
  -H "Authorization: Bearer <authz_token>" \
  -H "DB-ID: acme-banking-db-01" \
  -H "deuba-client-id: pb-banking" \
  -H "Accept: application/json"
```

Default (any other partner id returns the Hans Müller payload):
```bash
curl -X GET http://localhost:8080/customers/any-id/personal-data \
  -H "Authorization: Bearer <authz_token>" \
  -H "DB-ID: acme-banking-db-01" \
  -H "deuba-client-id: pb-banking" \
  -H "Accept: application/json"
```

**Example Response (Hans Müller):**
```json
{
  "firstname": "Hans",
  "lastname": "Müller",
  "academicTitle": "",
  "titleOfNobility": "",
  "fullName": "Hans Müller",
  "id": 1234567891,
  "dateOfBirth": "1996-01-01",
  "placeOfBirth": "",
  "nationality": "DEU",
  "maritalStatus": "married",
  "gender": "MALE",
  "registrationAddress": {
    "id": 1,
    "street": "Kurfürstendamm",
    "streetNumber": "100",
    "postalCode": "10711",
    "city": "Berlin"
  },
  "postalAddress": {
    "id": 1,
    "street": "Kurfürstendamm",
    "streetNumber": "100",
    "postalCode": "10711",
    "city": "Berlin"
  },
  "emailAddress": {
    "id": 0,
    "address": "hans.mueller@random.de",
    "type": "PRIVATE"
  },
  "phoneNumbers": {
    "private": {
      "id": 1,
      "countryCode": "+49",
      "number": "987654321"
    },
    "work": {
      "id": 1,
      "countryCode": "+49",
      "number": "444555666"
    },
    "mobile": {
      "id": 1,
      "countryCode": "+49",
      "number": "111222333"
    }
  }
}
```

**Example Response (Maria Musterfrau):**
```json
{
  "firstname": "Maria",
  "lastname": "Musterfrau",
  "academicTitle": "Dr.",
  "titleOfNobility": "",
  "fullName": "Dr. Maria Musterfrau",
  "id": 1234567890,
  "dateOfBirth": "1988-05-15",
  "placeOfBirth": "Berlin",
  "nationality": "DEU",
  "maritalStatus": "single",
  "gender": "FEMALE",
  "registrationAddress": {
    "id": 2,
    "street": "Friedrichstraße",
    "streetNumber": "45",
    "postalCode": "10117",
    "city": "Berlin"
  },
  "postalAddress": {
    "id": 2,
    "street": "Friedrichstraße",
    "streetNumber": "45",
    "postalCode": "10117",
    "city": "Berlin"
  },
  "emailAddress": {
    "id": 1,
    "address": "maria.musterfrau@mail.com",
    "type": "PRIVATE"
  },
  "phoneNumbers": {
    "private": {
      "id": 2,
      "countryCode": "+49",
      "number": "987654321"
    },
    "work": {
      "id": 2,
      "countryCode": "+49",
      "number": "333444555"
    },
    "mobile": {
      "id": 2,
      "countryCode": "+49",
      "number": "800000600"
    }
  }
}
```

> **Note:** Any partner ID other than `1234567890` will return the default Hans Müller response unless a specific mapping exists for that ID.

## How to Test

### Run All Tests

```bash
cd java/mock-server
mvn test
```

### Run Integration Tests Only

```bash
cd java/mock-server
mvn test -Dtest=DemoAccountApplicationIntegrationTest
```

## Request Matching Rules

The WireMock stub will match requests when:

1. **Method**: GET
2. **URL Pattern**: `/customers/.*/personal-data` (regex)
3. **Header**: `deuba-client-id` must **contain** `-banking`

Valid header examples:
- `pb-banking` ✓
- `mobile-banking` ✓
- `app-banking` ✓
- `invalid-header` ✗ (does not contain `-banking`)

## Configuration

### application.properties

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 8080 | Server port (uses PORT env var in Cloud Run) |
| `wiremock.server.port` | 8080 | WireMock server port |
| `wiremock.root.dir` | (empty) | File system path for WireMock files (used in Docker) |
| `logging.level.com.github.tomakehurst.wiremock` | DEBUG | WireMock logging level |

## Adding New Stubs

To add new mock endpoints:

1. Create a new JSON file under `src/main/resources/wiremock/mappings/`
2. Follow the WireMock stub mapping format
3. Restart the application

Example stub format:
```json
{
  "priority": 4,
  "request": {
    "method": "GET",
    "urlPattern": "/your/endpoint/.*",
    "headers": {
      "your-header": {
        "contains": "value"
      }
    }
  },
  "response": {
    "status": 200,
    "headers": {
      "Content-Type": "application/json"
    },
    "jsonBody": {
      "key": "value"
    }
  }
}
```

## Dependencies

- **Spring Boot 3.4.2**: Application framework
- **WireMock 3.12.1**: Mock server
- **JUnit 5**: Testing framework
- **Mockito**: Mocking framework
- **Rest-Assured 5.4.0**: REST API testing

## Bruno Collection

A [Bruno](https://www.usebruno.com/) API collection is included for easy testing.

### Collection Structure
```
bruno/
├── bruno.json
├── environments/
│   ├── Local.bru
│   └── CloudRun.bru
└── customers/
    ├── Get Hans Mueller Personal Data.bru
    ├── Get Maria Musterfrau Personal Data.bru
    ├── Get Personal Data - Missing Header.bru
    └── Get Personal Data - Invalid Header.bru
```

### How to Use
1. Install [Bruno](https://www.usebruno.com/downloads)
2. Open Bruno and click "Open Collection"
3. Navigate to the `bruno/` folder in this project
4. Select the "Local" or "CloudRun" environment
5. Run requests or the entire collection

### Environment Variables

#### Local Environment
| Variable | Value | Description |
|----------|-------|-------------|
| `baseUrl` | `http://localhost:8080` | Local mock server URL |
| `clientId` | `pb-banking` | Default deuba-client-id header value |

#### CloudRun Environment
| Variable | Value | Description |
|----------|-------|-------------|
| `baseUrl` | `https://mock-server-1041912723804.us-central1.run.app` | Cloud Run URL |
| `clientId` | `pb-banking` | Default deuba-client-id header value |

## License

Internal use only.
