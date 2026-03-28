# CI-friendly Dockerfile — JAR is pre-built by Maven in the GitHub Actions workflow.
# This image only packages the pre-built artifact; no JDK or Maven needed at image build time.
# CI build command: mvn package -DskipTests -pl inventory-service -am
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Non-root user — never run production containers as root
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

# JVM tuning: cap heap at 75% of container memory limit; faster SecureRandom for Spring
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

COPY target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
