FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY services/order-validation/pom.xml ./
COPY services/order-validation/src ./src
RUN mvn --batch-mode --no-transfer-progress package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/order-validation-service-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
