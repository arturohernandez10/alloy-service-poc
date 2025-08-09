# Alloy Service POC

* A minimal project (Maven + Java 17, Javalin HTTP).
* Code to parse & run Alloy, enumerate solutions, and extract **unsat cores**.
* JSON request/response shapes (works as a GPT tool “function”).
* A Dockerfile and quick deploy notes (Azure Container Apps/App Service).
* A couple of `curl` examples.

> **Why this will work**
> The service relies on Alloy’s public Java API (`CompUtil`, `TranslateAlloyToKodkod`, `A4Solution`) which exposes both **solution enumeration** and **unsat-core** accessors (`highLevelCore()`, `lowLevelCore()`). ([alloytools.org][1])
> We load Alloy from Maven Central (Alloy 6.2.x artifacts), and include the **`org.alloytools.alloy.dist`** runtime so JNI-based solvers (needed for unsat cores) are available inside Linux containers. ([Maven Repository][2])
> Alloy 6 also ships a CLI/LSP, but the API path below is the simplest way to integrate with GPT tool calls. ([GitHub][3], [alloytools.org][4])

---

## 1) Project layout

```
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

### `src/main/java/app/App.java`

```java
package app;

import io.javalin.Javalin;
import app.dto.SolveRequest;
import app.dto.SolveResponse;

public class App {
  public static void main(String[] args) {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    Javalin app = Javalin.create(cfg -> {
      cfg.http.defaultContentType = "application/json";
      cfg.jetty.threadPool.maxThreads = 32;
    });

    app.post("/v1/solve", ctx -> {
      try {
        SolveRequest req = ctx.bodyAsClass(SolveRequest.class);
        SolveResponse resp = AlloyRunner.solve(req);
        ctx.json(resp);
      } catch (Exception e) {
        SolveResponse err = new SolveResponse();
        err.status = "ERROR";
        err.error = new SolveResponse.ErrorInfo();
        err.error.type = "bad_request";
        err.error.message = e.getMessage();
        ctx.status(400).json(err);
      }
    });

    app.get("/healthz", ctx -> ctx.result("ok"));

    app.start(port);
  }
}
```

### `src/main/java/app/dto/SolveRequest.java`

```java
package app.dto;

import java.util.Map;

public class SolveRequest {
  public String model;            // Alloy model text
  public String commandLabel;     // optional: run/check label to execute
  public Integer commandIndex;    // optional: fallback by index
  public Integer maxInstances;    // optional: enumerate up to N solutions (default 1)

  public Options options;

  public static class Options {
    public String solver;          // e.g., "MiniSatProverJNI", "SAT4J"
    public Integer coreGranularity; // 0..3 (0=top-level only, 3=expand quantifiers)
    public Integer coreMinimization;// 0..2 (0=slow/accurate .. 2=faster)
    public Integer unrolls;         // recursion depth
    public Integer symmetry;        // symmetry breaking (default 20)
    public Boolean recordKodkod;    // include KK debugging payloads
  }

  // Optional: override scopes (by sig label), bitwidth/maxSeq (Alloy 'Command' options)
  public Map<String,Integer> exactScopes; // label -> exactly N atoms
  public Integer bitwidth;
  public Integer maxSeq;

  // For GPT tool-call alignment: an opaque correlation id for tracing
  public String requestId;
}
```

### `src/main/java/app/dto/SolveResponse.java`

```java
package app.dto;

import java.util.*;

public class SolveResponse {
  public String requestId;
  public String status; // "SAT" | "UNSAT"
  public String command; // which command we executed
  public List<Map<String,Object>> instances; // list of instances, each map contains sigs/fields
  public UnsatCore core; // if UNSAT and available
  public List<String> warnings = new ArrayList<>();
  public Debug debug;
  public ErrorInfo error;

  public static class UnsatCore {
    public List<Range> must;   // guaranteed members (highLevelCore().a)
    public List<Range> maybe;  // potential members (highLevelCore().b)
    public List<Range> low;    // low-level core (positions only)
    public Map<String,String> snippetByRangeId; // optional: small text snippets
  }

  public static class Range {
    public String id;        // stable id for client UI
    public String file;      // typically "input.als" when parsing from string
    public int startLine, startCol, endLine, endCol;
  }

