package pt.haslab.specassistant.treeedit;

import at.unisalzburg.dbresearch.apted.distance.APTED;
import at.unisalzburg.dbresearch.apted.node.NodeIndexer;
import edu.mit.csail.sdg.ast.Expr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ASTEditDiff extends APTED<EditData, EditDataCostModel> {
    public ASTEditDiff() {
        super(new EditDataCostModel());
    }


    public ASTEditDiff initFrom(Expr from, Expr to) {
        this.init(ExprToEditData.parseOrDefault(from), ExprToEditData.parseOrDefault(to));
        return this;
    }

    public List<EditOperation> getEditOperations() {
        List<EditOperation> result = new ArrayList<>();
        List<int[]> mapping = new ArrayList<>(this.computeEditMapping());

        Map<Integer, Integer> renameIndexes = mapping.stream()
                .filter(x -> x[0] != 0 && x[1] != 0)
                .collect(Collectors.toMap(x -> x[1] - 1, x -> x[0] - 1));

        NodeIndexer<EditData, EditDataCostModel> it1 = this.getIndexer1();
        NodeIndexer<EditData, EditDataCostModel> it2 = this.getIndexer2();

        mapping.sort((a, b) -> {
            if (a[0] == 0 || b[0] == 0) return a[1] - b[1];
            if (a[1] == 0 || b[1] == 0) return a[0] - b[0];
            return a[0] == b[0] ? a[1] - b[1] : a[0] - b[0];
        });
        Collections.reverse(mapping);
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
