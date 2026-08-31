# ==============================================================================
# acc GATEWAY - MULTI-STAGE DOCKERFILE
# ==============================================================================

# Stage 1: Build the Wasm Frontend
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :frontend:web:wasmJsBrowserDistribution --no-daemon

# Stage 2: Build the Gateway JAR
RUN ./gradlew :gateway:assemble --no-daemon

# Stage 3: Runtime Environment
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install Python and bridge dependencies
RUN apt-get update && apt-get install -y python3 python3-pip python3-venv jq curl && \
    rm -rf /var/lib/apt/lists/*

# Copy the JAR from builder
COPY --from=builder /app/gateway/build/libs/gateway-1.0.0.jar app.jar
COPY --from=builder /app/gateway/build/resources/main/static static/

# Copy bridge and scripts
COPY brain/ brain/
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt

# Environment Setup
ENV PROJECT_ROOT=/app
ENV PORT=8333

EXPOSE 8333

ENTRYPOINT ["java", "-jar", "app.jar"]
