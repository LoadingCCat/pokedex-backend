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
RUN ./mvnw -B -q package -DskipTests && mv target/pokedex-backend-*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination /app/ex

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios: el proceso no corre como root.
RUN addgroup -S pokedex && adduser -S -G pokedex pokedex
# El JAR ya viene desplegado en carpetas (app.jar + lib/) desde la etapa 1.
COPY --from=build --chown=pokedex:pokedex /app/ex /app/ex
USER pokedex

# Arranque en frío (Render gratuito = 0,1 CPU): una corrida de entrenamiento guarda las clases ya leídas y
# verificadas en un archivo CDS (app.jsa), que la JVM carga al arrancar en vez de leer y verificar miles de
# clases con casi nada de CPU. La corrida no tiene base de datos: falla al conectar, pero para entonces ya
# cargó Spring, Tomcat y Security, y la JVM guarda lo cargado igualmente (con 0,1 CPU: 102 s -> 76 s;
# docs/despliegue.md). Los valores de relleno son solo de esta corrida: no quedan en la imagen (y el secreto no puede contener las frases de ejemplo que SeguridadConfig rechaza, S-09). Se entrena
# con el mismo JRE que ejecuta; si el archivo no se genera, el build falla en vez de desplegar lento.
RUN ( JWT_SECRETO=relleno-solo-para-la-corrida-del-build-0123456789abcdef       SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:1/x SPRING_DATASOURCE_USERNAME=x SPRING_DATASOURCE_PASSWORD=x       SPRING_JPA_HIBERNATE_DDL_AUTO=none SPRING_SQL_INIT_MODE=never       SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT=-1 SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=1000       java -XX:ArchiveClassesAtExit=/app/ex/app.jsa -Dspring.context.exit=onRefresh -jar /app/ex/app.jar       > /tmp/entrenamiento.log 2>&1 || true )     && test -s /app/ex/app.jsa

# Render pone PORT; Spring lo lee en server.port=${PORT:8080}.
ENV PORT=8080
# - MaxRAMPercentage: el plan gratuito de Render da 512 MB; la JVM usa el 75 % como máximo para el heap.
# - TieredStopAtLevel=1: solo el compilador C1. Con 0,1 CPU el compilador C2 le quita CPU al arranque
#   (180 s -> 102 s); a cambio, menos rendimiento sostenido, que a este tráfico no importa.
# - SharedArchiveFile: el archivo CDS de arriba. Si no coincidiera con la JVM, la JVM lo ignora y arranca igual.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:SharedArchiveFile=/app/ex/app.jsa"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/ex/app.jar"]
