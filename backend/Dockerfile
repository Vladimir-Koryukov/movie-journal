# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /build
ENV MAVEN_OPTS="-Xmx512m"

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
# Also support source archives created on Windows without a Git checkout.
RUN sed -i 's/\r$//' mvnw
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -ntp clean verify

FROM eclipse-temurin:25-jre-noble AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --user-group --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /build/target/movie-journal-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
