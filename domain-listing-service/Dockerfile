FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/listing-service-*.jar /app/service.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