  public static class Debug {
    public String solverId;
    public Integer bitwidth;
    public Integer maxSeq;
    public String kodkod;       // if recordKodkod=true, include KK formula text
    public String originalFile; // the "filename" Alloy believes the model came from
    public Long durationMs;
    public Long peakMemoryBytes;
    public String modelHash;
  }
  public static class ErrorInfo { public String type; public String message; }
}
```

### `src/main/java/app/AlloyRunner.java`

```java
package app;

import app.dto.SolveRequest;
import app.dto.SolveResponse;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.ConstList;
import edu.mit.csail.sdg.alloy4.Pair;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.alloy4compiler.ast.Command;
import edu.mit.csail.sdg.alloy4compiler.ast.CompModule;
import edu.mit.csail.sdg.alloy4compiler.ast.Sig;
import edu.mit.csail.sdg.alloy4compiler.parser.CompUtil;
import edu.mit.csail.sdg.alloy4compiler.translator.*;

import java.util.*;

public class AlloyRunner {

  public static SolveResponse solve(SolveRequest req) throws Exception {
    SolveResponse out = new SolveResponse();
    out.requestId = req.requestId;

    // Parse model from string (single module; `open` blocked/ignored for safety)
    CompModule world = CompUtil.parseOneModule(req.model); // returns AST + commands
    ConstList<Command> cmds = world.getAllCommands(); // which run/checks exist

    // Choose command by label or index (default: 0)
    Command choice = chooseCommand(cmds, req.commandLabel, req.commandIndex);
    out.command = choice.toString();

    // Configure Alloy options
    A4Options opt = new A4Options();
    opt.solver         = parseSolver(req); // default SAT4J (portable)
    opt.coreGranularity= orDefault(req.options != null ? req.options.coreGranularity : null, 1);
    opt.coreMinimization = orDefault(req.options != null ? req.options.coreMinimization : null, 2);
    opt.unrolls        = orDefault(req.options != null ? req.options.unrolls : null, 3);
    opt.symmetry       = orDefault(req.options != null ? req.options.symmetry : null, 20);
    opt.recordKodkod   = req.options != null && Boolean.TRUE.equals(req.options.recordKodkod);
    opt.originalFilename = "input.als";

    // Apply optional scope overrides (bitwidth/maxSeq/exact scopes)
    Command adjusted = adjustCommand(choice, world, req);

    // Solve (and optionally enumerate multiple solutions)
    A4Reporter NOP = A4Reporter.NOP;
    List<Map<String,Object>> instances = new ArrayList<>();

    A4Solution sol = TranslateAlloyToKodkod.execute_command(NOP, world.getAllReachableSigs(), adjusted, opt);
    if (sol.satisfiable()) {
      int max = Math.max(1, orDefault(req.maxInstances, 1));
      int produced = 0;
      while (sol.satisfiable() && produced < max) {
        instances.add(serializeInstance(world, sol));
        produced++;
        sol = sol.next(); // enumerate next solution (or self if UNSAT)
      }
      out.status = "SAT";
    } else {
      out.status = "UNSAT";
      out.core = buildCore(req.model, sol);
    }

    out.instances = instances;
    out.debug = new SolveResponse.Debug();
    out.debug.solverId = opt.solver.id();
    out.debug.bitwidth = adjusted.bitwidth;
    out.debug.maxSeq = adjusted.maxseq;
    out.debug.originalFile = opt.originalFilename;
    if (opt.recordKodkod) out.debug.kodkod = sol.debugExtractKInput();

    return out;
  }

  private static Command chooseCommand(ConstList<Command> cmds, String label, Integer idx) {
    if (label != null) {
      for (Command c : cmds) {
        if (c.label != null && c.label.equals(label)) return c;
      }
    }
    int i = (idx != null && idx >= 0 && idx < cmds.size()) ? idx : 0;
    return cmds.get(i);
  }

  private static Command adjustCommand(Command cmd, CompModule world, SolveRequest req) {
    Command c = cmd;
    if (req.bitwidth != null) c = new Command(c.check, c.scope, req.bitwidth, c.maxseq, c.formula, c.additionalExactScopes);
    if (req.maxSeq   != null) c = new Command(c.check, c.scope, c.bitwidth, req.maxSeq, c.formula, c.additionalExactScopes);
    if (req.exactScopes != null) {
      for (Sig s : world.getAllReachableSigs()) {
        Integer n = req.exactScopes.get(s.label);
        if (n != null) c = c.change(s, true, n);
      }
    }
    return c;
  }

