package pt.haslab.specassistant.data.models;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.*;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

@MongoEntity(collection = "Graph")
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Graph extends PanacheMongoEntity {

    private String name;

    @BsonIgnore
    @ToString.Include(rank = 2,name = "id")
    public ObjectId getId() {
        return this.id;
    }

    public static Graph newGraph(String name) {
        Graph result = new Graph();
        result.persist();
        if (name != null && !name.isEmpty())
            result.name = name;
        result.update();
        return result;
    }

    public static void registerParsing(ObjectId graphId, String model_id, long parsedCount, long totalParsingTime) {
        String nest = "parsing." + model_id + ".";
        update(new Document("$set", new Document(nest + "time", 1e-9 * totalParsingTime).append(nest + "count", parsedCount))).where(new Document("_id", graphId));
    }

    public static void registerPolicy(ObjectId graphId, long policyCalculationTime, long policyCount) {
        update(new Document("$set", new Document("policy.time", 1e-9 * policyCalculationTime).append("policy.count", policyCount))).where(new Document("_id", graphId));
    }

    public static void removeAllPolicyStats(ObjectId id) {
        update(new Document("$unset", new Document("policyTime", null).append("policyCount", null))).where("_id", id);
    }

}
