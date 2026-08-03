FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY gradlew gradlew.bat build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --console=plain

COPY src ./src
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre

RUN useradd --system --create-home --uid 10001 weezzy

WORKDIR /app
COPY --from=builder --chown=weezzy:weezzy /workspace/build/libs/*.jar app.jar

USER weezzy
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=production

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
