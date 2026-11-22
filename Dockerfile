# Imagen del backend para Render (y para probar en local con Docker).
# Etapa 1: compila con Maven (por el wrapper) y Java 21. Etapa 2: solo el JRE 21 y el JAR.

# ---------- Etapa 1: compilar ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# El wrapper primero: mientras pom.xml no cambie, las dependencias quedan en caché de Docker.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# Las pruebas corren en el CI (mvn verify); aquí solo se empaqueta.
RUN ./mvnw -B -q package -DskipTests && mv target/pokedex-backend-*.jar app.jar

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios: el proceso no corre como root.
RUN addgroup -S pokedex && adduser -S -G pokedex pokedex
COPY --from=build --chown=pokedex:pokedex /app/app.jar app.jar
USER pokedex

# Render pone PORT; Spring lo lee en server.port=${PORT:8080}.
ENV PORT=8080
# El plan gratuito de Render da 512 MB: la JVM usa el 75 % como máximo para el heap.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
