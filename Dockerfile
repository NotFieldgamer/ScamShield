# syntax=docker/dockerfile:1
#
# Hugging Face Spaces (Docker SDK) build for the Scam Shield API.
# Spaces builds the Dockerfile at the repository root with the repo root as the build context,
# and routes external traffic to app_port (7860, declared in README.md). Only the backend is
# built into this image; the frontend deploys separately to Vercel.

# ---- Stage 1: build the Spring Boot jar ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Wrapper + build config first, so dependency resolution caches across source changes.
COPY backend/gradlew backend/settings.gradle backend/build.gradle backend/gradle.properties ./
COPY backend/gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version

# Build the boot jar. Tests need a Docker daemon (Testcontainers) and run in CI, not here.
COPY backend/src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# Extract into a runnable layout: a thin launcher jar plus an externalized lib/ directory,
# so third-party dependencies land in their own cache-friendly image layer.
RUN java -Djarmode=tools -jar build/libs/*.jar extract --destination build/extracted \
 && mv build/extracted/*.jar build/extracted/app.jar

# ---- Stage 2: lean JRE runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl fetches the embedding model at build time; ca-certificates for HTTPS.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Spaces runs the container as UID 1000. Create a matching non-root user and own /app up front,
# so the model download and app jar are written as that user (no costly recursive chown).
RUN useradd -m -u 1000 app
WORKDIR /app
RUN mkdir -p /app/models && chown -R app:app /app
USER app

# ---- MiniLM embedding model (~90MB): NOT committed. Fetched here at build time. ----
# Provide EMBEDDING_MODEL_URL as a Space Variable (Settings -> Variables and secrets); Spaces
# passes Variables as Docker build args. If the URL is absent (e.g. a CI image-build check), the
# download is skipped and the app reports the missing model at startup rather than failing the build.
ARG EMBEDDING_MODEL_URL=""
ARG EMBEDDING_MODEL_PATH=/app/models/minilm.onnx
ENV EMBEDDING_MODEL_PATH=${EMBEDDING_MODEL_PATH}
RUN if [ -n "$EMBEDDING_MODEL_URL" ]; then \
      echo "Fetching MiniLM model into $EMBEDDING_MODEL_PATH" && \
      curl -fSL "$EMBEDDING_MODEL_URL" -o "$EMBEDDING_MODEL_PATH" ; \
    else \
      echo "EMBEDDING_MODEL_URL not set — skipping model download. Set it in the Space Variables so the embedding service can start." ; \
    fi

# Dependencies (rarely change) first for layer reuse; the thin app jar (changes often) last.
COPY --from=build --chown=app:app /workspace/build/extracted/lib/ ./lib/
COPY --from=build --chown=app:app /workspace/build/extracted/app.jar ./app.jar

# Hugging Face Spaces routes external traffic to this port (see app_port in README.md).
ENV SERVER_PORT=7860
EXPOSE 7860
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
