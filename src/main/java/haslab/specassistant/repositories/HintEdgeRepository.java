package pt.haslab.specassistant.repositories;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.Document;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.models.HintEdge;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class HintEdgeRepository implements PanacheMongoRepository<HintEdge> {

    public Optional<HintEdge> findByOriginAndDestination(ObjectId origin, ObjectId destination) {
        return find(new Document("origin", origin).append("destination", destination)).firstResultOptional();
    }

    public Optional<HintEdge> findBestScoredByOriginNode(ObjectId origin_id) {
        return find(new Document("origin", origin_id).append("score", new Document("$ne", null)), new Document("score", 1)).firstResultOptional();
    }

    public Stream<HintEdge> streamByDestinationNodeIdAndAllScoreGT(ObjectId destination, Double score) {
        return find(new Document("$or", List.of(new Document("score", new Document("$gt", score)), new Document("score", null))).append("destination", destination).append("origin", new Document("$ne", destination))).stream();
    }

    public void deleteByGraphId(ObjectId graph_id) {
        delete(new Document("graph_id", graph_id));
    }

    public Stream<HintEdge> streamByGraphId(ObjectId graphId) {
        return find(new Document("graph_id", graphId)).stream();
    }

    public void deleteByScoreNull(ObjectId graph_id) {
        delete(new Document("score", null).append("graph_id", graph_id));
    }
}
