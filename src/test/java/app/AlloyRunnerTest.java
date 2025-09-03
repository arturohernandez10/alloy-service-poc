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
    // Ensure single-command mode for existing tests
    req.options.executeAllWhenUnspecified = false;

    SolveResponse resp = AlloyRunner.solve(req);
    if (!"ok".equals(resp.status)) {
      System.out.println("SAT test error: status=" + resp.status + ", message=" + (resp.error!=null?resp.error.message:null));
    }
    assertEquals("ok", resp.status, "expected ok envelope status");
    assertEquals(Boolean.TRUE, resp.satisfiable, "expected satisfiable=true");
    assertNotNull(resp.instances);
    assertFalse(resp.instances.isEmpty());
    // Validate normalized shape
    java.util.Map<String,Object> inst = resp.instances.get(0);
    assertTrue(inst.containsKey("atoms"));
    assertTrue(inst.containsKey("relations"));
    @SuppressWarnings("unchecked")
    java.util.Map<String,java.util.List<String>> atoms = (java.util.Map<String,java.util.List<String>>) inst.get("atoms");
    assertNotNull(atoms);
    assertTrue(atoms.containsKey("A"));
    assertEquals(2, atoms.get("A").size());
    @SuppressWarnings("unchecked")
    java.util.Map<String,java.util.List<java.util.List<String>>> rels = (java.util.Map<String,java.util.List<java.util.List<String>>>) inst.get("relations");
    assertNotNull(rels);
    assertTrue(rels.isEmpty());
  }

  @Test
  @Timeout(10)
  void unsat_model_returns_unsat_without_core_on_sat4j() throws Exception {
    String spec = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/models/unsat.als")));
    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";
    req.options.executeAllWhenUnspecified = false;

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
    req.options.executeAllWhenUnspecified = false;

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("error", resp.status);
    assertNotNull(resp.error);
    assertEquals("parse_error", resp.error.type);
    assertNotNull(resp.error.range, "expected range on parse_error");
    assertTrue(resp.error.range.startLine >= 1);
    assertTrue(resp.error.range.startCol >= 1);
  }

  @Test
  @Timeout(10)
  void type_error_is_reported_with_range() throws Exception {
    String spec = "sig A {}\n\npred p() { A in 1->A }\nrun p";
    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";
    req.options.executeAllWhenUnspecified = false;

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("error", resp.status);
    assertNotNull(resp.error);
    assertEquals("type_error", resp.error.type);
    assertNotNull(resp.error.range, "expected range on type_error");
    assertTrue(resp.error.range.startLine >= 1);
    assertTrue(resp.error.range.startCol >= 1);
  }

  @Test
  void dto_ignores_unknown_fields() throws Exception {
    String json = "{\n" +
        "  \"spec\": \"pred p() {} run p\",\n" +
        "  \"extraneous\": 123,\n" +
        "  \"scope\": { \"max_instances\": 1, \"unknown\": \"x\" },\n" +
        "  \"options\": { \"timeout_ms\": 10, \"seed\": 1, \"unknown\": true, \"execute_all_when_unspecified\": false }\n" +
        "}";
    ObjectMapper om = new ObjectMapper();
    SolveRequest req = om.readValue(json, SolveRequest.class);
    assertNotNull(req);
    assertEquals(1, req.scope.maxInstances);
    assertEquals(10, req.options.timeoutMs);
  }

  @Test
  @Timeout(10)
  void timeout_returns_timeout_status_and_type() throws Exception {
    // Use a simple SAT model but force an extremely small timeout
    String spec = "sig A {} run {} for 6";
    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J";
    req.options.timeoutMs = 1; // very small to trigger timeout
    req.options.executeAllWhenUnspecified = false;

    SolveResponse resp = AlloyRunner.solve(req);
    assertTrue("timeout".equals(resp.status) || (resp.error != null && "timeout".equals(resp.error.type)),
        "expected timeout envelope");
  }

  @Test
  @Timeout(10)
  void determinism_same_seed_same_instance_when_supported() throws Exception {
    String spec = "sig A {} run {} for 3";
    SolveRequest req1 = new SolveRequest();
    req1.spec = spec;
    req1.scope = new SolveRequest.Scope();
    req1.scope.maxInstances = 1;
    req1.options = new SolveRequest.Options();
    req1.options.solver = "SAT4J"; // portable path
    req1.options.seed = 123;
    req1.options.executeAllWhenUnspecified = false;

    SolveRequest req2 = new SolveRequest();
    req2.spec = spec;
    req2.scope = new SolveRequest.Scope();
    req2.scope.maxInstances = 1;
    req2.options = new SolveRequest.Options();
    req2.options.solver = "SAT4J";
    req2.options.seed = 123;
    req2.options.executeAllWhenUnspecified = false;

    SolveResponse r1 = AlloyRunner.solve(req1);
    SolveResponse r2 = AlloyRunner.solve(req2);

    if (r1.warnings != null && r1.warnings.stream().anyMatch(w -> w.contains("seed provided but"))) {
      // Seeding unsupported in this build/solver; skip strong assertion
      return;
    }
    assertEquals("ok", r1.status);
    assertEquals("ok", r2.status);
    assertEquals(Boolean.TRUE, r1.satisfiable);
    assertEquals(Boolean.TRUE, r2.satisfiable);
    assertNotNull(r1.instances);
    assertNotNull(r2.instances);
    assertFalse(r1.instances.isEmpty());
    assertFalse(r2.instances.isEmpty());
    assertEquals(r1.instances.get(0), r2.instances.get(0), "instances should match under same seed");
  }
}


