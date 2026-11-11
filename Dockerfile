# Etapa 1: Compilar la aplicacion Java con Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn clean package -DskipTests

# Etapa 2: Imagen ligera de ejecucion
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY uploads uploads
EXPOSE 8080
ENV PORT=8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]

