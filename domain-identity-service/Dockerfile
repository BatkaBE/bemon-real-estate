FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/identity-service-*.jar /app/service.jar
RUN mkdir -p /data && chown 10001:10001 /data
USER 10001:10001
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
