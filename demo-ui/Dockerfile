# Build do reator inteiro: o modulo depende do pom pai e do payments-api.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY payments-api/pom.xml payments-api/
COPY orders-api/pom.xml orders-api/
COPY demo-ui/pom.xml demo-ui/
# Baixa as dependencias numa camada separada, para que mudar codigo nao
# invalide o cache do download.
RUN mvn -B -q dependency:go-offline -DskipTests

COPY payments-api/src payments-api/src
COPY orders-api/src orders-api/src
COPY demo-ui/src demo-ui/src
RUN mvn -B -q -pl demo-ui -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/demo-ui/target/demo-ui-*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
