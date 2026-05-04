FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN MAVEN_OPTS="-Xmx384m -XX:MaxRAMPercentage=75.0" mvn clean package -DskipTests -T 1

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S trimlink && adduser -S trimlink -G trimlink \
    && mkdir -p uploads/receipts \
    && chown -R trimlink:trimlink /app
COPY --from=builder /app/target/trimlink-backend-*.jar app.jar
USER trimlink
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
