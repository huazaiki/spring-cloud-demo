# sc-purchase-service 多阶段构建
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn -q -pl sc-purchase-service -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/sc-purchase-service/target/sc-purchase-service-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java","-jar","app.jar"]