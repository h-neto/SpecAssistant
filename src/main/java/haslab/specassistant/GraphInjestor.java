package pt.haslab.specassistant;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorSyntax;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.parser.CompModule;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;
import pt.haslab.alloyaddons.Util;
import pt.haslab.alloyaddons.exceptions.UncheckedIOException;
import pt.haslab.specassistant.data.models.*;
import pt.haslab.specassistant.repositories.HintEdgeRepository;
import pt.haslab.specassistant.repositories.HintExerciseRepository;
import pt.haslab.specassistant.repositories.HintNodeRepository;
import pt.haslab.specassistant.repositories.ModelRepository;
import pt.haslab.specassistant.treeedit.ASTEditDiff;
import pt.haslab.specassistant.util.Static;
import pt.haslab.specassistant.util.Text;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@ApplicationScoped
public class GraphInjestor {

    private static final Logger LOG = Logger.getLogger(GraphInjestor.class);

    @Inject
    ModelRepository modelRepo;
    @Inject
    HintNodeRepository nodeRepo;
    @Inject
    HintEdgeRepository edgeRepo;
    @Inject
    HintExerciseRepository exerciseRepo;


    private static <Context> CompletableFuture<Void> walkModelTree(BiFunction<Model, Context, Context> step, Function<Model, Stream<Model>> modelGetter, Context ctx, Model current) {
        return CompletableFuture.supplyAsync(() -> step.apply(current, ctx)).thenCompose(updatedContext -> CompletableFuture.allOf((modelGetter.apply(current).map(child -> walkModelTree(step, modelGetter, updatedContext, child)).toArray(CompletableFuture[]::new))));
    }

    private static Map<String, ASTEditDiff> getFormulaMapDiff(Map<String, Expr> origin, Map<String, Expr> peer) {
        return Stream.of(origin.keySet(), peer.keySet())
                .flatMap(Collection::stream)
                .collect(Collectors.toSet())
                .stream()
                .collect(Collectors.toMap(key -> key, key -> new ASTEditDiff().initFrom(origin.get(key), peer.get(key))));
    }

    public synchronized ObjectId incrementOrCreateNode(Map<String, String> formula, Boolean valid, ObjectId graph_id, String witness) {
        HintNode res = nodeRepo.findByGraphIdAndFormula(graph_id, formula).orElseGet(() -> HintNode.create(graph_id, formula, valid, witness)).visit();
        res.persistOrUpdate();
        return res.id;
    }

    public synchronized void incrementOrCreateEdge(ObjectId graph_id, ObjectId origin, ObjectId destination) {
        edgeRepo.findByOriginAndDestination(origin, destination).orElseGet(() -> HintEdge.createEmpty(graph_id, origin, destination)).visit().persistOrUpdate();
    }

    public HintEdge computeDifferences(HintEdge edge, CompModule world, Function<ObjectId, HintNode> nodeGetter) {
        try {
            HintNode originN = nodeGetter.apply(edge.origin);
            HintNode originD = nodeGetter.apply(edge.destination);
            Map<String, Expr> originParsed = originN.getParsedFormula(Optional.ofNullable(originN.witness).map(Model::getWorld).orElse(world));
            Map<String, Expr> peerParsed = originD.getParsedFormula(Optional.ofNullable(originD.witness).map(Model::getWorld).orElse(world));

            edge.editDistance = getFormulaMapDiff(originParsed, peerParsed).values().stream().map(ASTEditDiff::computeEditDistance).reduce(0.0f, Float::sum);
        } catch (ErrorSyntax e) {
            Log.warn("SYNTAX ERROR WHILE PARSING FORMULA: " + e.getMessage());
        } catch (Err e) {
            Log.warn("ALLOY ERROR WHILE PARSING FORMULA: " + e.getMessage());
        } catch (UncheckedIOException e) {
            Log.error("IO ERROR WHILE PARSING FORMULA: " + e.getMessage());
        }
        return edge;
    }

    public CompletableFuture<Void> classifyAllEdges(CompModule world, ObjectId graph_id) {
        return CompletableFuture.allOf(
                edgeRepo.streamByGraphId(graph_id)
                        .map(e -> CompletableFuture.runAsync(() -> computeDifferences(e, world, nodeRepo::findById).update()))
                        .toArray(CompletableFuture[]::new)
        );
    }

    public CompletableFuture<Void> walkModelTree(Model root) {
        return walkModelTree(root, null);
    }

