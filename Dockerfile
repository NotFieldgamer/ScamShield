# syntax=docker/dockerfile:1
#
# Deployment build for the Scam Shield API (Render, Docker environment).
# Built from the repository root with the repo root as the build context. Only the backend is
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
# JRE (not JDK) for a smaller final image. Jammy (glibc), not Alpine (musl): the ONNX Runtime and
# DJL native libraries are built against glibc and fail to load on musl.
FROM eclipse-temurin:21-jre-jammy AS runtime

# curl fetches the embedding model at build time; ca-certificates for HTTPS.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Run as a non-root user and own /app up front, so the model download and app jar are written as
# that user (no costly recursive chown).
RUN useradd -m -u 1000 app
WORKDIR /app
RUN mkdir -p /app/models && chown -R app:app /app
USER app

# ---- MiniLM embedding model (~90MB): NOT committed. Fetched here at build time. ----
# Host-agnostic: provide EMBEDDING_MODEL_URL to the image build (e.g. a Render build-time env var
# surfaced as a build arg). If the URL is absent (e.g. a CI image-build check), the download is
# skipped and the app reports the missing model at startup rather than failing the build.
ARG EMBEDDING_MODEL_URL=""
ARG EMBEDDING_MODEL_PATH=/app/models/minilm.onnx
ENV EMBEDDING_MODEL_PATH=${EMBEDDING_MODEL_PATH}
RUN if [ -n "$EMBEDDING_MODEL_URL" ]; then \
      echo "Fetching MiniLM model into $EMBEDDING_MODEL_PATH" && \
      curl -fSL "$EMBEDDING_MODEL_URL" -o "$EMBEDDING_MODEL_PATH" ; \
    else \
      echo "EMBEDDING_MODEL_URL not set — skipping model download. Set it as a build-time variable so the embedding service can start." ; \
    fi

# Dependencies (rarely change) first for layer reuse; the thin app jar (changes often) last.
COPY --from=build --chown=app:app /workspace/build/extracted/lib/ ./lib/
COPY --from=build --chown=app:app /workspace/build/extracted/app.jar ./app.jar

# The app binds the port Render injects as $PORT (server.port=${PORT:8080}); default 8080 locally.
# EXPOSE is documentation only — Render detects the bound port at runtime.
EXPOSE 8080

# JVM memory tuning for Render's 512MB / 0.1-CPU free tier. The heap is only part of the budget:
# ONNX Runtime loads the ~90MB MiniLM model into NATIVE (off-heap) memory, which sits on top of the
# heap. So the heap cap is deliberately low to leave room for it — heap + ONNX native + metaspace
# must all fit in 512MB. (At 70% the heap alone reached ~358MB and the container OOM'd the moment
# the embedding model loaded.)
#   MaxRAMPercentage=35     → heap ≤ ~180MB, leaving headroom for the model's native allocations
#   UseSerialGC             → single-threaded GC; parallel/G1 collectors thrash on 0.1 CPU
#   Xss512k                 → smaller thread stacks to fit more threads in the tight footprint
#   ExitOnOutOfMemoryError  → fail fast so Render restarts the instance instead of thrashing
ENV JAVA_OPTS="-XX:MaxRAMPercentage=35 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
