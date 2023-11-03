package pt.haslab.specassistant.services;


import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.parser.CompModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import pt.haslab.alloyaddons.AlloyUtil;
import pt.haslab.alloyaddons.ParseUtil;
import pt.haslab.specassistant.data.models.Challenge;
import pt.haslab.specassistant.data.models.Graph;
import pt.haslab.specassistant.data.models.Model;
import pt.haslab.specassistant.repositories.ChallengeRepository;
import pt.haslab.specassistant.repositories.EdgeRepository;
import pt.haslab.specassistant.repositories.ModelRepository;
import pt.haslab.specassistant.repositories.NodeRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pt.haslab.specassistant.util.Text.secretPos;

@ApplicationScoped
public class GraphManager {
    @Inject
    ModelRepository modelRepo;
    @Inject
    NodeRepository nodeRepo;
    @Inject
    EdgeRepository edgeRepo;
    @Inject
    ChallengeRepository challengeRepo;

    public void cleanGraph(ObjectId graph_id) {
        nodeRepo.deleteByGraphId(graph_id);
        edgeRepo.deleteByGraphId(graph_id);
    }

    public void deleteGraph(ObjectId graph_id) {
        cleanGraph(graph_id);
        challengeRepo.deleteByGraphId(graph_id);
        Graph.deleteById(graph_id);
    }

    public void dropEverything() {
        Graph.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        challengeRepo.deleteAll();
    }

    public void generateChallenge(ObjectId graph_id, String model_id, Integer secretCommandCount, String cmd_n, Set<String> targetFunctions) {
        if (challengeRepo.notExistsModelIdAndCmdN(model_id, cmd_n)) {
            challengeRepo.persistOrUpdate(new Challenge(model_id, graph_id, secretCommandCount, cmd_n, targetFunctions));
        }
    }

    public void generateChallengesWithGraphIdFromSecrets(Function<String, ObjectId> commandToGraphId, String model_id) {
        Model m = modelRepo.findByIdOptional(model_id).orElseThrow();
        CompModule world = ParseUtil.parseModel(m.getCode());
        List<Pos> secretPositions = secretPos(world.path, m.getCode());

        Map<String, Set<String>> targets = AlloyUtil.getFunctionWithPositions(world, secretPositions);
        Integer cmdCount = targets.size();

        challengeRepo.persistOrUpdate(targets.entrySet().stream().map(x -> new Challenge(model_id, commandToGraphId.apply(x.getKey()), cmdCount, x.getKey(), x.getValue())));
    }

    public Collection<String> parseSecretFunctionNames(String model_id) {
        Model m = modelRepo.findByIdOptional(model_id).orElseThrow();
        CompModule world = ParseUtil.parseModel(m.getCode());
        List<Pos> secretPositions = secretPos(world.path, m.getCode());

        return AlloyUtil.getFunctionWithPositions(world, secretPositions).keySet();
    }

    public Set<ObjectId> getModelGraphs(String modelid) {
        return challengeRepo.streamByModelId(modelid).map(Challenge::getGraph_id).collect(Collectors.toSet());
    }

    public void deleteAllGraphStructures() {
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
    }

    public void deleteChallengesByModelIDs(List<String> ids, boolean cascadeToGraphs) {
        if (!cascadeToGraphs) {
            challengeRepo.deleteByModelIdIn(ids);
        } else {
            Set<ObjectId> graph_ids = challengeRepo.streamByModelIdIn(ids).map(Challenge::getGraph_id).collect(Collectors.toSet());
            challengeRepo.deleteByModelIdIn(ids);
            graph_ids.forEach(graph_id -> {
                if (!challengeRepo.containsGraph(graph_id))
                    deleteGraph(graph_id);
            });
        }
    }

}
