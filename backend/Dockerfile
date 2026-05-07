# Build stage using Eclipse Temurin JDK 21
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

# Copy Maven project files and source code
COPY pom.xml .
COPY src ./src

# Build the Spring Boot executable jar
RUN mvn -B package -DskipTests

# Runtime stage using Eclipse Temurin JRE 21
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the packaged JAR from the builder stage
COPY --from=builder /workspace/target/*.jar app.jar

# Render uses $PORT; default to 5000 in application.properties
EXPOSE 5000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
