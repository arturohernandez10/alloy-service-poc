package app;

import app.dto.SolveRequest;
import app.dto.SolveResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.*;

public class OpenApiExamplesTest {

	@Test
	@Timeout(10)
	void example_sat_run_matches_behavior() throws Exception {
		// Mirrors docs/openapi.yaml components.examples.SolveRequestSat
		String spec = "sig A {}\n" +
				"pred show {}\n" +
				"run show for 3\n";
		SolveRequest req = new SolveRequest();
		req.spec = spec;
		req.command = "run";
		req.target = "show";
		req.scope = new SolveRequest.Scope();
		req.scope.maxInstances = 1;
		req.options = new SolveRequest.Options();
		req.options.solver = "SAT4J";
		req.options.timeoutMs = 2000;
		req.options.executeAllWhenUnspecified = false;

		SolveResponse resp = AlloyRunner.solve(req);
		assertEquals("ok", resp.status);
		assertEquals(Boolean.TRUE, resp.satisfiable);
		assertNotNull(resp.instances);
		assertFalse(resp.instances.isEmpty());
	}

	@Test
	@Timeout(10)
	void example_unsat_check_matches_behavior() throws Exception {
		// Mirrors docs/openapi.yaml components.examples.SolveRequestUnsat
		String spec = "sig A {}\n" +
				"assert noA { no A }\n" +
				"check noA for exactly 0 A\n";
		SolveRequest req = new SolveRequest();
		req.spec = spec;
		req.command = "check";
		req.target = "noA";
		req.options = new SolveRequest.Options();
		req.options.solver = "SAT4J"; // portable path; core will be null
		req.options.timeoutMs = 2000;
		req.options.executeAllWhenUnspecified = false;

		SolveResponse resp = AlloyRunner.solve(req);
		assertEquals("ok", resp.status);
		assertEquals(Boolean.FALSE, resp.satisfiable);
	}

	@Test
	@Timeout(10)
	void example_bad_request_matches_behavior() throws Exception {
		// Mirrors docs/openapi.yaml components.examples.SolveResponseBadRequest
		SolveRequest req = new SolveRequest(); // no spec provided
		SolveResponse resp = AlloyRunner.solve(req);
		assertEquals("error", resp.status);
		assertNotNull(resp.error);
		assertEquals("bad_request", resp.error.type);
	}

	@Test
	@Timeout(10)
	void example_timeout_matches_behavior_quickly() throws Exception {
		// Quick timeout: simple SAT model with very small timeout
		String spec = "sig A {} run {} for 6";
		SolveRequest req = new SolveRequest();
		req.spec = spec;
		req.options = new SolveRequest.Options();
		req.options.solver = "SAT4J";
		req.options.timeoutMs = 1; // force timeout
		req.options.executeAllWhenUnspecified = false;

		SolveResponse resp = AlloyRunner.solve(req);
		boolean timedOut = "timeout".equals(resp.status)
			|| (resp.error != null && "timeout".equals(resp.error.type));
		assertTrue(timedOut, "expected timeout envelope or timeout error type");
	}
}


