# syntax=docker/dockerfile:1
# Imagem do backend (Fase 11a, D-105). Build com JDK 21 e runtime só com JRE 21, sem root.
# Bases fixadas por digest; os argumentos permitem trocar o registro (ex.: um espelho) sem editar o arquivo.
ARG JDK_IMAGE=eclipse-temurin:21-jdk-alpine@sha256:0bfc69a4758a86710e5c474032d28400a8bd00874766f9e8b1642ac2fd293159
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555

FROM ${JDK_IMAGE} AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
    && mv target/platform-*.jar target/app.jar \
    && java -Djarmode=tools -jar target/app.jar extract --layers --destination /extracted

FROM ${JRE_IMAGE}
RUN addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app -s /sbin/nologin app
WORKDIR /app
# Camadas do Spring Boot, da que menos muda para a que mais muda.
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./
# O "hoje" e o job seguem APP_TIMEZONE, não o fuso do container (§20, D-074).
ENV TZ=UTC \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080 8081
# O passo de migração usa a mesma imagem: java -cp app.jar com.resort.platform.MigrateMain (D-057).
ENTRYPOINT ["java", "-jar", "app.jar"]
