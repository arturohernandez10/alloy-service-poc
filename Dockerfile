FROM eclipse-temurin:17.0.11_9-jre-jammy
WORKDIR /app
COPY target/alloy-service-poc-0.1.0.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-Djava.library.path=/usr/lib:/app"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
