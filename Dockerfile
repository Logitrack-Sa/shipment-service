# --- Etapa 1: Build ---
FROM maven:3-eclipse-temurin-25 AS build
WORKDIR /app

# Copiar todo el proyecto directamente para asegurar que Maven resuelva el árbol completo de dependencias de una vez
COPY pom.xml ./
COPY src/ src/

# Ejecutar el empaquetado completo directamente (descarga todo y compila sin requerir múltiples conexiones divididas)
RUN mvn clean package -DskipTests -B

# --- Etapa 2: Runtime ---
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
