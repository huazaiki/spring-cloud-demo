# sc-auth-service 多阶段构建
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -q -pl sc-auth-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/sc-auth-service/target/sc-auth-service-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-jar","app.jar"]