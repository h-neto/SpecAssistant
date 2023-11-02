package pt.haslab.specassistant.runtime.services;

import edu.mit.csail.sdg.alloy4.ConstList;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.Sig;
import edu.mit.csail.sdg.parser.CompModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import pt.haslab.Repairer;
import pt.haslab.alloyaddons.AlloyUtil;
import pt.haslab.alloyaddons.ExprNormalizer;
import pt.haslab.alloyaddons.ExprStringify;
import pt.haslab.mutation.Candidate;
import pt.haslab.mutation.mutator.Mutator;
import pt.haslab.specassistant.runtime.data.aggregation.Transition;
import pt.haslab.specassistant.runtime.data.models.Challenge;
import pt.haslab.specassistant.runtime.data.models.Edge;
import pt.haslab.specassistant.runtime.data.models.Node;
import pt.haslab.specassistant.runtime.data.transfer.HintMsg;
import pt.haslab.specassistant.runtime.repositories.ChallengeRepository;
import pt.haslab.specassistant.runtime.repositories.EdgeRepository;
import pt.haslab.specassistant.runtime.repositories.ModelRepository;
import pt.haslab.specassistant.runtime.repositories.NodeRepository;
import pt.haslab.specassistant.runtime.services.treeedit.ASTEditDiff;
import pt.haslab.specassistant.runtime.util.DataUtil;

import java.util.*;
import java.util.stream.Collectors;

import static pt.haslab.specassistant.runtime.data.models.Node.formulaExprToString;
import static pt.haslab.specassistant.runtime.util.DataUtil.getCombinations;

@ApplicationScoped
public class HintGenerator {

    @Inject
    Logger log;

    @ConfigProperty(name = "hint.mutations", defaultValue = "true")
    boolean mutationsEnabled;

    @Inject
    ModelRepository modelRepo;
    @Inject
    NodeRepository nodeRepo;
    @Inject
    EdgeRepository edgeRepo;
    @Inject
    ChallengeRepository challengeRepo;

    public static <ID> List<Map<ID, Candidate>> mkAllMutatedFormula(Map<ID, Expr> formula, ConstList<Sig> sigs, int maxDepth) {
        Map<ID, Candidate> unchanged = new HashMap<>();
        List<Map.Entry<ID, List<Candidate>>> changed = new ArrayList<>();

        formula.forEach((target, expr) -> {
            List<Candidate> mutations = Repairer.getValidCandidates(expr, sigs, maxDepth);

            if (mutations.isEmpty()) {
                Candidate c = Candidate.empty();
                c.mutated = expr;
                unchanged.put(target, c);
            } else
                changed.add(Map.entry(target, mutations));
        });
        if (changed.isEmpty())
            return List.of(unchanged);

        return getCombinations(unchanged, changed);
    }

    public static HintMsg firstHint(Map<String, Expr> formulaExpr, Map<String, Expr> otherFormulaExpr) {
        for (String s : formulaExpr.keySet()) {
            ASTEditDiff diff = new ASTEditDiff().initFrom(formulaExpr.get(s), otherFormulaExpr.get(s));
            diff.computeEditDistance();
            return diff.getFirstEditOperation().getHintMessage();
        }
        return null;
    }

    public Optional<Transition> formulaTransition(ObjectId graph_id, Map<String, String> formula) {
        Optional<Node> node_opt = nodeRepo.findByGraphIdAndFormula(graph_id, formula);

        if (node_opt.isPresent()) {
            Node origin_node = node_opt.orElseThrow();
            Optional<Edge> edge_opt = edgeRepo.policyByOriginNode(origin_node.id);
            if (edge_opt.isPresent()) {
                Edge edge = edge_opt.orElseThrow();
                Node destination = nodeRepo.findByIdOptional(edge.getDestination()).orElseThrow();
                return Optional.of(new Transition(origin_node, edge, destination));
            }
        }
        return Optional.empty();
    }

