package pt.haslab.specassistant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;
import pt.haslab.specassistant.data.models.HintEdge;
import pt.haslab.specassistant.data.models.HintGraph;
import pt.haslab.specassistant.data.models.HintNode;
import pt.haslab.specassistant.repositories.HintEdgeRepository;
import pt.haslab.specassistant.repositories.HintNodeRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@ApplicationScoped
public class PolicyManager {

    private static final Logger LOG = Logger.getLogger(PolicyManager.class);
    @Inject
    HintNodeRepository nodeRepo;
    @Inject
    HintEdgeRepository edgeRepo;


    public void computePolicyForGraph(ObjectId graph_id) {
        HintGraph.removeAllPolicyStats(graph_id);
        long t = System.nanoTime();
        Collection<PolicyContext> batch = nodeRepo.streamByGraphIdAndValidTrue(graph_id).map(PolicyContext::init).toList();

        while (!batch.isEmpty()) {
            PolicyContext targetScore = Collections.min(batch);

            Map<Boolean, List<PolicyContext>> targetIds = batch.stream().collect(Collectors.partitioningBy(x -> x.compareTo(targetScore) <= 0));

            try {
                List<CompletableFuture<List<PolicyContext>>> actionPool = targetIds.get(true)
                        .stream()
                        .peek(PolicyContext::apply)
                        .map(x -> CompletableFuture.supplyAsync(() ->
                                edgeRepo.streamByDestinationNodeIdAndAllScoreGT(x.nodeId(), x.score)
                                        .map(y -> x.nextContext(y, nodeRepo.findById(y.origin))).filter(Objects::nonNull).toList())).toList();

                CompletableFuture.allOf(actionPool.toArray(CompletableFuture[]::new)).get();

                List<List<PolicyContext>> result = new ArrayList<>();
                result.add(targetIds.get(false));
                for (CompletableFuture<List<PolicyContext>> l : actionPool) {
                    result.add(l.get());
                }

                batch = List.copyOf(result.stream().flatMap(Collection::stream).collect(Collectors.toMap(PolicyContext::nodeId, x -> x, PolicyContext::min)).values());

            } catch (InterruptedException | ExecutionException e) {
                LOG.error(e);
            }
        }
        HintGraph.registerPolicyCalculationTime(graph_id, System.nanoTime() - t);
    }


    public static class PolicyContext implements Comparable<PolicyContext> {

        public static Double policyDiscount = 0.99;

        public HintNode node;
        public Double score;
        public int distance;

        public PolicyContext() {
        }

        PolicyContext(HintNode node) {
            this.node = node;
        }

        public static PolicyContext init(HintNode n) {
            PolicyContext result = new PolicyContext();
            result.node = n;
            result.score = 0.0;
            result.distance = 0;
            return result;
        }

        public static PolicyContext min(PolicyContext a, PolicyContext b) {
            if (a.compareTo(b) < 0) return a;
            return b;
        }

        public ObjectId nodeId() {
            return node.id;
        }

        public void apply() {
            this.node.score = this.score;
            this.node.persistOrUpdate();
        }

        public Double computeImminentCost(HintEdge action) {
            if (action.editDistance == null || action.hopDistance == null)
                return Double.MAX_VALUE / 2;
            return (double) (action.editDistance * action.hopDistance); // Imment score curve def
        }

        public Double computeActionProbability(HintEdge action) {
            if (this.node.leaves > 0)
                return (double) action.count / (double) this.node.leaves;
            return 0.0;
        }

        public Double applyBellmanEquation(HintEdge action, Double followUpScore) {
            return this.score = computeImminentCost(action) + policyDiscount * computeActionProbability(action) * followUpScore;
        }


        public PolicyContext nextContext(HintEdge action, HintNode previousState) {
            PolicyContext next = new PolicyContext(previousState);
            action.hopDistance = next.distance = distance + 1;
            action.score = next.applyBellmanEquation(action, this.score);
            action.update();
            return next;
        }

        @Override
        public int compareTo(PolicyContext o) {
            return this.score.compareTo(o.score);
        }

        @Override
        public String toString() {
            return "{%s, score=%s, distance=%d}".formatted(node, score, distance);
        }
    }

}
