package app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SolveRequest {
  public String spec;           // Primary Alloy module text
  public String command;        // "check" | "run" (optional)
  public String target;         // predicate/assertion label to execute (optional)
  public Integer commandIndex;  // optional index fallback

  // Optional extra modules (not yet resolved in runner; placeholder for K-20250809-004)
  public List<FileItem> files;

  // Optional scoping block
  public Scope scope;

  // Legacy top-level knobs consolidated into options
  public Options options;

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class FileItem {
    public String path;
    public String content;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Scope {
    public Boolean exact;
    public Integer bitwidth;
    public Integer ints;
    @JsonProperty("max_instances")
    public Integer maxInstances;
    // Optional per-sig exact scopes: label -> exactly N
    public Map<String,Integer> exactScopes;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Options {
    public String solver;            // e.g., SAT4J, MiniSatProverJNI
    public Integer coreGranularity;  // 0..3
    public Integer coreMinimization; // 0..2
    public Integer unrolls;
    @JsonProperty("symmetry_breaking")
    public Integer symmetryBreaking; // replaces "symmetry"
    public Boolean recordKodkod;
    @JsonProperty("timeout_ms")
    public Integer timeoutMs;
    public Integer seed;
  }

  public String requestId;
}
