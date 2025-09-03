package app;
import app.core.CommandExecutor;

import app.dto.SolveRequest;
import app.dto.SolveResponse;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorAPI;
import edu.mit.csail.sdg.alloy4.ErrorFatal;
import edu.mit.csail.sdg.alloy4.ErrorSyntax;
import edu.mit.csail.sdg.alloy4.ErrorType;
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
      if (cause instanceof Err) {
        return errorFromErr(out, started, (Err) cause, req.spec);
      }
      String msg = cause != null ? cause.getMessage() : ee.getMessage();
      return errorWithDebug(out, started, "solver_error", msg);
    } catch (RejectedExecutionException ree) {
      return errorWithDebug(out, started, "busy", "server busy");
    }
  }

  private static SolveResponse executeSolve(SolveRequest req, long started, SolveResponse out) throws Exception {
    try {
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

    boolean gate = req.options != null && Boolean.TRUE.equals(req.options.executeAllWhenUnspecified);
    boolean noSelection = (req.target == null && req.commandIndex == null);

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

    // Apply deterministic seed if provided (best-effort)
    if (req.options != null && req.options.seed != null) {
      long seed = req.options.seed.longValue();
      out.debug.seed = seed;
      try {
        java.lang.reflect.Method m = opt.getClass().getMethod("setRandomSeed", long.class);
        m.invoke(opt, seed);
      } catch (Throwable t1) {
        try {
          java.lang.reflect.Field f = opt.getClass().getDeclaredField("kkOptions");
          f.setAccessible(true);
          Object kk = f.get(opt);
          if (kk != null) {
            try {
              java.lang.reflect.Method mm = kk.getClass().getMethod("setRandomSeed", long.class);
              mm.invoke(kk, seed);
            } catch (Throwable t2) {
              out.warnings.add("seed provided but could not be applied (unsupported in this build)");
            }
          } else {
            out.warnings.add("seed provided but no underlying Kodkod options found");
          }
        } catch (Throwable t3) {
          out.warnings.add("seed provided but could not be applied (unsupported in this build)");
        }
      }
    }

    if (gate && noSelection) {
      // Multi-command mode
      out.results = new ArrayList<>();
      // Top-level single-command fields MUST remain null when results are present
      out.satisfiable = null;
      out.instances = null;
      out.unsatCore = null;
      out.command = null;
      out.error = null;

      // Selection: filter by kind when req.command is provided
      List<Command> selected = new ArrayList<>();
      if (req.command != null) {
        boolean wantCheck = "check".equalsIgnoreCase(req.command);
        boolean wantRun = "run".equalsIgnoreCase(req.command);
        for (Command c : cmds) {
          if ((wantCheck && c.check) || (wantRun && !c.check)) selected.add(c);
        }
      } else {
        selected.addAll(cmds);
      }

      long timeoutMs = (req.options != null && req.options.timeoutMs != null && req.options.timeoutMs > 0)
          ? req.options.timeoutMs.longValue()
          : DEFAULT_TIMEOUT_MS;
      long deadline = started + timeoutMs;

      for (int i = 0; i < selected.size(); i++) {
        long now = System.currentTimeMillis();
        if (now >= deadline) {
          out.status = "timeout";
          out.debug.durationMs = now - started;
          return out; // completed sub-commands only are in results
        }
        long remaining = Math.max(0, deadline - now);

        Command base = selected.get(i);
        Command adjusted = base;
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

        long subStart = System.currentTimeMillis();
        SolveResponse.CommandResult rr = new SolveResponse.CommandResult();
        rr.command = base.toString();

        final Module worldF = world;
        final Command adjustedF = adjusted;
        final A4Options optF = opt;
        final int reqMaxF = requestedMaxInstances;
        final int hardCapF = MAX_INSTANCES;
        final String sourceF = req.spec;
        Callable<CommandExecutor.CommandResult> callable = () ->
            CommandExecutor.execute(worldF, adjustedF, optF, reqMaxF, hardCapF, sourceF);

        Future<CommandExecutor.CommandResult> subFuture = EXECUTOR.submit(callable);
        try {
          CommandExecutor.CommandResult cr = subFuture.get(remaining, TimeUnit.MILLISECONDS);
          rr.satisfiable = cr.satisfiable;
          rr.unsatCore = cr.unsatCore;
          rr.instances = cr.instances;
          rr.debug = new SolveResponse.Debug();
          rr.debug.solverId = getSolverId(opt);
          rr.debug.durationMs = System.currentTimeMillis() - subStart;
          if (opt.recordKodkod) rr.debug.kodkod = cr.kodkod;
          out.results.add(rr); // only append completed sub-commands
        } catch (TimeoutException te) {
          subFuture.cancel(true);
          out.status = "timeout";
          out.debug.durationMs = System.currentTimeMillis() - started;
          return out; // omit in-progress sub-command
        } catch (ExecutionException ee) {
          Throwable t = ee.getCause();
          rr.error = new SolveResponse.ErrorInfo();
          if (t instanceof Err) {
            Err e = (Err) t;
            rr.error.type = mapErrType(e);
            rr.error.message = e.getMessage();
            if (e.pos != null && e.pos != Pos.UNKNOWN) {
              Map<String,String> scratch = new LinkedHashMap<>();
              rr.error.range = toRange(e.pos, req.spec, scratch);
            }
          } else {
            rr.error.type = "solver_error";
            rr.error.message = (t != null ? t.getMessage() : ee.getMessage());
          }
          rr.debug = new SolveResponse.Debug();
          rr.debug.solverId = getSolverId(opt);
          rr.debug.durationMs = System.currentTimeMillis() - subStart;
          out.results.add(rr);
        }
      }
      out.status = "ok";
      out.debug.solverId = getSolverId(opt);
      out.debug.bitwidth = null;
      out.debug.maxSeq = null;
      out.debug.originalFile = opt.originalFilename;
      out.debug.durationMs = System.currentTimeMillis() - started;
      return out;
    }

    // Single-command mode
    Command choice = chooseCommand(cmds, req.target, req.commandIndex, req.command);
    out.command = choice.toString();

    Command adjusted = choice;
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

    CommandExecutor.CommandResult cr = CommandExecutor.execute(world, adjusted, opt,
        requestedMaxInstances, MAX_INSTANCES, req.spec);
    out.status = "ok";
    out.satisfiable = cr.satisfiable;
    out.unsatCore = cr.unsatCore;
    out.instances = cr.instances;
    out.debug.solverId = getSolverId(opt);
    out.debug.bitwidth = null;
    out.debug.maxSeq = null;
    out.debug.originalFile = opt.originalFilename;
    if (opt.recordKodkod) out.debug.kodkod = cr.kodkod;

    out.debug.durationMs = System.currentTimeMillis() - started;
    return out;
    } catch (Err e) {
      return errorFromErr(out, started, e, req.spec);
    }
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
        : "sat4j";

    // Normalize common legacy names to Alloy 6.2 ids
    String normalized = normalizeSolverId(requested);

    SATFactory solver;
    try {
      solver = SATFactory.get(normalized);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Requested solver not found: " + normalized
          + "; available=" + SATFactory.getSolvers(), e);
    }
    if (!solver.isPresent()) {
      throw new IllegalStateException("Requested solver unavailable: " + solver.id());
    }
    opt.solver = solver;
  }

  private static String normalizeSolverId(String id) {
    if (id == null) return "sat4j";
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

  // Instance serialization helpers moved to app.core.InstanceSerializer

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

  private static SolveResponse errorFromErr(SolveResponse base, long started, Err e, String source) {
    SolveResponse r = base != null ? base : new SolveResponse();
    r.status = "error";
    r.error = new SolveResponse.ErrorInfo();
    r.error.type = mapErrType(e);
    r.error.message = e.getMessage();
    if (e.pos != null && e.pos != Pos.UNKNOWN) {
      Map<String,String> scratch = new LinkedHashMap<>();
      r.error.range = toRange(e.pos, source, scratch);
    }
    if (r.debug == null) r.debug = new SolveResponse.Debug();
    r.debug.durationMs = System.currentTimeMillis() - started;
    return r;
  }

  private static String mapErrType(Err e) {
    if (e instanceof ErrorSyntax) return "parse_error";
    if (e instanceof ErrorType) return "type_error";
    if (e instanceof ErrorAPI) return "bad_request";
    if (e instanceof ErrorFatal) return "solver_error";
    return "solver_error";
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
