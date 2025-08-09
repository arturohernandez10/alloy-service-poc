package app;

import io.javalin.Javalin;
import app.dto.SolveRequest;
import app.dto.SolveResponse;

public class App {
  public static void main(String[] args) {
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
}
