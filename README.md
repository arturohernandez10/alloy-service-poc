# Alloy Service POC

Note: This README is work-in-progress. Sections marked TODO are not finalized and may change as implementation evolves. For the current, authoritative contract and behavior, refer to `docs/PROJECT.md`, `docs/GPT5-grammar-feature.md`, and the DTO/runner sources under `src/main/java/app/**`.

* A minimal project (Maven + Java 17, Javalin HTTP).
* Code to parse & run Alloy, enumerate solutions, and extract **unsat cores**.
* Dockerfile and quick deploy notes (Azure Container Apps/App Service).
* TODO: JSON request/response examples will be added once the contract is finalized.

> **Why this will work**
> The service relies on Alloy’s public Java API (`CompUtil`, `TranslateAlloyToKodkod`, `A4Solution`) which exposes both **solution enumeration** and **unsat-core** accessors (`highLevelCore()`, `lowLevelCore()`). ([alloytools.org][1])
> We load Alloy from Maven Central (Alloy 6.2.x artifacts), and include the **`org.alloytools.alloy.dist`** runtime so JNI-based solvers (needed for unsat cores) are available inside Linux containers. ([Maven Repository][2])
> Alloy 6 also ships a CLI/LSP, but the API path below is the simplest way to integrate with GPT tool calls. ([GitHub][3], [alloytools.org][4])

---

## 1) Project layout

```text
alloy-service-poc/
├─ pom.xml
├─ src/main/java/
│  ├─ app/App.java
│  ├─ app/AlloyRunner.java
│  ├─ app/dto/SolveRequest.java
│  └─ app/dto/SolveResponse.java
├─ Dockerfile
└─ README.md
```

### `pom.xml` (Java 17)

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>dev.poc</groupId>
  <artifactId>alloy-service-poc</artifactId>
  <version>0.1.0</version>
  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <javalin.version>6.1.6</javalin.version>
    <jackson.version>2.17.2</jackson.version>
    <alloy.version>6.2.0</alloy.version>
  </properties>

  <dependencies>
    <!-- HTTP server -->
    <dependency>
      <groupId>io.javalin</groupId>
      <artifactId>javalin</artifactId>
      <version>${javalin.version}</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>${jackson.version}</version>
    </dependency>

    <!-- Alloy API (core + app) -->
    <dependency>
      <groupId>org.alloytools</groupId>
      <artifactId>org.alloytools.alloy.core</artifactId>
      <version>${alloy.version}</version>
    </dependency>
    <dependency>
      <groupId>org.alloytools</groupId>
      <artifactId>org.alloytools.alloy.application</artifactId>
      <version>${alloy.version}</version>
    </dependency>

    <!-- Alloy distribution (includes native solvers). Keep as runtime scope. -->
    <dependency>
      <groupId>org.alloytools</groupId>
      <artifactId>org.alloytools.alloy.dist</artifactId>
      <version>${alloy.version}</version>
      <scope>runtime</scope>
    </dependency>
    <!-- SLF4J binding to silence warnings in container -->
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>2.0.13</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <!-- Pack everything into a single runnable JAR -->
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.2</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <createDependencyReducedPom>false</createDependencyReducedPom>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>app.App</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

> `org.alloytools.alloy.core` / `.application` provide the parser/translator APIs; `org.alloytools.alloy.dist` brings the bundled native solvers (e.g., **MiniSatProverJNI**) you’ll need for unsat core extraction inside a Linux container. ([Maven Repository][2], [Maven Central][5])

---

## 2) The service

TODO: This section will be updated to reflect the current DTOs and envelope semantics once the contract is frozen. For now, please consult the source files directly for ground truth:

* `src/main/java/app/App.java`
* `src/main/java/app/AlloyRunner.java`
* `src/main/java/app/dto/SolveRequest.java`
* `src/main/java/app/dto/SolveResponse.java`

---

## 3) JSON shapes — GPT tool contract

TODO: The JSON contract is evolving. Do not rely on example schemas here. Refer to `docs/PROJECT.md` and `docs/AI_FUNCTION_CALL_CONTRACT_NOTES.md` for the latest request/response requirements, and inspect the DTOs in `src/main/java/app/dto/**` for the current shape.

---

## 3.5) API (OpenAPI 3.1)

See the OpenAPI spec at `docs/openapi.yaml` (validated by IDE schema header). Examples in the spec are covered by unit tests under `src/test/java/app/OpenApiExamplesTest.java`.

Quick curl (SAT run example):

```bash
curl -s http://localhost:8080/v1/solve \
  -H 'Content-Type: application/json' \
  -d @- << 'JSON'
{
  "spec": "sig A {}\npred show {}\nrun show for 3\n",
  "command": "run",
  "target": "show",
  "scope": { "max_instances": 1 },
  "options": { "solver": "SAT4J", "timeout_ms": 2000 }
}
JSON
```

Lint the spec locally (optional):

```bash
npx --yes @stoplight/spectral-cli lint docs/openapi.yaml
```

---

## 4) Run it

### Local

```bash
# Build (Windows)
build_windows.bat

# Or build (any OS)
mvn -q -DskipTests package

# Run
java -jar target/alloy-service-poc-0.1.0.jar
# Service listens on :8080 (override with PORT)
```

### Try it (examples)

The service normalizes instances as structured JSON with per-signature atoms and per-relation tuples. Example (first instance):

```json
{
  "instances": [
    {
      "atoms": {
        "A": ["A$0", "A$1"]
      },
      "relations": {
        "A.r": [["A$0", "A$1"]]
      }
    }
  ]
}
```

