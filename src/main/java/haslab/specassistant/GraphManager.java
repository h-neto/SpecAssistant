package pt.haslab.specassistant;


import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.parser.CompModule;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import pt.haslab.alloyaddons.Util;
import pt.haslab.specassistant.data.models.HintExercise;
import pt.haslab.specassistant.data.models.HintGraph;
import pt.haslab.specassistant.data.models.Model;
import pt.haslab.specassistant.repositories.HintEdgeRepository;
import pt.haslab.specassistant.repositories.HintExerciseRepository;
import pt.haslab.specassistant.repositories.HintNodeRepository;
import pt.haslab.specassistant.repositories.ModelRepository;

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
    HintNodeRepository nodeRepo;
    @Inject
    HintEdgeRepository edgeRepo;
    @Inject
    HintExerciseRepository exerciseRepo;

    public void cleanGraph(ObjectId graph_id) {
        nodeRepo.deleteByGraphId(graph_id);
        edgeRepo.deleteByGraphId(graph_id);
    }

    public void deleteGraph(ObjectId graph_id) {
        cleanGraph(graph_id);
        exerciseRepo.deleteByGraphId(graph_id);
        HintGraph.deleteById(graph_id);
    }

    public void dropEverything() {
        HintGraph.deleteAll();
        nodeRepo.deleteAll();
        edgeRepo.deleteAll();
        exerciseRepo.deleteAll();
    }

    public void debloatGraph(ObjectId graph_id) {
        edgeRepo.deleteByScoreNull(graph_id);
        nodeRepo.deleteByScoreNull(graph_id);
        HintGraph.setPolicySubmissionCount(graph_id, nodeRepo.getTotalVisitsFromGraph(graph_id));
    }

    public boolean generateExercise(ObjectId graph_id, String model_id, Integer secretCommandCount, String cmd_n, Set<String> targetFunctions) {
        if (exerciseRepo.notExistsModelIdAndCmdN(model_id, cmd_n)) {
            exerciseRepo.persistOrUpdate(new HintExercise(model_id, graph_id, secretCommandCount, cmd_n, targetFunctions));
            return true;
        }
        return false;
    }

    public void generateExercisesWithGraphIdFromSecrets(Function<String, ObjectId> commandToGraphId, String model_id) {
        Model m = modelRepo.findByIdOptional(model_id).orElseThrow();
        CompModule world = Util.parseModel(m.code);
        List<Pos> secretPositions = secretPos(world.path, m.code);

        Map<String, Set<String>> targets = Util.getFunctionWithPositions(world, secretPositions);
        Integer cmdCount = targets.size();

        exerciseRepo.persistOrUpdate(targets.entrySet().stream().map(x -> new HintExercise(model_id, commandToGraphId.apply(x.getKey()), cmdCount, x.getKey(), x.getValue())));
    }

    public Set<ObjectId> getModelGraphs(String modelid) {
        return exerciseRepo.streamByModelId(modelid).map(x -> x.graph_id).collect(Collectors.toSet());
    }


    public void deleteExerciseByModelIDs(List<String> ids, boolean cascadeToGraphs) {
        if (!cascadeToGraphs) {
            exerciseRepo.deleteByModelIdIn(ids);
        } else {
            Set<ObjectId> graph_ids = exerciseRepo.streamByModelIdIn(ids).map(x -> x.graph_id).collect(Collectors.toSet());
            exerciseRepo.deleteByModelIdIn(ids);
            graph_ids.forEach(graph_id -> {
                if (!exerciseRepo.containsGraph(graph_id))
                    deleteGraph(graph_id);
            });
        }
    }

}
