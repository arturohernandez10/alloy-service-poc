package app;

import app.dto.SolveRequest;
import app.dto.SolveResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

public class MultiCommandModeTest {

  @Test
  @Timeout(10)
  void executes_all_when_unspecified_gated_true_runs_both_kinds() throws Exception {
    String spec = "sig A {}\n" +
        "pred p() {}\n" +
        "run p for 3\n" +
        "assert noA { no A }\n" +
        "check noA for exactly 0 A\n";

    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";
    req.options.timeoutMs = 3000;
    req.options.executeAllWhenUnspecified = true;

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("ok", resp.status);
    assertNull(resp.satisfiable, "top-level satisfiable must be null in multi-command mode");
    assertNull(resp.instances, "top-level instances must be null in multi-command mode");
    assertNull(resp.unsatCore, "top-level unsat_core must be null in multi-command mode");
    assertNull(resp.command, "top-level command must be null in multi-command mode");
    assertNull(resp.error, "top-level error must be null in multi-command mode");
    assertNotNull(resp.results);
    assertTrue(resp.results.size() >= 2, "expected at least two commands executed");
    // Expect one SAT run and one UNSAT check
    boolean sawSat = resp.results.stream().anyMatch(r -> Boolean.TRUE.equals(r.satisfiable));
    boolean sawUnsat = resp.results.stream().anyMatch(r -> Boolean.FALSE.equals(r.satisfiable));
    assertTrue(sawSat, "expected a SAT result among results");
    assertTrue(sawUnsat, "expected an UNSAT result among results");
  }

  @Test
  @Timeout(10)
  void executes_only_run_when_command_filter_provided() throws Exception {
    String spec = "sig A {}\n" +
        "pred p() {}\n" +
        "run p for 3\n" +
        "assert noA { no A }\n" +
        "check noA for exactly 0 A\n";

    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.command = "run"; // filter to run commands only
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";
    req.options.executeAllWhenUnspecified = true;

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("ok", resp.status);
    assertNotNull(resp.results);
    assertFalse(resp.results.isEmpty());
    assertTrue(resp.results.stream().allMatch(r -> Boolean.TRUE.equals(r.satisfiable)));
  }
}

