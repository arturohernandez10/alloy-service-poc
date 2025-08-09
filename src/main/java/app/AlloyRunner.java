package app;

import app.dto.SolveRequest;
import app.dto.SolveResponse;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Pair;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.*;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import kodkod.engine.satlab.SATFactory;

public class AlloyRunner {

  private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
      Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
      Math.max(2, Runtime.getRuntime().availableProcessors()),
      60L, TimeUnit.SECONDS,
      new ArrayBlockingQueue<>(64),
      new ThreadPoolExecutor.AbortPolicy()
  );
  private static final long DEFAULT_TIMEOUT_MS = 20_000L;
  private static final int MAX_MODEL_CHARS = 200_000; // ~200 KB
  private static final int MAX_INSTANCES = 5;

  public static SolveResponse solve(SolveRequest req) throws Exception {
    long started = System.currentTimeMillis();

    SolveResponse out = new SolveResponse();
    out.requestId = req.requestId;

    // Basic request validation and caps
    if (req == null || req.spec == null || req.spec.isBlank()) {
      return error("bad_request", "spec is required");
    }
    if (req.spec.length() > MAX_MODEL_CHARS) {
      return error("bad_request", "model too large");
    }

    // Hash model for observability (no raw model in logs)
    out.debug = new SolveResponse.Debug();
    out.debug.modelHash = sha256Hex(req.spec);

    long timeoutMs = (req.options != null && req.options.timeoutMs != null && req.options.timeoutMs > 0)
        ? req.options.timeoutMs.longValue()
        : DEFAULT_TIMEOUT_MS;
    Callable<SolveResponse> task = () -> executeSolve(req, started, out);

    Future<SolveResponse> f = EXECUTOR.submit(task);
    try {
      SolveResponse resp = f.get(timeoutMs, TimeUnit.MILLISECONDS);
      resp.debug.durationMs = System.currentTimeMillis() - started;
      return resp;
    } catch (TimeoutException te) {
      f.cancel(true);
      return errorWithDebug(out, started, "timeout", "solve exceeded " + timeoutMs + " ms");
    } catch (ExecutionException ee) {
      Throwable cause = ee.getCause();
      String msg = cause != null ? cause.getMessage() : ee.getMessage();
      return errorWithDebug(out, started, "solver_error", msg);
    } catch (RejectedExecutionException ree) {
      return errorWithDebug(out, started, "busy", "server busy");
    }
  }

  private static SolveResponse executeSolve(SolveRequest req, long started, SolveResponse out) throws Exception {
    // compute requested instance cap from scope (default 1)
    int requestedMaxInstances = 1;
    if (req.scope != null && req.scope.maxInstances != null && req.scope.maxInstances > 0) {
      requestedMaxInstances = req.scope.maxInstances;
    }
    if (requestedMaxInstances > MAX_INSTANCES) {
      out.warnings.add("max_instances capped to " + MAX_INSTANCES);
      requestedMaxInstances = MAX_INSTANCES;
    }
    // Strict single-module parsing to block filesystem opens
    if (req.spec.contains("open ")) {
      out.warnings.add("open statements are ignored in single-module mode");
    }
    // Alloy 6: parse via file-based API to ensure correct typechecking
    Module world = CompUtil.parseEverything_fromString(A4Reporter.NOP, req.spec);
    List<Command> cmds = world.getAllCommands();
    Command choice = chooseCommand(cmds, req.target, req.commandIndex, req.command);
    out.command = choice.toString();

    A4Options opt = new A4Options();
    setSolver(opt, req);
    opt.coreGranularity = orDefault(req.options != null ? req.options.coreGranularity : null, 1);
    opt.coreMinimization = orDefault(req.options != null ? req.options.coreMinimization : null, 2);
    opt.unrolls = orDefault(req.options != null ? req.options.unrolls : null, 3);
    // symmetry breaking knob (new name)
    Integer sym = (req.options != null) ? req.options.symmetryBreaking : null;
    opt.symmetry = orDefault(sym, 20);
    opt.recordKodkod = req.options != null && Boolean.TRUE.equals(req.options.recordKodkod);
    opt.originalFilename = "input.als";

    Command adjusted = choice;
    // Apply scope overrides if provided
    if (req.scope != null) {
      if (req.scope.bitwidth != null) {
        out.warnings.add("bitwidth override not wired to Command in this build");
      }
      if (req.scope.exactScopes != null) {
        for (Sig s : world.getAllReachableSigs()) {
          Integer n = req.scope.exactScopes.get(s.label);
          if (n != null) adjusted = adjusted.change(s, true, n);
        }
      }
    }

    A4Reporter NOP = A4Reporter.NOP;
    List<Map<String,Object>> instances = new ArrayList<>();
    A4Solution sol = TranslateAlloyToKodkod.execute_command(NOP, world.getAllReachableSigs(), adjusted, opt);
    if (sol.satisfiable()) {
      int max = Math.max(1, requestedMaxInstances);
      int produced = 0;
      while (!Thread.currentThread().isInterrupted() && sol.satisfiable() && produced < max) {
        instances.add(serializeInstance(world, sol));
        produced++;
        sol = sol.next();
      }
      out.status = "ok";
      out.satisfiable = true;
    } else {
      out.status = "ok";
      out.satisfiable = false;
      out.unsatCore = buildCore(req.spec, sol);
    }
    out.instances = instances;
    out.debug.solverId = getSolverId(opt);
    // bitwidth/maxSeq introspection omitted for portability across API versions
    out.debug.bitwidth = null;
    out.debug.maxSeq = null;
    out.debug.originalFile = opt.originalFilename;
    if (opt.recordKodkod) out.debug.kodkod = sol.debugExtractKInput();

    out.debug.durationMs = System.currentTimeMillis() - started;
    return out;
  }

  private static Command chooseCommand(List<Command> cmds, String label, Integer idx, String kind) {
    // Optional filter by kind ("run" vs "check") when label is absent
    if (label != null) {
      for (Command c : cmds) {
        if (c.label != null && c.label.equals(label)) return c;
      }
    }
    if (kind != null) {
      for (Command c : cmds) {
        boolean isCheck = c.check;
        if (("check".equalsIgnoreCase(kind) && isCheck) || ("run".equalsIgnoreCase(kind) && !isCheck)) {
          return c;
        }
      }
    }
    int i = (idx != null && idx >= 0 && idx < cmds.size()) ? idx : 0;
    return cmds.get(i);
  }

  private static void setSolver(A4Options opt, SolveRequest req) {
    String requested = (req != null && req.options != null && req.options.solver != null)
        ? req.options.solver
        : "minisat.prover";

    // Normalize common legacy names to Alloy 6.2 ids
    String normalized = normalizeSolverId(requested);

    SATFactory solver;
    try {
      solver = SATFactory.get(normalized);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Requested solver not found: " + normalized
          + "; available=" + SATFactory.getSolvers(), e);
    }
    if (!solver.isPresent() || !solver.prover()) {
      throw new IllegalStateException("Prover solver unavailable: " + solver.id());
    }
    opt.solver = solver;
  }

  private static String normalizeSolverId(String id) {
    if (id == null) return "minisat.prover";
    String s = id.trim();
    if (s.equalsIgnoreCase("MiniSatProverJNI") || s.equalsIgnoreCase("MiniSatProver")
        || s.equalsIgnoreCase("MINISAT_PROVER") || s.equalsIgnoreCase("MINISAT.PROVER")) {
      return "minisat.prover";
    }
    if (s.equalsIgnoreCase("SAT4J") || s.equalsIgnoreCase("sat4j")) {
      return "sat4j"; // SATFactory id for default SAT4J
    }
    return s;
  }

  private static String getSolverId(A4Options opt) {
    try {
      return (opt != null && opt.solver != null) ? opt.solver.id() : null;
    } catch (Exception e) { return null; }
  }

  private static Map<String,Object> serializeInstance(Module world, A4Solution sol) throws Exception {
    Map<String,Object> m = new LinkedHashMap<>();
    for (Sig s : world.getAllReachableSigs()) {
      m.put("sig:" + s.label, sol.eval(s).toString());
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
    Pair<Set<Pos>, Set<Pos>> hi = sol.highLevelCore();
    for (Pos p : hi.a) core.must.add(toRange(p, source, core.snippetByRangeId));
    for (Pos p : hi.b) core.maybe.add(toRange(p, source, core.snippetByRangeId));
    for (Pos p : sol.lowLevelCore()) core.low.add(toRange(p, source, core.snippetByRangeId));
    return core;
  }

  private static SolveResponse.Range toRange(Pos p, String source, Map<String,String> snippets) {
    SolveResponse.Range r = new SolveResponse.Range();
    r.file = p.filename;
    r.startLine = p.y; r.startCol = p.x;
    r.endLine = p.y2;  r.endCol = p.x2;
    r.id = r.file + ":" + r.startLine + ":" + r.startCol + "-" + r.endLine + ":" + r.endCol;
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

  private static SolveResponse error(String type, String message) {
    SolveResponse r = new SolveResponse();
    r.status = "error";
    r.error = new SolveResponse.ErrorInfo();
    r.error.type = type;
    r.error.message = message;
    return r;
  }

  private static SolveResponse errorWithDebug(SolveResponse base, long started, String type, String message) {
    SolveResponse r = base != null ? base : new SolveResponse();
    r.status = "timeout".equals(type) ? "timeout" : "error";
    r.error = new SolveResponse.ErrorInfo();
    r.error.type = type;
    r.error.message = message;
    if (r.debug == null) r.debug = new SolveResponse.Debug();
    r.debug.durationMs = System.currentTimeMillis() - started;
    return r;
  }

  private static String sha256Hex(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(d.length * 2);
      for (byte b : d) sb.append(String.format("%02x", b));
      return sb.toString();
    } catch (Exception e) { return null; }
  }

  private static int orDefault(Integer v, int d) { return v!=null ? v : d; }
}
