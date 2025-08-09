package app;

import app.dto.SolveRequest;
import app.dto.SolveResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

public class AlloyRunnerTest {

  @Test
  @Timeout(10)
  void sat_model_returns_sat_and_instance() throws Exception {
    String model = "sig A {}\n\npred p[] { some A }\nrun p for 3\n";
    SolveRequest req = new SolveRequest();
    req.model = model;
    req.maxInstances = 1;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J"; // portable

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("SAT", resp.status, "expected SAT");
    assertNotNull(resp.instances);
    assertFalse(resp.instances.isEmpty());
  }

  @Test
  @Timeout(10)
  void unsat_model_returns_unsat_without_core_on_sat4j() throws Exception {
    String model = "sig A {}\n\nfact { no A }\n\nassert someA { some A }\ncheck someA for 3\n";
    SolveRequest req = new SolveRequest();
    req.model = model;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("UNSAT", resp.status, "expected UNSAT");
    // On SAT4J, unsat core is not available; ensure no crash and allow null core
  }

  @Test
  @Timeout(10)
  void parse_error_is_reported() throws Exception {
    String model = "sig A {\n  x: A\n"; // missing closing brace
    SolveRequest req = new SolveRequest();
    req.model = model;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("ERROR", resp.status);
    assertNotNull(resp.error);
    assertEquals("solver_error", resp.error.type); // surfaced from parser/typechecker path
  }
}


