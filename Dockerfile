# Multi-stage build - Optimized
FROM maven:3.9-eclipse-temurin-11-alpine AS build
WORKDIR /app

# Copiar settings.xml si existe (opcional)
COPY settings.xml* /root/.m2/
# Copiar proyecto
COPY pom.xml .

# Compilar aplicación directamente
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

# Imagen final - Alpine para menor tamaño
FROM eclipse-temurin:11-jre-alpine

# Instalar wget para healthcheck (más ligero que curl)
RUN apk add --no-cache wget

# Variables de entorno optimizadas para containers
ENV SPRING_PROFILES_ACTIVE=dev \
    JAVA_OPTS="-Xmx256m -Xms128m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:MaxRAM=512m -XX:+UseContainerSupport" \
    SERVER_PORT=8400

# Usuario no-root (sintaxis Alpine)
RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser

# Directorio de aplicación
WORKDIR /home/app
RUN chown appuser:appuser /home/app

USER appuser

# Copiar JAR
COPY --from=build --chown=appuser:appuser /app/target/*.jar payment-service.jar

# Exponer puertos
EXPOSE ${SERVER_PORT}

# Health check con wget
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${SERVER_PORT}/actuator/health || exit 1

# Punto de entrada
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE -Dserver.port=$SERVER_PORT -jar payment-service.jar"]