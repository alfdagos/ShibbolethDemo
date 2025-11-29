FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM registry.access.redhat.com/ubi9/openjdk-17
COPY --from=build /app/target/demo-backend-0.0.1-SNAPSHOT.jar /deployments/app.jar
EXPOSE 8080
CMD ["java", "-jar", "/deployments/app.jar"]