    public CompletableFuture<Void> walkModelTree(Model root, Predicate<LocalDateTime> dateFilter) {
        Map<String, HintExercise> cmdToExercise = exerciseRepo.streamByModelId(root.id).collect(Collectors.toUnmodifiableMap(x -> x.cmd_n, x -> x));

        if (cmdToExercise.isEmpty())
            return CompletableFuture.completedFuture(null);

        Map<ObjectId, ObjectId> initCtx = new HashMap<>();
        CompModule world = Util.parseModel(root.code);

        Set<String> modules = world.getAllReachableModules().makeConstList().stream().map(CompModule::path).collect(Collectors.toSet());

        cmdToExercise.values().forEach(exercise -> {
            Map<String, String> formula = HintNode.getNormalizedFormulaFrom(world.getAllFunc().makeConstList(), exercise.targetFunctions);
            initCtx.put(exercise.id, incrementOrCreateNode(formula, false, exercise.graph_id, null));
        });
        Function<Model, Stream<Model>> modelGetter;
        if (dateFilter == null)
            modelGetter = model -> modelRepo.streamByDerivationOfAndOriginal(model.id, model.original);
        else {
            modelGetter = model -> modelRepo.streamByDerivationOfAndOriginal(model.id, model.original).filter(x -> dateFilter.test(Text.parseDate(x.time)));
        }
        return walkModelTree((m, ctx) -> walkModelTreeStep(cmdToExercise, modules, m, ctx), modelGetter, initCtx, root);
    }

    private Map<ObjectId, ObjectId> walkModelTreeStep(Map<String, HintExercise> cmdToExercise, Set<String> root_modules, Model current, Map<ObjectId, ObjectId> context) {
        try {
            if (current.sat != null && current.sat >= 0 && cmdToExercise.containsKey(current.cmd_n)) {

                CompModule world = Util.parseModel(current.code);
                HintExercise exercise = cmdToExercise.get(current.cmd_n);
                HintGraph.incrementParsingCount(exercise.graph_id);
                ObjectId contextId = exercise.id;
                ObjectId old_node_id = context.get(contextId);

                boolean modified = world.getAllReachableModules().makeConstList().stream().map(CompModule::path).anyMatch(x -> !root_modules.contains(x));

                // If the command index is above or equal to the first "secret" index
                // (meteor currently places secrets as the last defined predicates)
                if (current.cmd_i >= world.getAllCommands().size() - exercise.secret_cmd_count) {
                    boolean valid = current.sat == 0;

                    Map<String, String> formula = HintNode.getNormalizedFormulaFrom(world.getAllFunc().makeConstList(), exercise.targetFunctions);

                    ObjectId new_node_id = incrementOrCreateNode(formula, valid, exercise.graph_id, modified ? current.id : null);

                    if (!old_node_id.equals(new_node_id)) { // No laces
                        nodeRepo.incrementLeaveById(old_node_id);
                        context = new HashMap<>(context); // Make a new context based on the previous one
                        context.put(contextId, new_node_id);
                        incrementOrCreateEdge(exercise.graph_id, old_node_id, new_node_id);
                    }
                }
            }
        } catch (UncheckedIOException e) {
            LOG.warn(e);
        }
        return context;
    }

    public CompletableFuture<Void> parseModelTree(String model_id) {
        return parseModelTree(model_id, null);
    }


    public CompletableFuture<Void> parseModelTree(String model_id, Predicate<LocalDateTime> year_tester) {
        Model model = modelRepo.findById(model_id);
        CompModule base_world = Util.parseModel(model.code);

        CompletableFuture<Long> job;

        long start = System.nanoTime();

        if (year_tester == null) job = walkModelTree(model).thenApplyAsync(nil -> System.nanoTime() - start);
        else job = walkModelTree(model, year_tester).thenApplyAsync(nil -> System.nanoTime() - start);

        return job.thenComposeAsync(commonTime -> {
            List<CompletableFuture<Map.Entry<ObjectId, Long>>> classifications = exerciseRepo.streamByModelId(model.id).map(ex -> {
                long v = System.nanoTime();
                return classifyAllEdges(base_world, ex.graph_id).thenApply(nil -> Map.entry(ex.graph_id, System.nanoTime() - v));
            }).toList();

            return CompletableFuture.allOf(classifications.toArray(CompletableFuture[]::new))
                    .whenCompleteAsync((nil, error) -> {
                        if (error != null)
                            LOG.error(error);
                        Static.mergeFutureEntries(classifications).forEach((id, time) -> HintGraph.registerParsingTime(id, time + commonTime));
                    });
        });
    }
}
