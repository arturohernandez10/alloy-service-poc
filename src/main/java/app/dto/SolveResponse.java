package app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SolveResponse {
  public String requestId;
  public String status; // ok | timeout | error (envelope)
  public Boolean satisfiable; // null when status=error
  public String command;
  public List<Map<String,Object>> instances;
  @JsonProperty("unsat_core")
  public UnsatCore unsatCore;
  public List<String> warnings = new ArrayList<>();
  public Debug debug;
  public ErrorInfo error;
  public List<CommandResult> results; // Present only in multi-command mode
  public static class UnsatCore {
    public List<Range> must;
    public List<Range> maybe;
    public List<Range> low;
    public Map<String,String> snippetByRangeId;
  }
  public static class Range {
    public String id;
    public String file;
    public int startLine, startCol, endLine, endCol;
  }
  public static class Debug {
    public String solverId;
    public Integer bitwidth;
    public Integer maxSeq;
    public String kodkod;
    public String originalFile;
    public Long durationMs;
    public Long peakMemoryBytes;
    public String modelHash;
    public Long seed;
  }

  public static class ErrorInfo {
    public String type; // parse_error | type_error | timeout | oom | solver_error | bad_request
    public String message;
    public Range range; // optional source range when available
  }

  /**
   * Per-command result shape for multi-command mode. Mirrors single-command fields.
   * When `results` is present on the top-level response, top-level fields
   * like `satisfiable`, `instances`, `unsat_core`, `command`, and `error` MUST be null.
   */
  public static class CommandResult {
    public String command;
    public Boolean satisfiable;
    public List<Map<String,Object>> instances;
    @JsonProperty("unsat_core")
    public UnsatCore unsatCore;
    public List<String> warnings = new ArrayList<>();
    public Debug debug; // per-result solver/debug info
    public ErrorInfo error; // per-result execution error (post-parse/type)
  }
}
