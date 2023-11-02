package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Func;
import edu.mit.csail.sdg.parser.CompModule;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface AlloyUtil {
    static Stream<Func> streamFuncsWithNames(Collection<Func> allFunctions, Collection<String> targetNames) {
        return allFunctions.stream().filter(x -> targetNames.contains(x.label.replace("this/", "")));
    }

    static List<Func> getFuncsWithNames(Collection<Func> allFunctions, Collection<String> targetNames) {
        return allFunctions.stream().filter(x -> targetNames.contains(x.label.replace("this/", ""))).toList();
    }

    static Stream<String> streamFuncsNamesWithNames(Collection<Func> allFunctions, Set<String> targetNames) {
        return allFunctions.stream().filter(x -> targetNames.contains(x.label.replace("this/", ""))).map(x -> x.label.replace("this/", ""));
    }

    static boolean containsFuncs(Collection<Func> allFunctions, Set<String> targetNames) {
        return allFunctions.stream().map(x -> x.label.replace("this/", "")).collect(Collectors.toSet()).containsAll(targetNames);
    }

    static boolean containsCommand(Collection<Command> commands, String targetName) {
        return commands.stream().map(x -> x.label.replace("this/", "")).collect(Collectors.toSet()).contains(targetName);
    }

    static Optional<Command> getCommand(Collection<Command> commands, String targetName) {
        return commands.stream().filter(x -> x.label.replace("this/", "").contains(targetName.replace("this/", ""))).findFirst();
    }

    static String stripThisFromLabel(String str) {
        if (str != null)
            str = str.replace("this/", "");
        return str;
    }

    static Map<String, Set<String>> getFunctionWithPositions(CompModule module, List<Pos> positions) {
        Map<String, Set<String>> result = new HashMap<>();

        module.getAllCommands().forEach(cmd -> {
            if (posIn(cmd.pos, positions)) {
                Set<String> targets = FunctionSearch
                        .search(f -> f.pos.sameFile(cmd.pos) && notPosIn(f.pos, positions), cmd.formula)
                        .stream()
                        .map(f -> f.label)
                        .map(AlloyUtil::stripThisFromLabel)
                        .collect(Collectors.toSet());
                if (!targets.isEmpty())
                    result.put(stripThisFromLabel(cmd.label), targets);
            }
        });
        return result;
    }

    static boolean posIn(Pos pos, Collection<Pos> collection) {
        return collection.stream().anyMatch(p -> p.contains(pos));
    }

    static boolean notPosIn(Pos pos, Collection<Pos> collection) {
        return collection.stream().noneMatch(p -> p.contains(pos));
    }

    static List<Pos> offsetsToPos(String code, List<Integer> offsets) {
        return offsetsToPos("alloy_heredoc.als", code, offsets);
    }

    static List<Pos> offsetsToPos(String filename, String code, List<Integer> offsets) {
        List<Integer> integers = offsets.stream().sorted().distinct().toList();
        Pattern p = Pattern.compile("\\n");
        Matcher m = p.matcher(code);

        List<Pos> result = new ArrayList<>(integers.size());

        int line = 1;
        int curr = 0;
        while (m.find() && curr < integers.size()) {
            int t0 = m.end();
            for (; curr < integers.size() && integers.get(curr) < t0; curr++)
                result.add(new Pos(filename, integers.get(curr) - t0, line));
            line++;
        }

        return result;
    }

    static String posAsStringTuple(Pos p) {
        return "(" + p.x + "," + p.y + (p.x2 != p.x || p.y2 != p.y ? "," + p.x2 + "," + p.y2 : "") + ")";
    }
}
