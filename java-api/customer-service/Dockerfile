FROM maven:3.8.3-openjdk-17 AS build


COPY . /home/app/java
RUN mvn -Dmaven.test.skip=true -f /home/app/java/pom.xml clean package spring-boot:repackage


FROM styli/openjdk17-alpine:clean


COPY --from=build /home/app/java/target/customer-service-1.0.jar /usr/local/lib/customer-service-1.0.jar
EXPOSE 8080
ENTRYPOINT ["java",  "-XX:MinRAMPercentage=50", "-XX:MaxRAMPercentage=60", "-jar", "/usr/local/lib/customer-service-1.0.jar"]
