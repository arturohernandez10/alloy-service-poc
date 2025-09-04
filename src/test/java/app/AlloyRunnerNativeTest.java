package app;

import app.dto.SolveRequest;
import app.dto.SolveResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

@Tag("native")
public class AlloyRunnerNativeTest {

  @Test
  @Timeout(15)
  void unsat_core_available_with_minisatproverjni() throws Exception {
    String spec = "sig A {}\n\nassert someA { some A }\ncheck someA for exactly 0 A\n";
    SolveRequest req = new SolveRequest();
    req.spec = spec;
    req.options = new SolveRequest.Options();
    req.options.solver = "MiniSatProverJNI"; // requires native libs (Linux container)
    req.options.executeAllWhenUnspecified = false;

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("ok", resp.status, "expected ok envelope for UNSAT");
    assertEquals(Boolean.FALSE, resp.satisfiable, "expected satisfiable=false for UNSAT");
    assertNotNull(resp.unsatCore, "unsat core should be present with MiniSatProverJNI");
    assertNotNull(resp.unsatCore.must);
    assertNotNull(resp.unsatCore.maybe);
    assertNotNull(resp.unsatCore.low);
  }
}


