
FROM openjdk:17-jdk-slim

WORKDIR /app
ARG JAR_FILE=target/PruebaTecnica-1.0.0.jar

COPY ${JAR_FILE} PruebaTecnica.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "PruebaTecnica.jar"]