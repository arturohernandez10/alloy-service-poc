FROM eclipse-temurin:17.0.11_9-jre-jammy

# Ensure C++ runtime and gcc runtime are present for native JNI solvers (MiniSatProverJNI)
RUN apt-get update \
    && apt-get install -y --no-install-recommends libstdc++6 libgcc-s1 unzip \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY target/alloy-service-poc-0.1.0.jar /app/app.jar

# Extract Alloy native solver libraries from the fat JAR so the JVM can load them
# Try a few patterns to catch architecture folder names; ignore if some are missing
RUN mkdir -p /usr/local/lib \
    && (unzip -j /app/app.jar '*minisat*prover*.so' -d /usr/local/lib || true) \
    && (unzip -j /app/app.jar '*/libminisatprover*.so' -d /usr/local/lib || true) \
    && (unzip -j /app/app.jar '*/libminisat*.so' -d /usr/local/lib || true) \
    && (unzip -j /app/app.jar '*/libglucose*.so' -d /usr/local/lib || true)
ENV JAVA_TOOL_OPTIONS="-Djava.library.path=/usr/lib:/usr/local/lib:/app"
ENV LD_LIBRARY_PATH="/usr/lib:/usr/local/lib:/app"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
