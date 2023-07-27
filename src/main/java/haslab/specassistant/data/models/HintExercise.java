package pt.haslab.specassistant.data.models;

import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;

import java.util.Set;

@MongoEntity(collection = "HintExercise")
public class HintExercise extends PanacheMongoEntity {
    public String model_id;

    public ObjectId graph_id;

    //Indicates the number of secret commands introduced by the model
    //Allows the program to filter commands with repeated names under normal conditions
    //(i.e., the secret commands are always last in the getAllCommands method list)
    public Integer secret_cmd_count;

    public String cmd_n;

    public Set<String> targetFunctions;


    public HintExercise() {
    }

    public HintExercise(String model_id, ObjectId graph_id, Integer secret_cmd_count, String cmd_n, Set<String> targetFunctions) {
        this.model_id = model_id;
        this.graph_id = graph_id;
        this.secret_cmd_count = secret_cmd_count;
        this.cmd_n = cmd_n;
        this.targetFunctions = targetFunctions;
    }


}