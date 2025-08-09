package app;

import io.javalin.Javalin;
import app.dto.SolveRequest;
import app.dto.SolveResponse;

public class App {
  public static void main(String[] args) {
    // Startup self-check: run a minimal UNSAT solve using MiniSatProverJNI and exit
    if (shouldRunSelfCheck(args)) {
      int code = runSelfCheck();
      System.exit(code);
    }

    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    Javalin app = Javalin.create(cfg -> {
      cfg.http.defaultContentType = "application/json";
    });
    app.post("/v1/solve", ctx -> {
      try {
        SolveRequest req = ctx.bodyAsClass(SolveRequest.class);
        SolveResponse resp = AlloyRunner.solve(req);
        ctx.json(resp);
      } catch (Exception e) {
        SolveResponse err = new SolveResponse();
        err.status = "error";
        err.error = new SolveResponse.ErrorInfo();
        err.error.type = "bad_request";
        err.error.message = e.getMessage();
        ctx.status(400).json(err);
      }
    });
    app.get("/healthz", ctx -> ctx.result("ok"));
    app.start(port);
  }

  private static boolean shouldRunSelfCheck(String[] args) {
    try {
      String env = System.getenv("SELF_CHECK");
      if (env != null && (env.equals("1") || env.equalsIgnoreCase("true"))) return true;
      if (args != null) {
        for (String a : args) { if ("--self-check".equals(a)) return true; }
      }
    } catch (Exception ignored) {}
    return false;
  }

  private static int runSelfCheck() {
    try {
      try { listAvailableSolvers(); } catch (Throwable ignored) {}
      // Make the check UNSAT by asserting no A with exactly 0 A (assertion holds; no counterexample)
      String spec = "sig A {}\n\nassert noA { no A }\ncheck noA for exactly 0 A\n";
      app.dto.SolveRequest req = new app.dto.SolveRequest();
      req.spec = spec;
      req.command = "check";
      req.target = "noA";
      req.options = new app.dto.SolveRequest.Options();
      req.options.solver = "minisat.prover";

      app.dto.SolveResponse resp = app.AlloyRunner.solve(req);
      boolean okEnvelope = "ok".equals(resp.status);
      boolean isUnsat = Boolean.FALSE.equals(resp.satisfiable);
      String sid = (resp.debug != null ? resp.debug.solverId : null);
      boolean solverIsProver = sid != null && (sid.equalsIgnoreCase("minisat.prover") || sid.equals("MiniSatProverJNI"));
      boolean hasCore = resp.unsatCore != null;
      boolean coreNonEmpty = hasCore && (
          (resp.unsatCore.must != null && !resp.unsatCore.must.isEmpty()) ||
          (resp.unsatCore.maybe != null && !resp.unsatCore.maybe.isEmpty()) ||
          (resp.unsatCore.low != null && !resp.unsatCore.low.isEmpty())
      );

      if (okEnvelope && isUnsat && solverIsProver && coreNonEmpty) {
        System.out.println("self-check ok: minisat.prover loaded, UNSAT core available");
        return 0;
      } else {
        System.out.println("self-check FAILED: status=" + resp.status
            + ", satisfiable=" + resp.satisfiable
            + ", solverId=" + sid
            + ", corePresent=" + (resp.unsatCore!=null)
            + ", coreNonEmpty=" + coreNonEmpty);
        return 2;
      }
    } catch (Throwable t) {
      System.out.println("self-check ERROR: " + t.getMessage());
      return 3;
    }
  }

  private static void listAvailableSolvers() {
    try {
      Class<?> a4 = Class.forName("edu.mit.csail.sdg.translator.A4Options");
      for (Class<?> c : a4.getDeclaredClasses()) {
        if (c.isEnum() && c.getSimpleName().toLowerCase().contains("solver")) {
          Object[] constants = c.getEnumConstants();
          System.out.println("Solvers:" );
          for (Object constant : constants) {
            String name = ((Enum<?>) constant).name();
            String id = null;
            try { id = String.valueOf(constant.getClass().getMethod("id").invoke(constant)); } catch (Exception ignore) {}
            System.out.println(" - name=" + name + ", id=" + id);
          }
        }
      }
    } catch (Exception ignored) {}
  }
}
