package pt.haslab.specassistant.data.models;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Optional;

@MongoEntity(collection = "HintGraph")
public class HintGraph extends PanacheMongoEntity {

    public String name;

    public Long policySubmissionCount;
    public Long parsedSubmissionCount;

    public Long totalParsingTime;

    public Long policyCalculationTime;

    public Long requestsCount;
    public Long responseCount;

    public Long mutationHintCount;

    public Long uniqueMutationHintCount;
    public Long graphHintCount;

    public Long uniqueGraphHintCount;

    HintGraph() {
    }

    public static HintGraph newGraph() {
        HintGraph result = new HintGraph();
        result.persist();
        result.update();
        return result;
    }

    public static HintGraph newGraph(String name) {
        HintGraph result = new HintGraph();
        result.persist();
        if (name != null && !name.isEmpty())
            result.name = name;
        result.update();
        return result;
    }

    public static Optional<HintGraph> findById(String hex_string) {
        return findByIdOptional(new ObjectId(hex_string));
    }

    public static Optional<HintGraph> findById(ObjectId graphId) {
        return findByIdOptional(graphId);
    }

    public static void setName(ObjectId graphId, String name) {
        update(new Document("$set", new Document("name", name))).where(new Document("_id", graphId));
    }

    public static void setPolicySubmissionCount(ObjectId graphId, long submissionCount) {
        update(new Document("$set", new Document("policySubmissionCount", submissionCount))).where(new Document("_id", graphId));
    }

    public static void incrementParsingCount(ObjectId graphId) {
        update(new Document("$inc", new Document("parsedSubmissionCount", 1))).where(new Document("_id", graphId));
    }

    public static void registerParsingTime(ObjectId graphId, long totalParsingTime) {
        update(new Document("$inc", new Document("totalParsingTime", totalParsingTime))).where(new Document("_id", graphId));
    }

    public static void registerPolicyCalculationTime(ObjectId graphId, long policyCalculationTime) {
        update(new Document("$inc", new Document("policyCalculationTime", policyCalculationTime))).where(new Document("_id", graphId));
    }

    public static void removeAllPolicyStats(ObjectId id) {
        update(new Document("$unset", new Document("policyCalculationTime", null).append("policySubmissionCount", null))).where("_id", id);
    }

    public static void removeAllHintStats() {
        update(new Document("$unset",
                new Document()
                        .append("requestsCount", null)
                        .append("responseCount", null)
                        .append("mutationHintCount", null)
                        .append("graphHintCount", null)
                        .append("uniqueMutationHintCount", null)
                        .append("uniqueGraphHintCount", null))).all();
    }

    public static void registerMultipleHintAttempt(ObjectId graphId, boolean mutationSuccess, boolean graphSuccess) {
        Document inc_value = new Document("requestsCount", 1);

        if (mutationSuccess || graphSuccess) inc_value.append("responseCount", 1);
        if (mutationSuccess) inc_value.append("mutationHintCount", 1);
        if (graphSuccess) inc_value.append("graphHintCount", 1);
        if (mutationSuccess && !graphSuccess) inc_value.append("uniqueMutationHintCount", 1);
        if (graphSuccess && !mutationSuccess) inc_value.append("uniqueGraphHintCount", 1);

        update(new Document("$inc", inc_value)).where(new Document("_id", graphId));

    }

}