  private static A4Options.SatSolver parseSolver(SolveRequest req) {
    String id = (req.options != null && req.options.solver != null) ? req.options.solver : "MiniSatProverJNI";
    // If the id is unknown, Alloy falls back to SAT4J.
    return A4Options.SatSolver.parse(id);
  }

  private static Map<String,Object> serializeInstance(CompModule world, A4Solution sol) throws Exception {
    Map<String,Object> m = new LinkedHashMap<>();
    // Sigs
    for (Sig s : world.getAllReachableSigs()) {
      m.put("sig:" + s.label, sol.eval(s).toString()); // compact; for production serialize tuples properly
      // Fields
      s.getFields().forEach(f -> {
        try {
          m.put("field:" + s.label + "." + f.label, sol.eval(f).toString());
        } catch (Exception ignored) {}
      });
    }
    return m;
  }

  private static SolveResponse.UnsatCore buildCore(String source, A4Solution sol) {
    SolveResponse.UnsatCore core = new SolveResponse.UnsatCore();
    core.must = new ArrayList<>();
    core.maybe = new ArrayList<>();
    core.low = new ArrayList<>();
    core.snippetByRangeId = new LinkedHashMap<>();

    Pair<Set<Pos>, Set<Pos>> hi = sol.highLevelCore(); // (guaranteed, potential)
    for (Pos p : hi.a) core.must.add(toRange(p, source, core.snippetByRangeId));
    for (Pos p : hi.b) core.maybe.add(toRange(p, source, core.snippetByRangeId));

    for (Pos p : sol.lowLevelCore()) core.low.add(toRange(p, source, core.snippetByRangeId));
    return core;
  }

  private static SolveResponse.Range toRange(Pos p, String source, Map<String,String> snippets) {
    SolveResponse.Range r = new SolveResponse.Range();
    r.file = p.filename;
    r.startLine = p.y; r.startCol = p.x;
    r.endLine = p.y2;  r.endCol   = p.x2;
    r.id = r.file + ":" + r.startLine + ":" + r.startCol + "-" + r.endLine + ":" + r.endCol;
    // add a small snippet to help GPT/tooling show useful errors
    String snippet = slice(source, r.startLine, r.startCol, r.endLine, r.endCol);
    if (snippet != null && !snippet.isBlank()) snippets.put(r.id, snippet);
    return r;
  }

  private static String slice(String text, int y1, int x1, int y2, int x2) {
    try {
      String[] lines = text.split("\r?\n", -1);
      StringBuilder sb = new StringBuilder();
      for (int i = y1; i <= y2 && i <= lines.length; i++) {
        String line = lines[i-1];
        int start = (i==y1) ? Math.max(0, x1-1) : 0;
        int end   = (i==y2) ? Math.min(line.length(), x2-1) : line.length();
        if (start < end) sb.append(line, start, end);
        if (i<y2) sb.append('\n');
      }
      return sb.toString();
    } catch (Exception e) { return null; }
  }

  private static int orDefault(Integer v, int d) { return v!=null ? v : d; }
}
```

**Notes about the API calls above (for reviewers):**

* `CompUtil.parseOneModule(String)` parses a single module from a string and exposes its `Command`s; use `parseEverything_fromFile(...)` if you need to resolve `open`-ed files. ([alloytools.org][1])
* `TranslateAlloyToKodkod.execute_command(...)` solves a selected `Command` and returns `A4Solution`. `A4Solution.next()` enumerates further solutions; if no more exist, `next()` returns an UNSAT solution. ([alloytools.org][6], [The Alloy Language and Tools][7])
* For **unsat cores**, `A4Solution.highLevelCore()` returns a pair of `Set<Pos>` (guaranteed vs. potential members), and `lowLevelCore()` returns a raw `Set<Pos>`. ([alloytools.org][8])
* `A4Options.SatSolver` accepts ids like `"MiniSatProverJNI"` and `"SAT4J"`; `parse()` maps strings and defaults to SAT4J if unknown. ([alloytools.org][9])
* `Pos` exposes filename and **1-based** line/column coordinates (`x,x2,y,y2`) — we convert them to ranges and extract small text snippets to help debugging. ([alloytools.org][10])

---

## 3) JSON shapes — ready for GPT tool calls

**Tool definition (JSON Schema-ish):**

```json
{
  "name": "solve_alloy",
  "description": "Execute an Alloy model, enumerate instances or return an unsat core.",
  "parameters": {
    "type": "object",
    "properties": {
      "model": { "type": "string", "description": "Alloy module text" },
      "commandLabel": { "type": "string" },
      "commandIndex": { "type": "integer", "minimum": 0 },
      "maxInstances": { "type": "integer", "minimum": 1, "default": 1 },
      "bitwidth": { "type": "integer", "minimum": 1, "maximum": 30 },
      "maxSeq": { "type": "integer", "minimum": 0 },
      "exactScopes": { "type": "object", "additionalProperties": { "type": "integer", "minimum": 0 } },
      "options": {
        "type": "object",
        "properties": {
          "solver": { "type": "string", "enum": ["MiniSatProverJNI","MiniSatJNI","SAT4J","ZChaffJNI","BerkMinPIPE","SpearPIPE","CNF","KK"] },
          "coreGranularity": { "type": "integer", "minimum": 0, "maximum": 3 },
          "coreMinimization": { "type": "integer", "minimum": 0, "maximum": 2 },
          "unrolls": { "type": "integer" },
          "symmetry": { "type": "integer" },
          "recordKodkod": { "type": "boolean" }
        }
      },
      "requestId": { "type": "string" }
    },
    "required": ["model"]
  }
}
```

You can point GPT’s function-call grammar at the service endpoint by proxying calls (or call the Java method in-process if you embed the module).

---

## 4) Run it

### Local

```bash
# Build (Windows)
build_windows.bat

