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
    String model = "sig A {}\n\nfact { no A }\n\nassert someA { some A }\ncheck someA for 3\n";
    SolveRequest req = new SolveRequest();
    req.model = model;
    req.options = new SolveRequest.Options();
    req.options.solver = "MiniSatProverJNI"; // requires native libs (Linux container)

    SolveResponse resp = AlloyRunner.solve(req);
    assertEquals("UNSAT", resp.status, "expected UNSAT");
    assertNotNull(resp.core, "unsat core should be present with MiniSatProverJNI");
    assertNotNull(resp.core.must);
    assertNotNull(resp.core.maybe);
    assertNotNull(resp.core.low);
  }
}


