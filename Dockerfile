FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace

COPY pom.xml .
COPY docker/maven-settings.xml /root/.m2/settings.xml
RUN mvn -s /root/.m2/settings.xml -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -s /root/.m2/settings.xml -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN apk add --no-cache curl \
	&& addgroup -S matchforge \
	&& adduser -S matchforge -G matchforge
USER matchforge

COPY --from=build /workspace/target/matchforge-*.jar /app/app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
	CMD curl -fsS http://127.0.0.1:${PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]