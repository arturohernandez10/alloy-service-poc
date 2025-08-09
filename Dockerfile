FROM eclipse-temurin:17.0.11_9-jre-jammy

# Native solver deps for MiniSatProverJNI
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgmp10 libstdc++6 libgcc-s1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY target/alloy-service-poc-0.1.0.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
