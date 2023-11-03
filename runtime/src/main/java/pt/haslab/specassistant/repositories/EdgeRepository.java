package pt.haslab.specassistant.repositories;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Synchronized;
import org.bson.Document;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.aggregation.Transition;
import pt.haslab.specassistant.data.models.Edge;
import pt.haslab.specassistant.data.models.Node;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class EdgeRepository implements PanacheMongoRepository<Edge> {

    @Synchronized
    public synchronized void incrementOrCreate(ObjectId graph_id, ObjectId origin, ObjectId destination) {
        if (update(new Document("$inc", new Document("count", 1))).where(new Document("origin", origin).append("destination", destination).append("graph_id", graph_id)) <= 0)
            Edge.builder().graph_id(graph_id).origin(origin).destination(destination).count(1).build().persistOrUpdate();
    }

    public Optional<Edge> policyByOriginNode(ObjectId origin_id) {
        return find(new Document("origin", origin_id).append("policy", true)).firstResultOptional();
    }

    public void deleteByGraphId(ObjectId graph_id) {
        delete(new Document("graph_id", graph_id));
    }

    public void deleteByOriginIn(Collection<ObjectId> origin) {
        delete(new Document("origin", new Document("$in", origin)));
    }

    public Stream<Edge> streamByGraphId(ObjectId graphId) {
        return find(new Document("graph_id", graphId)).stream();
    }

    public void clearPolicyFromGraph(ObjectId graph_id) {
        update(new Document("$unset", new Document("policy", null))).where("graph_id", graph_id);
    }

    public Stream<Transition> streamTransitionsByDestinationScoreNull(Node destination, Double min) {
        return StreamSupport.stream(mongoCollection().aggregate(List.of(
                new Document("$match", new Document("destination", destination.id)),
                new Document("$replaceRoot", new Document("newRoot", new Document("edge", "$$ROOT"))),
                new Document("$lookup", new Document("from", "Node").append("localField", "edge.origin").append("foreignField", "_id").append("as", "from")),
                new Document("$unwind", "$from"),
                new Document("$match", new Document(new Document("from.score",  null)))
        ), Transition.class).spliterator(), false).peek(x -> x.setTo(destination));
    }

    public Stream<Edge> streamByOriginIn(List<ObjectId> origin) {
        return find(new Document("origin", new Document("$in", origin))).stream();
    }
}
