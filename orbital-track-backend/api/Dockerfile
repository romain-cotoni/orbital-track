# Stage 1: Build
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY common/pom.xml common/pom.xml
COPY batch/pom.xml batch/pom.xml
COPY api/pom.xml api/pom.xml

# Download dependencies first (layer cache)
RUN ./mvnw dependency:go-offline -pl common,api -am -q

COPY common/ common/
COPY api/ api/

RUN ./mvnw clean package -pl common,api -am -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /build/common/src/main/resources/orekit-data /app/orekit-data
COPY --from=builder /build/api/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
