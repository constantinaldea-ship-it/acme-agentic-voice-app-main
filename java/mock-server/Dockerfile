FROM eclipse-temurin:17-jre

WORKDIR /app

RUN useradd --system --uid 1001 --create-home appuser

# Copy the Spring Boot JAR
COPY target/*.jar app.jar

# Copy WireMock mappings and files to a location WireMock can access
COPY src/main/resources/wiremock /app/wiremock

RUN chown -R appuser:appuser /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar", "--wiremock.root.dir=/app/wiremock"]
