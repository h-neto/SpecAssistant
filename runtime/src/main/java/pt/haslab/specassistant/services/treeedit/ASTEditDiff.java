package pt.haslab.specassistant.services.treeedit;

import edu.mit.csail.sdg.ast.Expr;
import pt.haslab.specassistant.services.treeedit.apted.distance.APTED;
import pt.haslab.specassistant.services.treeedit.apted.node.NodeIndexer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ASTEditDiff extends APTED<EditData, EditDataCostModel> {
    public ASTEditDiff() {
        super(new EditDataCostModel());
    }

    public static Map<String, ASTEditDiff> getFormulaMapDiff(Map<String, Expr> origin, Map<String, Expr> peer) {
        return Stream.of(origin.keySet(), peer.keySet())
                .flatMap(Collection::stream)
                .collect(Collectors.toSet())
                .stream()
                .collect(Collectors.toMap(key -> key, key -> new ASTEditDiff().initFrom(origin.get(key), peer.get(key))));
    }

    public static Float getFormulaDistanceDiff(Map<String, Expr> originParsed, Map<String, Expr> peerParsed) {
        return getFormulaMapDiff(originParsed, peerParsed).values().stream().map(ASTEditDiff::computeEditDistance).reduce(0.0f, Float::sum);
    }

    public ASTEditDiff initFrom(Expr from, Expr to) {
        this.init(ExprToEditData.parseOrDefault(from), ExprToEditData.parseOrDefault(to));
        return this;
    }

    public List<EditOperation> getEditOperations() {
        List<EditOperation> result = new ArrayList<>();
        List<int[]> mapping = this.computeEditMapping();

        Map<Integer, Integer> renameIndexes = mapping.stream()
                .filter(x -> x[0] != 0 && x[1] != 0)
                .collect(Collectors.toMap(x -> x[1] - 1, x -> x[0] - 1));

        NodeIndexer<EditData, EditDataCostModel> it1 = this.getIndexer1();
        NodeIndexer<EditData, EditDataCostModel> it2 = this.getIndexer2();

        mapping.forEach(e -> {
            if (e[0] != 0) {
                if (e[1] == 0) {
                    result.add(new EditOperation("delete", null, it1.preL_to_node[e[0] - 1].getNodeData()));
                } else {
                    EditData prev = it1.preL_to_node[e[0] - 1].getNodeData();
                    EditData next = it2.preL_to_node[e[1] - 1].getNodeData();
                    if (!prev.equals(next))
                        result.add(new EditOperation("rename", next, prev));
                }
            } else {
                EditData addition = it2.preL_to_node[e[1] - 1].getNodeData();
                EditData target = it1.preL_to_node[it1.parents.length - 1].getNodeData();
                int currentIt2 = e[1] - 1;
                while (currentIt2 >= 0) {
                    currentIt2 = it2.parents[currentIt2];
                    if (renameIndexes.containsKey(currentIt2)) {
                        target = it1.preL_to_node[renameIndexes.get(currentIt2)].getNodeData();
                        break;
                    }
                }
                result.add(new EditOperation("insert", addition, target));
            }
        });
        return result;
    }

    public EditOperation getFirstEditOperation() {
        try {
            return getEditOperations().get(0);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }
}
