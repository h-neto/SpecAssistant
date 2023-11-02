package pt.haslab.specassistant.runtime.data.aggregation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import pt.haslab.specassistant.runtime.data.models.Edge;
import pt.haslab.specassistant.runtime.data.models.Node;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Transition {
    private Node from;
    private Edge edge;
    private Node to;
}
