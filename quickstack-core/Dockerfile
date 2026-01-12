# ============================================
# STAGE 1: Build
# ============================================
FROM gradle:8.14.3-jdk17-alpine AS build

WORKDIR /app

# Copiar archivos de configuración de Gradle primero (mejor caching)
COPY build.gradle settings.gradle ./
COPY gradle ./gradle

# Descargar dependencias (se cachea si build.gradle no cambia)
RUN gradle dependencies --no-daemon

# Copiar código fuente
COPY src ./src

# Compilar aplicación (sin tests para build más rápido)
# Los tests ya pasaron en CI, no necesitamos correrlos de nuevo en Docker
RUN gradle bootJar --no-daemon -x test

# ============================================
# STAGE 2: Runtime
# ============================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Crear usuario no-root por seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar JAR desde build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Exponer puerto
EXPOSE 8080

# Variables de entorno con valores por defecto
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Comando de inicio
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
