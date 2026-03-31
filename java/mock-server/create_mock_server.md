Here is a **clean, structured, production-ready prompt** you can use with **GitHub Copilot / Copilot Chat / Copilot Workspace** to generate your Java WireMock mock server based on what appears in your screenshots.

You can copy–paste this as-is.

---

## ✅ **Copilot Prompt — Java WireMock Mock Server Generator**

> You are a senior Java backend engineer specializing in test environments and API mocking.
>
> I need you to generate a **Java-based WireMock mock server** that reproduces the following system.
>
> ---
>
> ## 1. Project Type
>
> * Maven project
> * Packaging: `jar`
> * Java: 17
> * WireMock version: `3.12.1`
> * JUnit 5
> * Mockito
> * Rest-Assured (test scope)
>
> ---
>
> ## 2. Project Structure
>
> Use this base package:
>
> ```
> com.db.olbfe.demoaccount
> ```
>
> Main class:
>
> ```
> DemoAccountApplication
> ```
>
> ---
>
> Resources layout:
>
> ```
> src/main/resources/wiremock/mappings/
>   mobile.api/
>     customers/
>       #{partnerId}#personal-data/
>         success.json
> ```
>
> ---
>
> ## 3. Server Behavior
>
> Implement a standalone WireMock server that:
>
> * Starts on port **8080**
> * Loads mappings from `classpath:/wiremock`
> * Runs via Maven `exec-maven-plugin`
> * Can be started using:
>
> ```
> mvn exec:java
> ```
>
> ---
>
> ## 4. Endpoint to Mock
>
> Implement this endpoint:
>
> ```
> GET /customers/{partnerId}/personal-data
> ```
>
> Example:
>
> ```
> /customers/6585363429/personal-data
> ```
>
> ---
>
> ## 5. Request Matching Rules
>
> The request must match:
>
> * Method: GET
> * URL pattern: `/customers/.*/personal-data`
> * Required Header:
>
> ```
> deuba-client-id
> ```
>
> Header value must contain:
>
> ```
> -banking
> ```
>
> Example:
>
> ```
> pb-banking
> mobile-banking
> app-banking
> ```
>
> Use WireMock `contains` matcher.
>
> ---
>
> ## 6. Stub Mapping (success.json)
>
> Generate a WireMock mapping file with:
>
> ### Priority
>
> ```
> priority: 4
> ```
>
> ### Request
>
> ```
> method: GET
> urlPattern: /customers/.*/personal-data
> header: deuba-client-id contains "-banking"
> ```
>
> ### Response
>
> ```
> status: 200
> Content-Type: application/json
> ```
>
> Body:
>
> ```json
> {
>   "firstname": "Max",
>   "lastname": "Mustermann",
>   "academicTitle": "",
>   "titleOfNobility": "",
>   "fullName": "Max Mustermann",
>   "id": 6585363429,
>   "dateOfBirth": "1996-01-01",
>   "placeOfBirth": "",
>   "nationality": "DEU",
>   "maritalStatus": "married",
>   "gender": "MALE",
>   "registrationAddress": {
>     "id": 1,
>     "street": "Taunusanlage",
>     "streetNumber": "12",
>     "postalCode": "60325",
>     "city": "Frankfurt am Main"
>   },
>   "postalAddress": {
>     "id": 1,
>     "street": "Taunusanlage",
>     "streetNumber": "12",
>     "postalCode": "60325",
>     "city": "Frankfurt am Main"
>   },
>   "emailAddress": {
>     "id": 0,
>     "address": "max.mustermann@mail.com",
>     "type": "PRIVATE"
>   },
>   "phoneNumbers": {
>     "private": {
>       "id": 1,
>       "countryCode": "+49",
>       "number": "123456789"
>     },
>     "work": {
>       "id": 1,
>       "countryCode": "+49",
>       "number": "555666777"
>     },
>     "mobile": {
>       "id": 1,
>       "countryCode": "+49",
>       "number": "900000700"
>     }
>   }
> }
> ```
>
> ---
>
> ## 7. Application Configuration
>
> Provide:
>
> * `application.properties`
> * Port configuration
> * WireMock bootstrap
> * Logging enabled
>
> ---
>
> ## 8. Maven Configuration
>
> Generate a complete `pom.xml` with:
>
> * wiremock-standalone
> * junit-jupiter
> * mockito-junit-jupiter
> * rest-assured
> * exec-maven-plugin
>
> Configure `exec-maven-plugin` to run:
>
> ```
> com.db.olbfe.demoaccount.DemoAccountApplication
> ```
>
> ---
>
> ## 9. Testing
>
> Create at least one integration test that:
>
> * Starts the server
> * Calls:
>
> ```
> GET /customers/6585363429/personal-data
> ```
>
> * Sends header:
>
> ```
> deuba-client-id: pb-banking
> ```
>
> * Asserts:
>
>   * HTTP 200
>   * JSON contains `firstname = Max`
>   * `nationality = DEU`
>
> ---
>
> ## 10. Deliverables
>
> Generate:
>
> * Full project structure
> * Java classes
> * WireMock mappings
> * pom.xml
> * README.md
> * Example curl command
>
> ---
>
> ## 11. README Content
>
> Include:
>
> * How to run
> * How to test
> * Example curl
>
> Example:
>
> ```bash
> curl -X GET http://localhost:8080/customers/6585363429/personal-data \
>  -H "deuba-client-id: pb-banking" \
>  -H "Accept: application/json"
> ```
>
> ---
>
> Generate everything following Java best practices, clean architecture, and production-grade structure.
>
> Do NOT omit any required file.

