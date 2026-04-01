# Hello Kitty Microservice Blueprint

This is a self-contained learning resource for developers to understand end-to-end Cloud Run deployment patterns using Java and Spring Boot.

## Purpose
The `hellokitty` project serves as a minimal, standalone blueprint for creating and deploying Java microservices to Google Cloud Run. It demonstrates:
- Standalone Maven project structure
- Spring Boot 3.x and Java 21 integration
- Containerization with Docker
- Automated deployment to Cloud Run

## API Endpoints

### 1. Hello API
`GET /api/v1/hello?name={name}`

**Behaviors:**
- Missing `name` parameter: Returns `{"message": ""}`
- `name=hi`: Returns `{"message": "hello kitty"}`
- Any other `name`: Returns `{"message": "hello {name}"}`

### 2. Health Check
`GET /actuator/health`

## Prerequisites
- **Java 21**: JDK installed and configured
- **Maven**: To build the application
- **Docker**: For containerizing the service
- **Google Cloud CLI (gcloud)**: To interact with GCP

## Quick Start

### Build and Run Locally
```bash
# 1. Build the Maven project
mvn clean package

# 2. Run the application
java -jar target/hellokitty-1.0.0.jar
```
The service will be available at [http://localhost:8080/api/v1/hello](http://localhost:8080/api/v1/hello).

### Run with Docker locally
```bash
# 1. Build Docker image
docker build -t hellokitty .

# 2. Run container
docker run -p 8080:8080 hellokitty
```

### Deploy to Cloud Run
```bash
# Use the self-contained deployment script
./deploy.sh [YOUR_PROJECT_ID] [YOUR_REGION]
```

## Testing the Deployed Service
```bash
# Get the Service URL
SERVICE_URL=$(gcloud run services describe hellokitty --platform managed --format 'value(status.url)')

# Test with various parameters
curl "${SERVICE_URL}/api/v1/hello"
curl "${SERVICE_URL}/api/v1/hello?name=hi"
curl "${SERVICE_URL}/api/v1/hello?name=world"
```

## Cleanup
To delete the deployed service and avoid ongoing costs:
```bash
gcloud run services delete hellokitty --region [REGION] --project [PROJECT_ID]
```
