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

# Install Python, Docker CLI, and common tools
RUN apt-get update && apt-get install -y \
    python3 python3-pip python3-venv jq curl \
    ca-certificates gnupg lsb-release && \
    mkdir -p /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu jammy stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    apt-get update && apt-get install -y docker-ce-cli && \
    rm -rf /var/lib/apt/lists/*

# Copy the JAR from builder
COPY --from=builder /app/gateway/build/libs/gateway-1.0.0.jar app.jar
COPY --from=builder /app/gateway/build/resources/main/static static/

# Copy the universal orchestrator, registry, and deployment manifest
COPY acc.py .
COPY docker-compose.yml .
COPY brain/ brain/
COPY config/ config/
COPY requirements.txt .
RUN pip3 install --no-cache-dir -r requirements.txt huggingface-hub && \
    ln -s /usr/local/bin/huggingface-cli /usr/local/bin/hf

# Environment Setup
ENV PROJECT_ROOT=/app
ENV PORT=8333
ENV IN_DOCKER=true

EXPOSE 8333

ENTRYPOINT ["java", "-jar", "app.jar"]