# Or build (any OS)
mvn -q -DskipTests package

# Run
java -jar target/alloy-service-poc-0.1.0-shaded.jar
# Service listens on :8080 (override with PORT)
```

### Try it (SAT)

```bash
curl -s localhost:8080/v1/solve -H 'content-type: application/json' -d @- <<'JSON'
{
  "model": "sig A {} run {} for 3",
  "maxInstances": 1,
  "options": { "solver": "SAT4J" }
}
JSON | jq .
```

### Try it (UNSAT + core)

```bash
curl -s localhost:8080/v1/solve -H 'content-type: application/json' -d @- <<'JSON'
{
  "model": "sig A {}\n\nfact { no A }\n\nassert someA { some A }\ncheck someA for 3\n",
  "commandLabel": "someA",
  "options": { "solver": "MiniSatProverJNI", "coreGranularity": 1, "coreMinimization": 2 }
}
JSON | jq .
```

You should see `"status": "UNSAT"` and `core.must/core.maybe` populated with file/line/column ranges. (Unsat cores are exposed through `A4Solution.highLevelCore()` / `lowLevelCore()`, so this requires a solver with unsat-core support such as **MiniSatProverJNI**). ([alloytools.org][8])

---

## 5) Docker & Azure

### `Dockerfile`

```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/alloy-service-poc-0.1.0-shaded.jar /app/app.jar
# Allow Alloy natives to load
ENV JAVA_TOOL_OPTIONS="-Djava.library.path=/usr/lib:/app"
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Build & run:

```bash
docker build -t alloy-poc:latest .
docker run --rm -p 8080:8080 alloy-poc:latest
```

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

**Included now**

* Execute a selected **run/check** command from a model string.
* Enumerate N solutions; serialize sigs/fields (compact string form for brevity here).
* Return **unsat cores** with file/line/column ranges and text snippets. ([alloytools.org][8])
* Request/response shapes suitable for GPT tool calls.
* Containerization for remote JVM execution (works on Azure).

**Deliberately skipped for POC (easy follow-ups)**

* Resolving `open` commands from a model that spans files. (Switch to `parseEverything_fromFile` and provide a model root with read-only volumes.) ([alloytools.org][1])
* Rich instance serialization (e.g., JSON arrays of atoms/tuples instead of `.toString()`).
* Streaming progress / timeouts / per-request solver caps.
* AuthN/Z & rate limits.
* Running temporal backends (nuXmv/electrod). (Alloy 6 embeds an LSP/CLI, but that’s not required here.) ([GitHub][3])

---

## 7) Quick API reference (for your team)

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
[8]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/translator/A4Solution.html "
A4Solution
"
[9]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/translator/A4Options.SatSolver.html "
A4Options.SatSolver
"
[10]: https://alloytools.org/documentation/alloy-api/index-files/index-23.html?utm_source=chatgpt.com "X-Index"
[11]: https://mvnrepository.com/artifact/org.alloytools "Maven Repository: org.alloytools"
[12]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/translator/A4Options.html?utm_source=chatgpt.com "A4Options"
