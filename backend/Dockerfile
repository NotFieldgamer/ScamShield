# syntax=docker/dockerfile:1

# ---- Stage 1: build the Spring Boot jar ----
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Wrapper + build config first, so dependency resolution caches across source changes.
COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon --version

# Build the boot jar. Tests need a Docker daemon (Testcontainers) and run in CI, not here.
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# Extract into a runnable layout: a thin launcher jar plus an externalized lib/ directory,
# so third-party dependencies land in their own cache-friendly image layer.
RUN java -Djarmode=tools -jar build/libs/*.jar extract --destination build/extracted \
 && mv build/extracted/*.jar build/extracted/app.jar

# ---- Stage 2: lean JRE runtime ----
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system app && useradd --system --no-create-home --gid app app

# Dependencies (rarely change) first for layer reuse; the thin app jar (changes often) last.
COPY --from=build --chown=app:app /workspace/build/extracted/lib/ ./lib/
COPY --from=build --chown=app:app /workspace/build/extracted/app.jar ./app.jar

USER app
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
