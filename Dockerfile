# -------- Stage 1: Build --------
FROM gradle:8.10.1-jdk21 AS builder
WORKDIR /app

# Cache dependencies
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN gradle build -x test || return 0

# Copy source and build
COPY . .
RUN gradle shadowJar

# -------- Stage 2: Runtime --------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy fat jar
COPY --from=builder /app/build/libs/*-all.jar app.jar

# Run
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
