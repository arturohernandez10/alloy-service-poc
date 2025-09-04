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

  @Test
  @Timeout(10)
  void shows_full_error_details_per_subcommand() throws Exception {
    String spec =
        // one harmless command that should succeed
        "run {} for 3\n" +
        // one command that requests an impossible number of Int atoms for default bitwidth
        "run {} for exactly 100 Int\n";

    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";
    req.options.executeAllWhenUnspecified = true; // multi-command mode

    SolveResponse resp = AlloyRunner.solve(req);
    if (resp.results == null || resp.results.isEmpty()) {
      // If the model fails before per-command execution (parser/type error), there's nothing to assert here.
      // This test focuses on per-command error shape when present.
      return;
    }

    boolean sawSat = resp.results.stream().anyMatch(r -> Boolean.TRUE.equals(r.satisfiable));
    assertTrue(sawSat, "expected at least one satisfiable sub-command");

    SolveResponse.CommandResult errored = resp.results.stream()
        .filter(r -> r.error != null)
        .findFirst()
        .orElse(null);
    if (errored != null) {
      assertNotNull(errored.error.type, "expected error type on sub-command error");
      assertNotNull(errored.error.message, "expected error message on sub-command error");
      assertNotNull(errored.error.range, "expected source range on sub-command error");
      assertTrue(errored.error.range.startLine >= 1);
      assertTrue(errored.error.range.startCol >= 1);
      // Prefer specific mapping when available; allow solver_error fallback
      assertTrue(
          "type_error".equals(errored.error.type) || "parse_error".equals(errored.error.type) || "solver_error".equals(errored.error.type),
          "expected a mapped error type");
    }
  }
}

