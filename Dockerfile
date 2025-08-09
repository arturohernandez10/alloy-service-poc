FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/alloy-service-poc-0.1.0-shaded.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-Djava.library.path=/usr/lib:/app"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
