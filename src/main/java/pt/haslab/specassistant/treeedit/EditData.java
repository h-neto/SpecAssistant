package pt.haslab.specassistant.treeedit;

import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.ast.Expr;
import pt.haslab.alloyaddons.ExprNodeEquals;
import pt.haslab.alloyaddons.ExprNodeStringify;
import pt.haslab.alloyaddons.Util;

public record EditData(Expr node, Pos position) {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EditData editData = (EditData) o;

        return ExprNodeEquals.equals(node, editData.node);
    }

    @Override
    public String toString() {
        return "{\"node\"=" + ExprNodeStringify.stringify(node) + ",\"position\"=\"" + Util.posAsStringTuple(position) + "\"}";
    }
}
