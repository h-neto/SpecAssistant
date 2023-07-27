package pt.haslab.specassistant.repositories;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.models.HintExercise;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class HintExerciseRepository implements PanacheMongoRepository<HintExercise> {

    public Stream<HintExercise> streamByModelId(String model_id) {
        return find("model_id = ?1", model_id).stream();
    }

    public Stream<HintExercise> streamByModelIdIn(List<String> model_id) {
        return find(new Document("model_id", new Document("$in", model_id))).stream();
    }

    public Optional<HintExercise> findByModelIdAndCmdN(String model_id, String cmd_n) {
        return find("model_id = ?1 and cmd_n = ?2", model_id, cmd_n).firstResultOptional();
    }

    public boolean notExistsModelIdAndCmdN(String model_id, String cmd_n) {
        return findByModelIdAndCmdN(model_id, cmd_n).isEmpty();
    }

    public void deleteByGraphId(ObjectId graph_id) {
        delete(new Document("graph_id", graph_id));
    }

    public void deleteByModelIdIn(List<String> model_id) {
        delete(new Document("model_id", new Document("$in", model_id)));
    }

    public boolean containsGraph(ObjectId graph_id) {
        return find(new Document("graph_id", graph_id)).firstResultOptional().isPresent();
    }
}
