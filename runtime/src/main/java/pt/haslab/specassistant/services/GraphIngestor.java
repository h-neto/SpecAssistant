package pt.haslab.specassistant.services;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.parser.CompModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;
import pt.haslab.alloyaddons.ExprComplexity;
import pt.haslab.alloyaddons.ParseUtil;
import pt.haslab.alloyaddons.UncheckedIOException;
import pt.haslab.specassistant.data.models.Challenge;
import pt.haslab.specassistant.data.models.Graph;
import pt.haslab.specassistant.data.models.Model;
import pt.haslab.specassistant.data.models.Node;
import pt.haslab.specassistant.repositories.ChallengeRepository;
import pt.haslab.specassistant.repositories.EdgeRepository;
import pt.haslab.specassistant.repositories.ModelRepository;
import pt.haslab.specassistant.repositories.NodeRepository;
import pt.haslab.specassistant.services.treeedit.ASTEditDiff;
import pt.haslab.specassistant.util.FutureUtil;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@ApplicationScoped
public class GraphIngestor {

    @Inject
    Logger log;

    @Inject
    ModelRepository modelRepo;
    @Inject
    NodeRepository nodeRepo;
    @Inject
    EdgeRepository edgeRepo;
    @Inject
    ChallengeRepository challengeRepo;

    /**
     * The abstract base BFS algorithm
     *
     * @param step        The action applied to every node according to its current context,
     *                    Returns the next context
     * @param modelGetter Function that gets thes subnodes of a model node
     * @param ctx         Current/Initial context
     * @param current     Current/Initial node
     * @param <C>         Context Class
     * @return CompletableFuture of the traversal job
     */
    public static <C> CompletableFuture<Void> abstractWalkModelTree(BiFunction<Model, C, C> step, Function<Model, Stream<Model>> modelGetter, C ctx, Model current) {
        return CompletableFuture.supplyAsync(() -> step.apply(current, ctx)).thenCompose(nextCtx -> FutureUtil.runEachAsync(modelGetter.apply(current), child -> abstractWalkModelTree(step, modelGetter, nextCtx, child)));
    }


    private static boolean testSpecModifications(CompModule original, CompModule target) {
        Set<String> root_modules = original.getAllReachableModules().makeConstList().stream().map(CompModule::path).collect(Collectors.toSet());
        return target.getAllReachableModules().makeConstList().stream().map(CompModule::path).anyMatch(x -> !root_modules.contains(x));
        //Missing Signature check
        //List<Sig> oSigs = original.getAllSigs().makeConstList();
        //List<Sig> tSigs = original.getAllSigs().makeConstList();
    }

    private Map<ObjectId, ObjectId> walkModelTreeStep(Predicate<Model> model_filter, Map<String, Challenge> cmdToChallenge, Predicate<CompModule> modifiedPred, Model current, Map<ObjectId, ObjectId> context) {
        try {
            if (model_filter.test(current) && current.isValidExecution() && cmdToChallenge.containsKey(current.getCmd_n())) {

                CompModule world = ParseUtil.parseModel(current.getCode());
                Challenge challenge = cmdToChallenge.get(current.getCmd_n());
                ObjectId contextId = challenge.id;
                ObjectId old_node_id = context.get(contextId);

                boolean modified = modifiedPred.test(world);

                if (challenge.isValidCommand(world, current.getCmd_i())) {
                    boolean valid = current.getSat() == 0;

                    Map<String, String> formula = Node.getNormalizedFormulaFrom(world.getAllFunc().makeConstList(), challenge.getTargetFunctions());

                    nodeRepo.incrementOrCreate(formula, valid, challenge.getGraph_id(), modified ? current.getId() : null);
                    ObjectId new_node_id = nodeRepo.findByGraphIdAndFormula(challenge.getGraph_id(), formula).orElseThrow().getId();

                    if (!old_node_id.equals(new_node_id)) { // No laces
                        nodeRepo.incrementLeaveById(old_node_id);
                        context = new HashMap<>(context); // Make a new context based on the previous one
                        context.put(contextId, new_node_id);
                        edgeRepo.incrementOrCreate(challenge.getGraph_id(), old_node_id, new_node_id);
                    }
                }
            }
        } catch (UncheckedIOException e) {
            log.warn(e);
        } catch (Err e) {
            log.warn("Error parsing model, skipping " + current.getId() + " : " + e.getMessage());
        }
        return context;
    }

