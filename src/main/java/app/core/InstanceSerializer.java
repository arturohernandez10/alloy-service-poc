package app.core;

import edu.mit.csail.sdg.ast.Module;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.A4Tuple;
import edu.mit.csail.sdg.translator.A4TupleSet;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InstanceSerializer {

  private InstanceSerializer() {}

  public static Map<String,Object> serializeInstance(Module world, A4Solution sol) throws Exception {
    Map<String, Object> instance = new LinkedHashMap<>();
    Map<String, List<String>> atomsBySig = new LinkedHashMap<>();
    Map<String, List<List<String>>> tuplesByRelation = new LinkedHashMap<>();

    List<Sig> sigs = new ArrayList<>(world.getAllReachableSigs());
    sigs.sort(Comparator.comparing(s -> normalizeLabel(s.label)));

    for (Sig sig : sigs) {
      String sigLabel = normalizeLabel(sig.label);
      if (isBuiltinSigLabel(sigLabel)) continue;

      try {
        A4TupleSet ts = sol.eval(sig);
        List<String> atoms = new ArrayList<>();
        for (A4Tuple t : ts) {
          atoms.add(normalizeLabel(t.atom(0)));
        }
        atoms.sort(Comparator.naturalOrder());
        atomsBySig.put(sigLabel, atoms);
      } catch (Throwable ignored) {}

      List<edu.mit.csail.sdg.ast.Sig.Field> fields = new ArrayList<edu.mit.csail.sdg.ast.Sig.Field>();
      for (edu.mit.csail.sdg.ast.Sig.Field f0 : sig.getFields()) fields.add(f0);
      fields.sort(Comparator.comparing(f -> normalizeLabel(f.label)));
      for (edu.mit.csail.sdg.ast.Sig.Field f : fields) {
        String relKey = sigLabel + "." + normalizeLabel(f.label);
        try {
          A4TupleSet fs = sol.eval(f);
          List<List<String>> tuples = new ArrayList<>();
          for (A4Tuple t : fs) {
            int arity = t.arity();
            List<String> tuple = new ArrayList<>(arity);
            for (int i = 0; i < arity; i++) {
              tuple.add(normalizeLabel(t.atom(i)));
            }
            tuples.add(tuple);
          }
          tuples.sort((a, b) -> {
            int n = Math.min(a.size(), b.size());
            for (int i = 0; i < n; i++) {
              int c = a.get(i).compareTo(b.get(i));
              if (c != 0) return c;
            }
            return Integer.compare(a.size(), b.size());
          });
          tuplesByRelation.put(relKey, tuples);
        } catch (Throwable ignored) {}
      }
    }

    instance.put("atoms", atomsBySig);
    instance.put("relations", tuplesByRelation);
    return instance;
  }

  public static boolean isBuiltinSigLabel(String normalizedLabel) {
    return normalizedLabel == null || normalizedLabel.isEmpty()
        || normalizedLabel.equals("univ")
        || normalizedLabel.equals("Int")
        || normalizedLabel.equals("seq/Int")
        || normalizedLabel.equals("String")
        || normalizedLabel.equals("iden")
        || normalizedLabel.equals("none");
  }

  public static String normalizeLabel(String label) {
    if (label == null) return null;
    String s = label;
    if (s.startsWith("this/")) s = s.substring(5);
    return s;
  }
}


