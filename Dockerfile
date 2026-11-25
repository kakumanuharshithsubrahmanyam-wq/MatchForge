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

ENV JAVA_OPTS="-XX:MaxRAMPercentage=50.0 -XX:InitialRAMPercentage=20.0 -XX:+UseContainerSupport -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError"
EXPOSE 10000

HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=5 \
	CMD curl -fsS "http://127.0.0.1:${PORT:-10000}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]