    public CompletableFuture<Void> walkModelTree(Model root, Predicate<Model> model_filter, Collection<Challenge> challenges) {
        if (challenges.isEmpty())
            return CompletableFuture.completedFuture(null);

        CompModule world = ParseUtil.parseModel(root.getCode());

        Map<String, Challenge> cmdToChallenge = challenges.stream().collect(Collectors.toUnmodifiableMap(Challenge::getCmd_n, x -> x));
        Map<ObjectId, ObjectId> challengeToNodeId = new HashMap<>();

        cmdToChallenge.values().forEach(challenge -> {
                    Map<String, String> formula = Node.getNormalizedFormulaFrom(world.getAllFunc().makeConstList(), challenge.getTargetFunctions());
                    nodeRepo.incrementOrCreate(formula, false, challenge.getGraph_id(), null);
                    challengeToNodeId.put(challenge.id, nodeRepo.findByGraphIdAndFormula(challenge.getGraph_id(), formula).orElseThrow().getId());
                }
        );
        return abstractWalkModelTree(
                (m, ctx) -> walkModelTreeStep(model_filter, cmdToChallenge, w -> testSpecModifications(world, w), m, ctx),
                model -> modelRepo.streamByDerivationOfAndOriginal(model.getId(), model.getOriginal()),
                challengeToNodeId,
                root
        );
    }

    public CompletableFuture<Void> classifyAllEdges(CompModule world, ObjectId graph_id) {
        return FutureUtil.forEachAsync(edgeRepo.streamByGraphId(graph_id), e -> {
            Node destNode = nodeRepo.findById(e.getDestination());
            Node originNode = nodeRepo.findById(e.getOrigin());
            try {
                Map<String, Expr> peerParsed = destNode.getParsedFormula(world);
                Map<String, Expr> originParsed = originNode.getParsedFormula(world);

                e.setEditDistance(ASTEditDiff.getFormulaDistanceDiff(originParsed, peerParsed));
                e.update();
            } catch (IllegalStateException e1) {
                log.warn("Error in edge classification, editDistance will be set to infinity: " + e1.getClass().getSimpleName() + ":" + e1.getMessage());
            }
        });
    }

    public void trimDeparturesFromValidNodes(ObjectId graph_id) {
        List<ObjectId> nodes = nodeRepo.streamByGraphIdAndValid(graph_id).filter(x -> x.getLeaves() > 0).map(x -> x.id).peek(x -> nodeRepo.setLeaves(x, 0)).toList();
        edgeRepo.streamByOriginIn(nodes).forEach(x -> nodeRepo.addVisits(x.getDestination(), -x.getCount()));
        edgeRepo.deleteByOriginIn(nodes);
    }

    public void assignComplexityToGraphNodes(ObjectId graph_id, CompModule world) {
        FutureUtil.forEachAsync(nodeRepo.streamByGraphId(graph_id),
                n -> {
                    try {
                        n.setComplexity(n.getParsedFormula(world).values().stream().map(f -> {
                            ExprComplexity c = new ExprComplexity();
                            c.visitThis(f);
                            return c.getComplexity();
                        }).reduce(0.0, Double::sum));
                        n.update();
                    } catch (IllegalStateException e1) {
                        log.warn("Error in node classification, complexity will be set to infinity: " + e1.getClass().getSimpleName() + ":" + e1.getMessage());
                    }
                }
        );
    }

    private CompletableFuture<Void> classify(CompModule base_world, ObjectId graph_id) {
        return classifyAllEdges(base_world, graph_id).thenRun(() -> assignComplexityToGraphNodes(graph_id, base_world)).thenRun(() -> trimDeparturesFromValidNodes(graph_id));
    }

    public CompletableFuture<Void> parseModelTree(String model_id, Predicate<Model> model_filter) {
        return parseModelTree(model_id, model_filter, challengeRepo.streamByModelId(model_id).collect(Collectors.toSet()));
    }

    public CompletableFuture<Void> parseModelTree(String model_id, Predicate<Model> model_filter, Collection<Challenge> challenges) {
        Model model = modelRepo.findById(model_id);

        CompModule base_world = ParseUtil.parseModel(model.getCode());

        AtomicLong parsingTime = new AtomicLong(System.nanoTime());
        Map<ObjectId, Long> count = challenges.stream().map(Challenge::getGraph_id).collect(Collectors.toMap(x -> x, x -> nodeRepo.getTotalVisitsFromGraph(x)));

        return walkModelTree(model, model_filter, challenges)
                .thenRun(() -> parsingTime.updateAndGet(l -> System.nanoTime() - l))
                .thenCompose(nil -> FutureUtil.runEachAsync(challenges,
                        ex -> {
                            long st = System.nanoTime();
                            AtomicLong local_count = new AtomicLong();
                            return classify(base_world, ex.getGraph_id())
                                    .thenRun(() -> local_count.set(nodeRepo.getTotalVisitsFromGraph(ex.getGraph_id())))
                                    .thenRun(() -> Graph.registerParsing(ex.getGraph_id(), model_id, local_count.get() - count.getOrDefault(ex.getGraph_id(), 0L), parsingTime.get() + System.nanoTime() - st));
                        }))
                .whenComplete(FutureUtil.logTrace(log, "Finished parsing model " + model_id));
    }


}
