package pt.haslab.specassistant.data.models;

import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.aggregation.Transition;

@MongoEntity(collection = "Test")
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class SpecAssistantTest extends PanacheMongoEntityBase {

    @BsonId
    private ID id;

    private ObjectId graphId;

    private Data data;

    public SpecAssistantTest(ID id) {
        this.id = id;
    }

    public SpecAssistantTest setData(Data data) {
        this.data = data;
        return this;
    }

    @BsonIgnore
    public SpecAssistantTest setGraphId(ObjectId graph_id) {
        this.graphId = graph_id;
        return this;
    }

    public record ID(String model_id, String type) {
    }

    public record Data(Boolean success, Double time, Integer hintDistance, Transition t) {
        public Data(Boolean success, Double time) {
            this(success, time, null, null);
        }

        public Data(Boolean success, Long nano_time) {
            this(success, nano_time * 1e-9, null, null);
        }

        public Data(Boolean success, Long nano_time, Integer hintDistance) {
            this(success, nano_time * 1e-9, hintDistance, null);
        }

        public Data(Boolean success, Long nano_time, Integer hintDistance, Transition t) {
            this(success, nano_time * 1e-9, hintDistance, t);
        }

    }

}
