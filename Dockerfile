# Modified by Codex on 2026-02-07
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

COPY java java

RUN mvn -f java/pom.xml -pl bfa-service-resource -am clean package -DskipTests

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /build/java/bfa-service-resource/target/bfa-service-resource-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=70.0", "-jar", "/app/app.jar"]
