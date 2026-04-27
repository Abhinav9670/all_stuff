# FROM maven:3.9.0-openjdk-17-slim AS build
#
#
# COPY . /home/app/java
# RUN mvn -Dmaven.test.skip=true -f /home/app/java/pom.xml clean package spring-boot:repackage
#
# FROM openjdk:17-alpine
#
# COPY --from=build /home/app/java/target/order-service-1.0.jar /usr/local/lib/order-service-1.0.jar
#
# EXPOSE 8080
# ENTRYPOINT ["java", "-Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2", "-XX:MinRAMPercentage=50", "-XX:MaxRAMPercentage=60", "-jar", "/usr/local/lib/order-service-1.0.jar"]

FROM maven:3.8.3-openjdk-17 AS build
WORKDIR /home/app/java
COPY . .
RUN mvn -Dmaven.test.skip=true clean package spring-boot:repackage
FROM styli/openjdk17-alpine:clean
COPY --from=build /home/app/java/target/order-service-1.0.jar /usr/local/lib/order-service-1.0.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2", "-XX:MinRAMPercentage=50", "-XX:MaxRAMPercentage=60", "-jar", "/usr/local/lib/order-service-1.0.jar"]
