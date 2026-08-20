# Build do reator inteiro: o modulo depende do pom pai.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY payments-api/pom.xml payments-api/
COPY orders-api/pom.xml orders-api/
# Baixa as dependencias numa camada separada, para que mudar codigo nao
# invalide o cache do download.
RUN mvn -B -q dependency:go-offline -DskipTests

COPY payments-api/src payments-api/src
COPY orders-api/src orders-api/src
RUN mvn -B -q -pl orders-api -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/orders-api/target/orders-api-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
