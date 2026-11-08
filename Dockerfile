# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine

RUN apk upgrade --no-cache \
    && apk add --no-cache tzdata wget postgresql16-client \
    && addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/servisdefteri-1.0.0.jar /app/app.jar
RUN mkdir -p /app/backups && chown -R app:app /app

USER app
EXPOSE 18080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:18080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=Europe/Istanbul", "-jar", "/app/app.jar"]