Notes:
- Atom identifiers are stable within a response but abstract (not raw analyzer internals).
- Built-in signatures like `univ`, `Int`, `seq/Int`, `String`, `iden`, `none` are omitted.

For working end-to-end samples today, see the unit tests under `src/test/java/app/**` and models in `src/test/resources/models/**`.

---

## 5) Docker & Azure

### Dockerfile

```dockerfile
FROM eclipse-temurin:17.0.11_9-jre-jammy
WORKDIR /app
COPY target/alloy-service-poc-0.1.0.jar /app/app.jar
ENV JAVA_TOOL_OPTIONS="-Djava.library.path=/usr/lib:/app"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Build & run:

```bash
mvn -q -DskipTests package
docker build -t alloy-poc:latest .
docker run --rm -p 8080:8080 alloy-poc:latest
```

### Self-check

Verify native solver availability (unsat core path) locally or in CI:

```bash
# via CLI arg
java -jar target/alloy-service-poc-0.1.0.jar --self-check

# or inside Docker
docker run --rm alloy-poc:latest --self-check

# or with env var
docker run --rm -e SELF_CHECK=1 alloy-poc:latest
```
The command exits 0 on success and non-zero if MiniSatProverJNI is unavailable or unsat-core cannot be retrieved.

**Azure Container Apps** (example):

```bash
# assuming you pushed ghcr.io/you/alloy-poc:latest and have ACA env created
az containerapp create \
  --name alloy-poc \
  --resource-group YOUR_RG \
  --environment YOUR_ACA_ENV \
  --image ghcr.io/you/alloy-poc:latest \
  --target-port 8080 --ingress external \
  --min-replicas 0 --max-replicas 2
```

(Any Azure hosting that runs a Linux container will do; the Maven `dist` dependency ensures native solver libs are present for unsat-core extraction.) ([Maven Repository][11])

---

## 6) What this POC covers (and what it skips)

### Included now

* Execute a selected **run/check** command from a model string.
* Enumerate N solutions; serialize sigs/fields (compact string form for brevity here).
* Return **unsat cores** with file/line/column ranges and text snippets. ([alloytools.org][8])
* Request/response shapes suitable for GPT tool calls.
* Containerization for remote JVM execution (works on Azure).

### Deliberately skipped for POC (easy follow-ups)

* Resolving `open` commands from a model that spans files. (Switch to `parseEverything_fromFile` and provide a model root with read-only volumes.) ([alloytools.org][1])
* Rich instance serialization (e.g., JSON arrays of atoms/tuples instead of `.toString()`).
* Streaming progress / timeouts / per-request solver caps.
* AuthN/Z & rate limits.
* Running temporal backends (nuXmv/electrod). (Alloy 6 embeds an LSP/CLI, but that’s not required here.) ([GitHub][3])

---

## 7) Quick API reference (for your team)

TODO: Parameter names and semantics may change as implementation lands (e.g., `symmetry_breaking` vs `symmetry`, envelope `status` vs `satisfiable`). Verify against `docs/PROJECT.md` and DTOs before integrating.

* **Choose solver**
  Prefer `"MiniSatProverJNI"` to get unsat cores. If the runtime can’t load the native library, Alloy silently falls back to other choices; we expose the final solver id in `debug.solverId`. ([alloytools.org][9])

* **Unsat core knobs**
  `coreMinimization` (0..2) and `coreGranularity` (0..3) control core size/precision and how much formula flattening/quantifier expansion is attempted. See Alloy docs for the trade-offs. ([alloytools.org][12])

* **Enumerating solutions**
  Call `next()` until the returned solution is UNSAT (we cap with `maxInstances`). ([The Alloy Language and Tools][7])

---

### Why not use the CLI or LSP?

Alloy 6 ships a CLI (`java -jar alloy.jar help`) and embeds an LSP server. Those are great for editors/dev, but for GPT tool-calls the **in-process API** keeps JSON I/O simple and gives you direct access to `A4Solution` (instances & cores) without scraping CLI output. ([GitHub][3])

---

[1]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/parser/CompUtil.html?utm_source=chatgpt.com "CompUtil - Alloy"
[2]: https://mvnrepository.com/artifact/org.alloytools/org.alloytools.alloy.core?utm_source=chatgpt.com "org.alloytools.alloy.core - Maven Repository"
[3]: https://github.com/AlloyTools/org.alloytools.alloy/releases?utm_source=chatgpt.com "Releases · AlloyTools/org.alloytools.alloy - GitHub"
[4]: https://alloytools.org/download.html "alloytools.org"
[5]: https://central.sonatype.com/artifact/org.alloytools/org.alloytools.alloy.application?utm_source=chatgpt.com "org.alloytools:org.alloytools.alloy.application - Maven Central"
[6]: https://alloytools.org/documentation/code/ExampleUsingTheCompiler.java?utm_source=chatgpt.com "download - Alloy Analyzer"
[7]: https://alloytools.discourse.group/t/how-can-i-run-alloy-in-command-line/275?utm_source=chatgpt.com "How can I run Alloy in command line? - Questions"
[8]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/translator/A4Solution.html 
[9]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/translator/A4Options.SatSolver.html 
[10]: https://alloytools.org/documentation/alloy-api/index-files/index-23.html?utm_source=chatgpt.com "X-Index"
[11]: https://mvnrepository.com/artifact/org.alloytools "Maven Repository: org.alloytools"
[12]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/translator/A4Options.html?utm_source=chatgpt.com "A4Options"
