package pt.haslab.specassistant.repositories;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.Synchronized;
import org.bson.Document;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.models.Node;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class NodeRepository implements PanacheMongoRepository<Node> {

    private static Document appendFormulaToDocument(Document graph_id, Map<String, String> formula) {
        Document query = graph_id;
        for (Map.Entry<String, String> entry : formula.entrySet()) {
            query = query.append("formula." + entry.getKey(), entry.getValue());
        }
        return query;
    }

    public Optional<Node> findByGraphIdAndFormula(ObjectId graph_id, Map<String, String> formula) {
        Document query = appendFormulaToDocument(new Document("graph_id", graph_id), formula);
        return find(query).firstResultOptional();
    }

    @Synchronized
    public synchronized void incrementOrCreate(Map<String, String> formula, Boolean valid, ObjectId graph_id, String witness) {
        if (update(new Document("$inc", new Document("visits", 1))).where(appendFormulaToDocument(new Document("graph_id", graph_id), formula)) <= 0)
            Node.builder().graph_id(graph_id).formula(formula).valid(valid).witness(witness).visits(1).leaves(0).build().persistOrUpdate();

    }

    public void deleteByGraphId(ObjectId graph_id) {
        delete(new Document("graph_id", graph_id));
    }

    public Optional<Node> findBestByGraphIdAndFormulaIn(ObjectId graph_id, List<Map<String, String>> formulas) {
        return find(new Document("$or", formulas.stream().map(x -> appendFormulaToDocument(new Document("graph_id", graph_id).append("score", new Document("$ne", null)), x)).toList()), new Document("hopDistance", 1).append("score", 1)).firstResultOptional();
    }

    public void incrementLeaveById(ObjectId oldNodeId) {
        update(new Document("$inc", new Document("leaves", 1))).where(new Document("_id", oldNodeId));
    }

    public Stream<Node> streamByGraphIdAndValid(ObjectId graphId) {
        return find(new Document("graph_id", graphId).append("valid", true)).stream();
    }

    public Stream<Node> streamByGraphId(ObjectId graphId) {
        return find(new Document("graph_id", graphId)).stream();
    }

    public Stream<Node> streamByGraphIdAndInvalid(ObjectId graphId) {
        return find(new Document("graph_id", graphId).append("valid", false)).stream();
    }

    public Long getTotalVisitsFromScoredGraph(ObjectId graph_id) {
        return find(new Document("graph_id", graph_id).append("score", new Document("$ne", null))).stream().map(Node::getVisits).map(Integer::longValue).reduce(0L, Long::sum);
    }

    public Long getTotalVisitsFromGraph(ObjectId graph_id) {
        return find(new Document("graph_id", graph_id)).stream().map(Node::getVisits).map(Integer::longValue).reduce(0L, Long::sum);
    }

    public void clearPolicy(ObjectId graph_id) {
        update(new Document("$unset", new Document("score", null).append("hopDistance", null))).where("graph_id", graph_id);
    }

    public void setLeaves(ObjectId _id, int count) {
        update(new Document("$set", new Document("leaves", count))).where("_id,", _id);
    }

    public void addVisits(ObjectId _id, int count) {
        update(new Document("$inc", new Document("visits", count))).where("_id,", _id);
    }


}