    public Optional<Transition> worldTransition(Challenge challenge, CompModule world) {
        Map<String, Expr> formulaExpr = Node.getNormalizedFormulaExprFrom(world, challenge.getTargetFunctions());
        Map<String, String> formula = formulaExprToString(formulaExpr);

        return formulaTransition(challenge.getGraph_id(), formula);
    }

    public Optional<HintMsg> hintWithGraph(Challenge challenge, CompModule world) {
        Map<String, Expr> formulaExpr = Node.getNormalizedFormulaExprFrom(world, challenge.getTargetFunctions());
        Map<String, String> formula = formulaExprToString(formulaExpr);

        return formulaTransition(challenge.getGraph_id(), formula).map(x -> firstHint(formulaExpr, x.getTo().getParsedFormula(world)));
    }


    public Optional<Node> mutatedNextState(Challenge challenge, CompModule world) {
        List<Map<String, String>> mutatedFormulas = getMutatedFormulas(challenge, world);

        return nodeRepo.findBestByGraphIdAndFormulaIn(challenge.getGraph_id(), mutatedFormulas);
    }

    private static List<Map<String, String>> getMutatedFormulas(Challenge challenge, CompModule world) {
        List<Map<String, Candidate>> candidateFormulas = mkAllMutatedFormula(Node.getFormulaExprFrom(world.getAllFunc().makeConstList(), challenge.getTargetFunctions()), world.getAllReachableSigs(), 1);
        return candidateFormulas.stream().map(m -> DataUtil.mapValues(m, f -> ExprStringify.stringify(ExprNormalizer.normalize(f.mutated)))).toList();
    }

    public Optional<HintMsg> hintWithMutation(Challenge challenge, CompModule world) {
        List<Map<String, Candidate>> candidateFormulas = mkAllMutatedFormula(Node.getFormulaExprFrom(world.getAllFunc().makeConstList(), challenge.getTargetFunctions()), world.getAllReachableSigs(), 1);

        List<Map<String, String>> mutatedFormulas = candidateFormulas.stream().map(m -> DataUtil.mapValues(m, f -> ExprStringify.stringify(ExprNormalizer.normalize(f.mutated)))).toList();

        Optional<Node> e = nodeRepo.findBestByGraphIdAndFormulaIn(challenge.getGraph_id(), mutatedFormulas);

        if (e.isPresent()) {
            Node n = e.orElseThrow();
            int target = mutatedFormulas.indexOf(n.getFormula());

            for (Candidate c : candidateFormulas.get(target).values()) {
                if (!c.mutators.isEmpty()) {
                    for (Mutator m : c.mutators) {
                        if (m.hint().isPresent())
                            return Optional.of(HintMsg.from(m.original.expr.pos(), m.hint().orElseThrow()));
                    }
                }
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings("unused")
    public Optional<HintMsg> getHint(String originId, String command_label, CompModule world) {
        String original_id = modelRepo.getOriginalById(originId);
        Challenge challenge = challengeRepo.findByModelIdAndCmdN(original_id, command_label).orElse(null);

        if (challenge == null) {
            log.debug("No challenge found for original=" + original_id + " && command_label=" + command_label);
            return Optional.empty();
        }

        Set<String> availableFuncs = AlloyUtil.streamFuncsNamesWithNames(world.getAllFunc().makeConstList(), challenge.getTargetFunctions()).collect(Collectors.toSet());
        if (!availableFuncs.containsAll(challenge.getTargetFunctions())) {
            log.debug("Some of the targeted functions are not contained within provided world, missing " + new HashSet<>(challenge.getTargetFunctions()).removeAll(availableFuncs));
            return Optional.empty();
        }

        Optional<HintMsg> result = hintWithGraph(challenge, world);

        if (result.isEmpty() && mutationsEnabled) {
            result = hintWithMutation(challenge, world);
        }

        return result;
    }
}
