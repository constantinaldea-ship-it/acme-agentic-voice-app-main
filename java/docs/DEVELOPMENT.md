# Voice Banking Assistant - Development Guide

## Prerequisites

- **Java:** 21 LTS (OpenJDK 21.0.9 or later)
- **Maven:** 3.9.12 or later
- **IDE:** IntelliJ IDEA, VS Code, or Eclipse with Java support
- **Git:** For version control
- **(Optional) Google Cloud:** For cloud profile testing

---

## Setup

### 1. Install Dependencies

#### macOS:
```bash
brew install openjdk@21
brew install maven
```

#### Linux:
```bash
sudo apt-get install openjdk-21-jdk maven
```

#### Windows:
Download from [AdoptOpenJDK](https://adoptopenjdk.net/) and [Apache Maven](https://maven.apache.org/).

### 2. Clone Repository

```bash
git clone https://github.com/your-org/voice-banking-assistant.git
cd voice-banking-assistant/java
```

### 3. Verify Installation

```bash
java -version    # Should show 21.x.x
mvn -version     # Should show 3.9.x
```

---

## Build

### Full Build

```bash
cd java
mvn clean install
```

**Output:**
- Compiles all source code
- Runs 68 unit/integration tests
- Generates JaCoCo coverage report (target/site/jacoco/)
- Packages JAR (voice-banking-app/target/voice-banking-app-0.1.0-SNAPSHOT.jar)

### Quick Compile (Skip Tests)

```bash
mvn clean compile -DskipTests
```

---

## Running the Application

### Local Profile (Stub Adapters)

```bash
cd java
mvn spring-boot:run -pl voice-banking-app -Dspring.profiles.active=local
```

**Activated Beans:**
- `SttStubAdapter` - Deterministic STT simulation
- `LlmStubAdapter` - Pattern-based intent detection

**Access:**
- API: http://localhost:8080/api
- Health: http://localhost:8080/api/health

### Cloud Profile (Real GCP Services)

**1. Set up credentials:**
```bash
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/credentials.json
export GOOGLE_CLOUD_PROJECT=your-project-id
```

**2. Run with cloud profile:**
```bash
cd java
mvn spring-boot:run -pl voice-banking-app -Dspring.profiles.active=cloud
```

**Activated Beans:**
- `SttChirp2Adapter` - Google Cloud Speech-to-Text (Chirp 2)
- `LlmGeminiAdapter` - Vertex AI Gemini (1.5 Flash)

---

## Testing

### Run All Tests

```bash
cd java
mvn test
```

**Expected Output:**
```
Tests run: 68, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Run Specific Test Class

```bash
mvn test -Dtest=OrchestratorServiceTest
mvn test -Dtest=AdkSessionManagerTest
```

### Run Tests with Coverage

```bash
mvn verify
```

**Coverage Report:** `voice-banking-app/target/site/jacoco/index.html`

**Requirements:**
- Services: ≥70% line coverage
- Controllers: ≥60% line coverage
- Overall: ≥70% line coverage

### Integration Tests

```bash
mvn verify -P integration-tests
```

**Note:** Integration tests require Docker (Testcontainers) for PostgreSQL (Phase 3).

---

## Development Workflow

### 1. Create Feature Branch

```bash
git checkout -b feature/your-feature-name
```

### 2. Write Tests First (TDD)

```bash
# Create test file
touch voice-banking-app/src/test/java/com/voicebanking/YourFeatureTest.java

# Run tests (should fail initially)
mvn test -Dtest=YourFeatureTest
```

### 3. Implement Feature

```bash
# Create implementation file
touch voice-banking-app/src/main/java/com/voicebanking/YourFeature.java

# Run tests again (should pass)
mvn test -Dtest=YourFeatureTest
```

### 4. Verify Coverage

```bash
mvn verify
open voice-banking-app/target/site/jacoco/index.html
```

Ensure coverage meets requirements (≥70%).

### 5. Run Full Build

```bash
mvn clean install
```

### 6. Commit Changes

```bash
git add .
git commit -m "feat: add YourFeature with tests and ≥70% coverage"
```

### 7. Create Pull Request

Push branch and open PR on GitHub/GitLab.

---

## Project Structure

```
java/
├── pom.xml                           # Parent POM
├── docs/                             # Documentation
│   ├── ARCHITECTURE.md              # Architecture overview
│   ├── API.md                       # API documentation
│   ├── DEVELOPMENT.md               # This file
│   ├── JAVA-MIGRATION-MASTER-PLAN.md
│   └── adr/                         # Architecture Decision Records
│       ├── ADR-J001-validation-framework.md
│       ├── ADR-J002-testing-framework.md
│       ├── ADR-J003-memory-module-deprecation.md
│       └── ADR-J004-google-adk-selection.md
└── voice-banking-app/
    ├── pom.xml                      # Module POM
    └── src/
        ├── main/
        │   ├── java/com/voicebanking/
        │   │   ├── VoiceBankingApplication.java
        │   │   ├── config/          # Spring configuration
        │   │   ├── controller/      # REST controllers
        │   │   ├── service/         # Business logic
        │   │   ├── adapter/         # STT/LLM adapters
        │   │   │   ├── stt/         # Speech-to-Text adapters
        │   │   │   └── llm/         # LLM adapters
        │   │   ├── session/         # Session management (Phase 2)
        │   │   ├── domain/          # Domain models (records)
        │   │   └── util/            # Utilities
        │   └── resources/
        │       ├── application.yml          # Base config
        │       ├── application-local.yml    # Local profile
        │       └── application-cloud.yml    # Cloud profile
        └── test/
            └── java/com/voicebanking/
                ├── adapter/         # Adapter tests
                ├── controller/      # Controller tests
                ├── service/         # Service tests
                └── session/         # Session tests
```

---

## Debugging

### Enable Debug Logging

Add to `application-local.yml`:
```yaml
logging:
  level:
    com.voicebanking: DEBUG
```

### Run in Debug Mode (IDE)

**IntelliJ IDEA:**
1. Right-click `VoiceBankingApplication.java`
2. Select "Debug 'VoiceBankingApplication'"
3. Set breakpoints in source code

**VS Code:**
1. Install "Extension Pack for Java"
2. Add `.vscode/launch.json`:
   ```json
   {
     "type": "java",
     "name": "Debug Voice Banking",
     "request": "launch",
     "mainClass": "com.voicebanking.VoiceBankingApplication",
     "projectName": "voice-banking-app"
   }
   ```
3. Press F5 to start debugging

### Remote Debugging

```bash
# Start application with remote debug
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Attach debugger to localhost:5005
```

---

## Code Style

### Formatting

The project uses standard Java conventions:
- **Indentation:** 4 spaces
- **Line width:** 120 characters
- **Braces:** Opening brace on same line

**IntelliJ IDEA:** Import `java/.editorconfig` (if present)

### Linting

```bash
# Run Checkstyle (if configured)
mvn checkstyle:check

# Run SpotBugs (if configured)
mvn spotbugs:check
```

---

## Common Tasks

### Add New Dependency

Edit `voice-banking-app/pom.xml`:
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>example-library</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then:
```bash
mvn clean compile
```

### Create New Adapter

1. **Create interface:**
   ```java
   // src/main/java/com/voicebanking/adapter/foo/FooProvider.java
   public interface FooProvider {
       FooResponse doSomething(String input);
   }
   ```

2. **Create stub implementation:**
   ```java
   // src/main/java/com/voicebanking/adapter/foo/FooStubAdapter.java
   @Component
   @Profile("local")
   public class FooStubAdapter implements FooProvider {
       @Override
       public FooResponse doSomething(String input) {
           // Stub logic
       }
   }
   ```

3. **Create cloud implementation:**
   ```java
   // src/main/java/com/voicebanking/adapter/foo/FooCloudAdapter.java
   @Component
   @Profile("cloud")
   public class FooCloudAdapter implements FooProvider {
       @Override
       public FooResponse doSomething(String input) {
           // Real cloud integration
       }
   }
   ```

4. **Write tests:**
   ```java
   // src/test/java/com/voicebanking/adapter/foo/FooStubAdapterTest.java
   @SpringBootTest
   @ActiveProfiles("local")
   class FooStubAdapterTest {
       @Autowired
       private FooProvider fooProvider;
       
       @Test
       void shouldDoSomething() {
           FooResponse response = fooProvider.doSomething("test");
           assertThat(response).isNotNull();
       }
   }
   ```

---

## Troubleshooting

### Build Fails with "Java version mismatch"

**Problem:** Maven uses Java 11 instead of Java 21

**Solution:**
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -version  # Verify Java 21 is active
```

### Tests Fail with "Could not initialize class"

**Problem:** Spring Boot context fails to load

**Solution:**
1. Check `application-local.yml` for typos
2. Verify `@SpringBootTest` annotation on test class
3. Run with `-X` for detailed logs: `mvn test -X`

### Application Starts but Returns 404

**Problem:** Controller mapping incorrect

**Solution:**
1. Verify `@RestController` and `@RequestMapping("/api")` on controller
2. Check logs for "Mapped \"{[/api/...]}\"" messages
3. Test with curl: `curl -v http://localhost:8080/api/health`

### Cloud Profile Fails with "Google Cloud credentials not found"

**Problem:** `GOOGLE_APPLICATION_CREDENTIALS` not set

**Solution:**
```bash
export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/credentials.json
echo $GOOGLE_APPLICATION_CREDENTIALS  # Verify it's set
mvn spring-boot:run -Dspring.profiles.active=cloud
```

### Coverage Report Shows <70%

**Problem:** Tests don't cover enough code

**Solution:**
1. Run `mvn verify` to generate report
2. Open `target/site/jacoco/index.html`
3. Identify uncovered lines (red)
4. Add tests for those code paths
5. Re-run `mvn verify` and verify coverage increased

---

## Performance Tips

### Faster Test Execution

```bash
# Run tests in parallel (4 threads)
mvn test -T 4

# Skip slow integration tests
mvn test -DskipITs
```

### Faster Builds

```bash
# Skip tests during development iteration
mvn clean compile -DskipTests

# Use incremental compilation (IDE)
# IntelliJ IDEA: Build > Build Project (Ctrl+F9)
```

### Faster Startup

Add to `application-local.yml`:
```yaml
spring:
  devtools:
    restart:
      enabled: true
  main:
    lazy-initialization: true
```

---

## References

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/3.3.0/reference/html/)
- [Maven Documentation](https://maven.apache.org/guides/)
- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Google Cloud Java SDKs](https://cloud.google.com/java/docs)

---

## Getting Help

- **Questions:** Open GitHub issue with `question` label
- **Bugs:** Open GitHub issue with `bug` label and reproduction steps
- **Feature Requests:** Open GitHub issue with `enhancement` label
- **Slack:** #voice-banking-dev (if available)

---

**Last Updated:** 2026-01-16  
**Version:** 0.1.0-SNAPSHOT
