package pt.haslab.specassistant.data.policy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.aggregation.Transition;
import pt.haslab.specassistant.data.models.Edge;
import pt.haslab.specassistant.data.models.Node;

import java.util.NoSuchElementException;
import java.util.function.Function;


@AllArgsConstructor
@RequiredArgsConstructor
public class Var extends PolicyRule {
    public static PolicyRule old() {
        return Var.of(Name.OLD);
    }

    public static Var ted() {
        return of(Name.TED);
    }

    public static Var complexity() {
        return of(Name.COMPLEXITY);
    }

    public static Var complexity_diff() {
        return of(Name.COMPLEXITY_DIFF);
    }

    public static Var arrivals() {
        return of(Name.ARRIVALS);
    }

    public static Var departures() {
        return of(Name.DEPARTURES);
    }

    public static Var visits() {
        return of(Name.VISITS);
    }

    @Override
    public String toString() {
        return var.toString();
    }

    @Getter
    final Name var;
    @JsonIgnore
    Normalizer normalizer;


    public static Var of(Name name) {
        return new Var(name);
    }

    @Override
    public Double apply(Transition transition) {
        try {
            double result = switch (var) {
                case OLD -> transition.getTo().getScore();
                case TED -> transition.getEdge().getEditDistance();
                case VISITS -> transition.getFrom().getVisits();
                case COMPLEXITY_DIFF -> Math.abs(transition.getFrom().getLeaves() - transition.getTo().getComplexity());
                case LEAVES -> transition.getFrom().getLeaves();
                case COMPLEXITY -> transition.getFrom().getComplexity();
                case DEPARTURES -> (double) transition.getEdge().getCount() / (double) transition.getFrom().getLeaves();
                case ARRIVALS -> (double) transition.getEdge().getCount() / (double) transition.getTo().getVisits();
            };
            if (normalizer != null)
                result = normalizer.apply(result);
            return result;
        } catch (NullPointerException e) {
            if (normalizer != null)
                return 1.0;
            else return Double.MAX_VALUE / 2;
        }
    }

    public void normalizeByGraph(ObjectId graph_id) {
        try {
            normalizer = switch (var) {
                case TED -> new Normalizer(
                        Edge.findByMax(graph_id, "editDistance").map(Edge::getEditDistance).orElseThrow(),
                        Edge.findByMin(graph_id, "editDistance").map(Edge::getEditDistance).orElseThrow()
                );
                case VISITS -> new Normalizer(
                        Node.findByMax(graph_id, "visits").map(Node::getVisits).orElseThrow(),
                        Node.findByMin(graph_id, "visits").map(Node::getVisits).orElseThrow()
                );
                case LEAVES -> new Normalizer(
                        Node.findByMax(graph_id, "leaves").map(Node::getLeaves).orElseThrow(),
                        Node.findByMin(graph_id, "leaves").map(Node::getLeaves).orElseThrow()
                );
                case COMPLEXITY -> new Normalizer(
                        Node.findByMax(graph_id, "complexity").map(Node::getComplexity).orElseThrow(),
                        Node.findByMin(graph_id, "complexity").map(Node::getComplexity).orElseThrow()
                );
                case COMPLEXITY_DIFF -> new Normalizer(
                        Math.abs(Node.findByMax(graph_id, "complexity").map(Node::getComplexity).orElseThrow()),
                        0.0
                );
                default -> null;
            };
        } catch (NoSuchElementException ignored) {
        }
    }


    public enum Name {
        OLD,
        TED,
        COMPLEXITY,
        COMPLEXITY_DIFF,
        LEAVES,
        VISITS,
        DEPARTURES,
        ARRIVALS
    }

    public static class Normalizer implements Function<Double, Double> {
        Double maxMinusMin, min;

        public Normalizer(Number max, Number min) {
            this.maxMinusMin = max.doubleValue() - min.doubleValue();
            this.min = min.doubleValue();
        }

        @Override
        public Double apply(Double value) {
            return (value - min) / maxMinusMin;
        }
    }
}
