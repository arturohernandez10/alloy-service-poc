package app.dto;

import java.util.Map;

public class SolveRequest {
  public String model;
  public String commandLabel;
  public Integer commandIndex;
  public Integer maxInstances;
  public Options options;
  public static class Options {
    public String solver;
    public Integer coreGranularity;
    public Integer coreMinimization;
    public Integer unrolls;
    public Integer symmetry;
    public Boolean recordKodkod;
  }
  public Map<String,Integer> exactScopes;
  public Integer bitwidth;
  public Integer maxSeq;
  public String requestId;
}
