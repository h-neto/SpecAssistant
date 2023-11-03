package pt.haslab.specassistant.data.policy;


import lombok.*;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.aggregation.Transition;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Binary extends PolicyRule {
    String operator;
    PolicyRule left, right;

    @Override
    public void normalizeByGraph(ObjectId objectId) {
        left.normalizeByGraph(objectId);
        right.normalizeByGraph(objectId);
    }

    @Override
    public Double apply(Transition transition) {
        return switch (operator) {
            case "*" -> left.apply(transition) * right.apply(transition);
            case "/" -> left.apply(transition) / right.apply(transition);
            case "+" -> left.apply(transition) + right.apply(transition);
            case "-" -> left.apply(transition) - right.apply(transition);
            case "max" -> Math.max(left.apply(transition), right.apply(transition));
            case "min" -> Math.min(left.apply(transition), right.apply(transition));
            default -> throw new UnkownOperationException("Unkown operation " + operator);
        };
    }

    public static Binary mult(PolicyRule left, PolicyRule right) {
        return new Binary("*", left, right);
    }

    public static Binary div(PolicyRule left, PolicyRule right) {
        return new Binary("/", left, right);
    }

    public static Binary sum(PolicyRule left, PolicyRule right) {
        return new Binary("+", left, right);
    }

    public static Binary sub(PolicyRule left, PolicyRule right) {
        return new Binary("-", left, right);
    }

    public static Binary max(PolicyRule left, PolicyRule right) {
        return new Binary("max", left, right);
    }

    public static Binary min(PolicyRule left, PolicyRule right) {
        return new Binary("min", left, right);
    }


    @Override
    public String toString() {
        return "(" + left + " " + operator + " " + right + ")";
    }


    public static PolicyRule scale(Double scalar, PolicyRule p) {
        return Binary.mult(new Constant(scalar), p);
    }

    public static PolicyRule sumOld(PolicyRule p) {
        return new Binary("+", p, Var.old());
    }

    public static PolicyRule oneMinusPrefTimesCost(PolicyRule pref, PolicyRule cost) {
        return sum(mult(sub(Constant.of(1.0), pref), cost), Var.old());
    }

}
