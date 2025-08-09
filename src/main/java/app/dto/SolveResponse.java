package app.dto;

import java.util.*;

public class SolveResponse {
  public String requestId;
  public String status;
  public String command;
  public List<Map<String,Object>> instances;
  public UnsatCore core;
  public List<String> warnings = new ArrayList<>();
  public Debug debug;
  public ErrorInfo error;
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
  }

  public static class ErrorInfo {
    public String type; // parse_error | type_error | timeout | oom | solver_error | bad_request
    public String message;
  }
}
