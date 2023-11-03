package pt.haslab.specassistant.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.aggregation.Transition;
import pt.haslab.specassistant.data.models.Edge;
import pt.haslab.specassistant.data.models.Graph;
import pt.haslab.specassistant.data.models.Node;
import pt.haslab.specassistant.data.policy.PolicyOption;
import pt.haslab.specassistant.data.policy.PolicyRule;
import pt.haslab.specassistant.repositories.EdgeRepository;
import pt.haslab.specassistant.repositories.NodeRepository;
import pt.haslab.specassistant.util.Ordered;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class PolicyManager {

    @Inject
    NodeRepository nodeRepo;
    @Inject
    EdgeRepository edgeRepo;

    public void computePolicyForGraph(ObjectId graph_id, PolicyOption policyOption) {
        Graph.removeAllPolicyStats(graph_id);
        clearPolicy(graph_id);
        policyOption.getRule().normalizeByGraph(graph_id);
        long t = System.nanoTime();
        doDijkstra(graph_id, policyOption);
        Graph.registerPolicy(graph_id, System.nanoTime() - t, nodeRepo.getTotalVisitsFromScoredGraph(graph_id));

    }

    private void clearPolicy(ObjectId graph_id) {
        nodeRepo.clearPolicy(graph_id);
        edgeRepo.clearPolicyFromGraph(graph_id);
    }

    public void doDijkstra(ObjectId graph_id, PolicyOption policyOption) {
        Collection<DijkstraNode> batch = nodeRepo.streamByGraphIdAndValid(graph_id).map(n -> DijkstraNode.builder().node(n).score(policyOption.getIdentity()).build()).toList();

        Function<List<Double>, Double> choosenOne = switch (policyOption.getObjective()) {
            case MIN -> Collections::min;
            case MAX -> Collections::max;
        };

        BinaryOperator<DijkstraNode> best = switch (policyOption.getObjective()) {
            case MIN -> Ordered::min;
            case MAX -> Ordered::max;
        };

        while (!batch.isEmpty()) {
            List<Double> scores = batch.stream().map(x -> x.score).toList();
            Double target = choosenOne.apply(scores);
            Map<Boolean, List<DijkstraNode>> nodes = batch.stream().collect(Collectors.groupingBy(x -> x.score <= target, Collectors.toList()));
            ConcurrentMap<ObjectId, DijkstraNode> map = nodes.getOrDefault(false, List.of()).stream().collect(Collectors.toConcurrentMap(x -> x.node.id, x -> x));
            batch = nodes.get(true)
                    .stream().parallel()
                    .peek(DijkstraNode::save)
                    .flatMap(n -> edgeRepo.streamTransitionsByDestinationScoreNull(n.node, target).parallel().map(t -> n.apply(t, policyOption.getRule())))
                    .collect(Collectors.toConcurrentMap(x -> x.node.id, x -> x, best, () -> map)).values();
        }
    }

    @Data
    @Builder
    @RequiredArgsConstructor
    @AllArgsConstructor
    static class DijkstraNode implements Ordered<DijkstraNode> {
        Node node;
        @Builder.Default
        Edge parent_edge = null;
        @Builder.Default
        Double score = 0.0;
        @Builder.Default
        int distance = 0;

        public void save() {
            node.setScore(score);
            node.setHopDistance(distance);
            node.update();
            if (parent_edge != null) {
                parent_edge.setPolicy(true);
                parent_edge.update();
            }
        }

        public DijkstraNode apply(Transition t, PolicyRule r) {
            return new DijkstraNode(t.getFrom(), t.getEdge(), r.apply(t), distance + 1);
        }

        @Override
        public int compareTo(DijkstraNode o) {
            return Double.compare(score, o.score);
        }
    }


}