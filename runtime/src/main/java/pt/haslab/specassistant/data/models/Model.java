package pt.haslab.specassistant.data.models;


import edu.mit.csail.sdg.parser.CompModule;
import io.quarkus.mongodb.panache.PanacheMongoEntityBase;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import pt.haslab.alloyaddons.ParseUtil;

@MongoEntity(collection = "Model")
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Model extends PanacheMongoEntityBase {

    @BsonId
    private String id;

    /**
     * the complete code of the model.
     */
    private String code;

    /**
     * which model does it derive from (null if original).
     */
    private String derivationOf;

    /**
     * The root of the derivation tree. Different from derivation, as this is
     * the original model and remains the same after derivation to preserve
     * the original secrets. It Should only change when a model with secrets is
     * shared (i.e., sharing public versions of a model with secrets should
     * not break the derivation).
     */
    private String original;

    /**
     * optional field for the index of the executed command, if created by
     * execution.
     */
    private Integer cmd_i;

    /**
     * optional field for the name of the executed command, if created by
     * execution.
     */
    private String cmd_n;

    /**
     * optional field, whether the command was a check (1) or a run (0), if
     * created by execution.
     */
    private Boolean cmd_c;

    /**
     * optional field, whether the command was satisfiable (1) or unsatisfiable
     * (0), if created by execution. if execution fails, then -1.
     */
    private Integer sat;

    /**
     * the timestamp.
     */
    private String time;

    public static CompModule getWorld(String model_id) {
        return ParseUtil.parseModel(((Model) findById(model_id)).code);
    }

    @BsonIgnore
    public boolean isValidExecution() {
        return sat != null && sat >= 0;
    }
}
