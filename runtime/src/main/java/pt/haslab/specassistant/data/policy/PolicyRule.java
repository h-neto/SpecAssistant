package pt.haslab.specassistant.data.policy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import pt.haslab.specassistant.data.aggregation.Transition;

import java.util.function.Function;


@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@NoArgsConstructor
@JsonSubTypes({
        @JsonSubTypes.Type(value = Var.class, name = "var"),
        @JsonSubTypes.Type(value = Binary.class, name = "binary"),
        @JsonSubTypes.Type(value = Constant.class, name = "constant")
})
public abstract class PolicyRule implements Function<Transition, Double> {

    public abstract void normalizeByGraph(ObjectId objectId);

}
