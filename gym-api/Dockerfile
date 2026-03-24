# gym-api/Dockerfile

# --- Build stage: compiles the Spring Boot app with Maven and JDK 21
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Use -DskipTests to speed up container builds; keep tests on CI
RUN mvn -q -DskipTests package

# --- Runtime stage: lean JRE image for production
FROM eclipse-temurin:21-jre
WORKDIR /app
# JAVA_OPTS lets you inject memory/tuning flags from the orchestrator
ENV JAVA_OPTS=""
# Copy the fat jar built in the previous stage
COPY --from=build /app/target/*-SNAPSHOT.jar app.jar
EXPOSE 8080
# Enable the "docker" Spring profile by default
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-docker}"]
