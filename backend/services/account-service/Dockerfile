# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S banksphere && adduser -S banksphere -G banksphere
COPY --from=build /build/target/account-service.jar app.jar
RUN chown banksphere:banksphere app.jar
USER banksphere

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
