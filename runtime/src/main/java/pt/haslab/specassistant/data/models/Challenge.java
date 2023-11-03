package pt.haslab.specassistant.data.models;

import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.parser.CompModule;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.*;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import pt.haslab.alloyaddons.AlloyUtil;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@MongoEntity(collection = "Challenge")
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Challenge extends PanacheMongoEntity {
    private String model_id;

    private ObjectId graph_id;

    //Indicates the number of secret commands introduced by the model
    //Allows the program to filter commands with repeated names under normal conditions
    //(i.e., the secret commands are always last in the getAllCommands method list)
    private Integer end_offset;

    private String cmd_n;

    private Set<String> targetFunctions;

    @BsonIgnore
    @ToString.Include(rank = 2,name = "id")
    public ObjectId getId() {
        return this.id;
    }

    public ObjectId getGraph_id() {
        return graph_id;
    }

    /**
     * Tests if the command index is contained wothing the last "end_offset" defined comands
     * (meteor currently places secrets as the last defined predicates)
     *
     * @param world Alloy Module
     * @param index Command index
     * @return True if valid, False otherwise
     */
    public boolean isValidCommand(CompModule world, Integer index) {
        return index >= world.getAllCommands().size() - end_offset;
    }

    public Optional<Command> getValidCommand(CompModule world, String label) {
        List<Command> l = List.copyOf(world.getAllCommands());
        int from = Integer.max(0, l.size() - end_offset);

        return AlloyUtil.getCommand(l.subList(from, l.size()), label);
    }


}