package pt.haslab.specassistant.services.treeedit;

import pt.haslab.alloyaddons.ExprNodeEquals;
import pt.haslab.specassistant.services.treeedit.apted.costmodel.CostModel;
import pt.haslab.specassistant.services.treeedit.apted.node.Node;

public class EditDataCostModel implements CostModel<EditData> {

    @Override
    public float del(Node<EditData> n) {
        return 1.0f;
    }

    @Override
    public float ins(Node<EditData> n) {
        return 1.0f;
    }

    @Override
    public float ren(Node<EditData> n1, Node<EditData> n2) {
        return ExprNodeEquals.equals(n1.getNodeData().node(), n2.getNodeData().node()) ? 0.0f : 1.0f;
    }
}

