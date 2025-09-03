package app.core;

import app.dto.SolveResponse;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.alloy4.Pair;
import edu.mit.csail.sdg.alloy4.Pos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class CommandExecutor {

  private CommandExecutor() {}

  public static class CommandResult {
    public boolean satisfiable;
    public List<Map<String,Object>> instances;
    public SolveResponse.UnsatCore unsatCore;
    public String kodkod; // optional, only when recordKodkod
  }

  public static CommandResult execute(Module world, Command adjusted, A4Options opt, int requestedMaxInstances, int serviceHardCap, String sourceForCoreSnippets) throws Exception {
    int max = Math.max(1, Math.min(requestedMaxInstances, serviceHardCap));

    A4Reporter NOP = A4Reporter.NOP;
    A4Solution sol = edu.mit.csail.sdg.translator.TranslateAlloyToKodkod.execute_command(NOP, world.getAllReachableSigs(), adjusted, opt);

    CommandResult cr = new CommandResult();
    List<Map<String,Object>> instances = new ArrayList<>();
    if (sol.satisfiable()) {
      int produced = 0;
      while (!Thread.currentThread().isInterrupted() && sol.satisfiable() && produced < max) {
        instances.add(InstanceSerializer.serializeInstance(world, sol));
        produced++;
        sol = sol.next();
      }
      cr.satisfiable = true;
    } else {
      cr.satisfiable = false;
      try {
        if (opt != null && opt.solver != null && opt.solver.prover()) {
          cr.unsatCore = buildCore(sourceForCoreSnippets, sol);
        }
      } catch (Throwable ignored) {}
    }
    cr.instances = instances;
    if (opt.recordKodkod) {
      try { cr.kodkod = sol.debugExtractKInput(); } catch (Throwable ignored) {}
    }
    return cr;
  }

  private static SolveResponse.UnsatCore buildCore(String source, A4Solution sol) {
    SolveResponse.UnsatCore core = new SolveResponse.UnsatCore();
    core.must = new ArrayList<>();
    core.maybe = new ArrayList<>();
    core.low = new ArrayList<>();
    core.snippetByRangeId = new LinkedHashMap<>();
    Pair<java.util.Set<Pos>, java.util.Set<Pos>> hi = sol.highLevelCore();
    for (Pos p : hi.a) core.must.add(toRange(p, source, core.snippetByRangeId));
    for (Pos p : hi.b) core.maybe.add(toRange(p, source, core.snippetByRangeId));
    for (Pos p : sol.lowLevelCore()) core.low.add(toRange(p, source, core.snippetByRangeId));
    return core;
  }

  private static SolveResponse.Range toRange(Pos p, String source, Map<String,String> snippets) {
    SolveResponse.Range r = new SolveResponse.Range();
    r.file = p.filename;
    r.startLine = p.y; r.startCol = p.x;
    r.endLine = p.y2;  r.endCol = p.x2;
    r.id = r.file + ":" + r.startLine + ":" + r.startCol + "-" + r.endLine + ":" + r.endCol;
    String snippet = slice(source, r.startLine, r.startCol, r.endLine, r.endCol);
    if (snippet != null && !snippet.isBlank()) snippets.put(r.id, snippet);
    return r;
  }

  private static String slice(String text, int y1, int x1, int y2, int x2) {
    try {
      String[] lines = text.split("\r?\n", -1);
      StringBuilder sb = new StringBuilder();
      for (int i = y1; i <= y2 && i <= lines.length; i++) {
        String line = lines[i-1];
        int start = (i==y1) ? Math.max(0, x1-1) : 0;
        int end   = (i==y2) ? Math.min(line.length(), x2-1) : line.length();
        if (start < end) sb.append(line, start, end);
        if (i<y2) sb.append('\n');
      }
      return sb.toString();
    } catch (Exception e) { return null; }
  }
}


