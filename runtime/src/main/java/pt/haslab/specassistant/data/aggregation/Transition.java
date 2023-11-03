package pt.haslab.specassistant.data.aggregation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.haslab.specassistant.data.models.Edge;
import pt.haslab.specassistant.data.models.Node;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Transition {
    private Node from;
    private Edge edge;
    private Node to;
}
