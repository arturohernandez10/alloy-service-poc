## Background Agents (Cursor)

This repo is configured for Cursor Background Agents via `.cursor/environment.json`.

### Prerequisites

- Ensure the repo is on GitHub.
- Grant the Cursor GitHub app read-write access to this repo (and any submodules).

### Environment

- The background machine is Ubuntu-based with internet access.
- `.cursor/environment.json` installs: OpenJDK 17, Maven, and native libs (`libgmp10`, `libstdc++6`, `libgcc-s1`) required by Alloy's JNI solver.
- Terminals included:
  - Run service: builds and runs `target/alloy-service-poc-0.1.0.jar` on `PORT` (default 8080)
  - Run tests: `mvn test`
  - Self-check: verifies native solver availability

### How to Start a Background Agent

1. Commit/push `.cursor/environment.json` (already present).
2. In Cursor, open the Background Agents sidebar or press `Ctrl+E`.
3. Choose this repo and start a new agent. The machine will:
   - Provision Java/Maven and go offline with dependencies
   - Start your selected terminal(s)
4. You can send follow-up prompts, view logs, or take over the machine.

### Advanced: Custom Machine via Dockerfile

For stricter parity with production images or custom toolchains, create a snapshot based on your Dockerfile. Do not `COPY` the whole project in the Dockerfile; keep dependency install logic in the environment install step.

### Secrets

Add any required secrets (tokens, endpoints) in the background agent setup flow. They are encrypted at rest and exposed as env vars to the agent.

### Notes

- CI-like builds use `mvn -q -DskipTests package` for speed; remove `-DskipTests` if you want tests in the run terminal.
- The agent runs terminal commands automatically; be mindful of prompt-injection risks when allowing internet access.


