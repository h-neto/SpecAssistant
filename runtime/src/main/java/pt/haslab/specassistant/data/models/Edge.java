package pt.haslab.specassistant.data.models;


import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.*;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.Optional;

@MongoEntity(collection = "Edge")
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Builder
public class Edge extends PanacheMongoEntity {
    private ObjectId graph_id, origin, destination;

    private Float editDistance;

    private Integer count;

    private Boolean policy;

    public Edge visit() {
        count++;
        return this;
    }

    @BsonIgnore
    @ToString.Include(rank = 2,name = "id")
    public ObjectId getId() {
        return this.id;
    }

    public Boolean getPolicy() {
        return policy != null && policy;
    }

    public static Optional<Edge> findByMax(ObjectId graph_id, String field) {
        return find(new Document("graph_id", graph_id), new Document(field, -1)).project(Edge.class).firstResultOptional();
    }

    public static Optional<Edge> findByMin(ObjectId graph_id, String field) {
        return find(new Document("graph_id", graph_id), new Document(field, 1)).project(Edge.class).firstResultOptional();
    }
}
