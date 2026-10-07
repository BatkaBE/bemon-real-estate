FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/payment-service-*.jar /app/service.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
