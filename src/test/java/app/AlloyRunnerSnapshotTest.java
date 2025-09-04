package app;

import app.dto.SolveRequest;
import app.dto.SolveResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AlloyRunnerSnapshotTest {

  private static final ObjectMapper OM = new ObjectMapper();

  @Test
  @Timeout(10)
  void sat_instance_snapshot_normalized() throws Exception {
    String spec = new String(Files.readAllBytes(Paths.get("src/test/resources/models/sat.als")));
    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.scope = new SolveRequest.Scope();
    req.scope.maxInstances = 1; // single instance for snapshot stability
    req.options = new SolveRequest.Options();
    req.options.solver = "SAT4J"; // portable
    req.options.executeAllWhenUnspecified = false;

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("ok", resp.status);
    assertEquals(Boolean.TRUE, resp.satisfiable);
    assertNotNull(resp.instances);
    assertFalse(resp.instances.isEmpty());

    // Sanitize the first instance by mapping atom ids per sig to SIG$index
    Map<String,Object> inst = (Map<String,Object>) resp.instances.get(0);
    Map<String,Object> sanitized = sanitizeInstance(inst);
    String sanitizedJson = OM.writeValueAsString(sanitized);

    String snapshot = new String(Files.readAllBytes(Paths.get("src/test/resources/snapshots/sat-instance.snapshot.json")));
    assertEquals(minify(snapshot), minify(sanitizedJson), "SAT instance JSON should match snapshot");
  }

  private static Map<String,Object> sanitizeInstance(Map<String,Object> inst) {
    Map<String,Object> out = new LinkedHashMap<>();
    Map<String,List<String>> atoms = (Map<String,List<String>>) inst.get("atoms");
    Map<String,List<List<String>>> relations = (Map<String,List<List<String>>>) inst.get("relations");

    Map<String,String> mapping = new HashMap<>();
    Map<String,List<String>> newAtoms = new LinkedHashMap<>();
    if (atoms != null) {
      List<String> sigs = new ArrayList<>(atoms.keySet());
      Collections.sort(sigs);
      for (String sig : sigs) {
        List<String> names = new ArrayList<>(atoms.getOrDefault(sig, Collections.emptyList()));
        Collections.sort(names);
        List<String> repl = new ArrayList<>(names.size());
        for (int i = 0; i < names.size(); i++) {
          String newName = sig + "$" + i;
          mapping.put(names.get(i), newName);
          repl.add(newName);
        }
        newAtoms.put(sig, repl);
      }
    }

    Map<String,List<List<String>>> newRels = new LinkedHashMap<>();
    if (relations != null) {
      List<String> relKeys = new ArrayList<>(relations.keySet());
      Collections.sort(relKeys);
      for (String rk : relKeys) {
        List<List<String>> tuples = relations.getOrDefault(rk, Collections.emptyList());
        List<List<String>> replTuples = new ArrayList<>(tuples.size());
        for (List<String> t : tuples) {
          List<String> nt = new ArrayList<>(t.size());
          for (String a : t) nt.add(mapping.getOrDefault(a, a));
          replTuples.add(nt);
        }
        newRels.put(rk, replTuples);
      }
    }

    out.put("atoms", newAtoms);
    out.put("relations", newRels);
    return out;
  }

  private static String minify(String s) {
    return s.replaceAll("\\s+", "").trim();
  }
}


