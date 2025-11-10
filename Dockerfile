FROM eclipse-temurin:17-jre

ARG PROJECT_VERSION=0.1.0

# Crear directorio y configurar permisos
RUN mkdir -p /home/app && \
    addgroup --system --gid 1001 appgroup && \
    adduser --system --uid 1001 --gid 1001 appuser && \
    chown -R appuser:appgroup /home/app

WORKDIR /home/app
USER appuser

# Variables de entorno optimizadas para contenedor
ENV SPRING_PROFILES_ACTIVE=dev \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Copiar JAR como última capa para mejor caché
COPY --chown=appuser:appgroup target/payment-service-v${PROJECT_VERSION}.jar payment-service.jar

EXPOSE 8300

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar payment-service.jar"]