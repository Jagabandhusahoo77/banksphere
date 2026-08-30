# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21.0.12_8-jre-alpine-3.24
# CVE-2026-14456 (OpenSSL DoS via unbounded memory growth in QUIC server)
# — fixed in libssl3/libcrypto3/openssl 3.5.8-r0, which Alpine's 3.24
# package repo has published but no eclipse-temurin:*-jre-alpine-3.24
# tag has been rebuilt with yet (same situation as the frontend images
# — no base-image upgrade path exists for this one right now). A
# targeted upgrade of exactly these three packages, not a blanket
# `apk upgrade`, pulls the fix from the SAME Alpine 3.24 branch already
# in use. Revisit/remove once a published tag ships this baked in.
RUN apk add --no-cache --upgrade libssl3 libcrypto3 openssl
WORKDIR /app

RUN addgroup -S banksphere && adduser -S banksphere -G banksphere
COPY --from=build /build/target/account-service.jar app.jar
RUN chown banksphere:banksphere app.jar
USER banksphere

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
