# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-alpine@sha256:69c2a26e9894016e274c880fa542956d005b3755f84db765145185e87405bd89

RUN apk upgrade --no-cache \
    && apk add --no-cache tzdata postgresql16-client \
    && apk del --no-cache sqlite sqlite-libs gnupg gpg gnupg-keyboxd \
    && addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /build/target/servisdefteri-1.0.0.jar /app/app.jar
RUN mkdir -p /app/backups && chown -R app:app /app

USER app
EXPOSE 18080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD /bin/busybox wget -qO- http://127.0.0.1:18080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=Europe/Istanbul", "-jar", "/app/app.jar"]
