# Build stage: Gradle 8.9 (the Lombok plugin used here is not compatible with Gradle 9).
FROM gradle:8.9-jdk21 AS build
WORKDIR /home/gradle/src
COPY --chown=gradle:gradle settings.gradle.kts build.gradle.kts ./
COPY --chown=gradle:gradle packages ./packages
COPY --chown=gradle:gradle apps/backend ./apps/backend
RUN gradle :apps:backend:bootJar --no-daemon -x test

# Run stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 careerpilot
COPY --from=build /home/gradle/src/apps/backend/build/libs/backend.jar /app/backend.jar
USER careerpilot
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3 && grep -q "UP" <&3'
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/backend.jar"]
