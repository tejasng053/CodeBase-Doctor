FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/codebase-doctor-0.1.0.jar /app/backend.jar
RUN mkdir -p /app/data && chown 10001:10001 /app/data
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
