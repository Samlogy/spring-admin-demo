# Monolith image. Build context: repository root (needs .git for git.properties -> /actuator/info).
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY .git .git
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/monolith-*.jar app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app \
    && mkdir -p /var/log/app \
    && chown app:app /var/log/app
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER 10001:10001
# 8080: application, 9090: Actuator (qua/prod, management.server.port)
EXPOSE 8080 9090
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
