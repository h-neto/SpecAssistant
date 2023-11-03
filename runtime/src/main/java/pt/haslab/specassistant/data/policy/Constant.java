package pt.haslab.specassistant.data.policy;


import lombok.*;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.aggregation.Transition;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Constant extends PolicyRule {

    Double value;

    public static Constant of(Double value) {
        return new Constant(value);
    }

    @Override
    public void normalizeByGraph(ObjectId objectId) {
    }

    @Override
    public Double apply(Transition transition) {
        return value;
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
