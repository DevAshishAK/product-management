# syntax=docker/dockerfile:1.7
# Multi-stage build. Build context = repository root:  docker build -f infrastructure/monitoring-service/Dockerfile .

# ---- Stage 1: build (Maven + JDK 21), cached dependency layer ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common ./common
COPY infrastructure ./infrastructure
COPY services ./services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -q -pl infrastructure/monitoring-service -am -DskipTests -Djacoco.skip=true package \
 && java -Djarmode=tools -jar infrastructure/monitoring-service/target/monitoring-service-*.jar extract --layers --launcher --destination /workspace/extracted

# ---- Stage 2: minimal runtime (JRE only, layered jar, non-root) ----
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -h /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 9090
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
