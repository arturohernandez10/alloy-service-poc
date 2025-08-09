package app;

import app.dto.SolveRequest;
import app.dto.SolveResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

public class AlloyRunnerTest {

  @Test
  @Timeout(10)
  void sat_model_returns_sat_and_instance() throws Exception {
    String spec = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/models/sat.als")));
    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J"; // portable

    SolveResponse resp = AlloyRunner.solve(req);
    if (!"ok".equals(resp.status)) {
      System.out.println("SAT test error: status=" + resp.status + ", message=" + (resp.error!=null?resp.error.message:null));
    }
    assertEquals("ok", resp.status, "expected ok envelope status");
    assertEquals(Boolean.TRUE, resp.satisfiable, "expected satisfiable=true");
    assertNotNull(resp.instances);
    assertFalse(resp.instances.isEmpty());
  }

  @Test
  @Timeout(10)
  void unsat_model_returns_unsat_without_core_on_sat4j() throws Exception {
    String spec = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/models/unsat.als")));
    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";

    SolveResponse resp = AlloyRunner.solve(req);
    if (!"ok".equals(resp.status)) {
      System.out.println("UNSAT test error: status=" + resp.status + ", message=" + (resp.error!=null?resp.error.message:null));
    }
    assertEquals("ok", resp.status, "expected ok envelope");
    assertEquals(Boolean.FALSE, resp.satisfiable, "expected satisfiable=false");
    // On SAT4J, unsat core is not available; ensure no crash and allow null core
  }

  @Test
  @Timeout(10)
  void parse_error_is_reported() throws Exception {
    String spec = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/models/parse_error.als")));
    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("error", resp.status);
    assertNotNull(resp.error);
    assertEquals("solver_error", resp.error.type); // surfaced from parser/typechecker path
  }

  @Test
  void dto_ignores_unknown_fields() throws Exception {
    String json = "{\n" +
        "  \"spec\": \"pred p() {} run p\",\n" +
        "  \"extraneous\": 123,\n" +
        "  \"scope\": { \"max_instances\": 1, \"unknown\": \"x\" },\n" +
        "  \"options\": { \"timeout_ms\": 10, \"seed\": 1, \"unknown\": true }\n" +
        "}";
    ObjectMapper om = new ObjectMapper();
    SolveRequest req = om.readValue(json, SolveRequest.class);
    assertNotNull(req);
    assertEquals(1, req.scope.maxInstances);
    assertEquals(10, req.options.timeoutMs);
  }
}


