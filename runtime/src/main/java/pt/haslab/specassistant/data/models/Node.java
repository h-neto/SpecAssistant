package pt.haslab.specassistant.data.models;


import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.ErrorSyntax;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.Func;
import edu.mit.csail.sdg.parser.CompModule;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.*;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import pt.haslab.alloyaddons.*;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

@MongoEntity(collection = "Node")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(callSuper = false)
public class Node extends PanacheMongoEntity {
    private ObjectId graph_id;
    @ToString.Include
    private Map<String, String> formula;

    private String witness;

    private Boolean valid;

    private Integer visits;

    private Integer leaves;

    private Integer hopDistance;

    private Double complexity;
    @ToString.Include(rank = 1)

    private Double score;

    @BsonIgnore
    @ToString.Include(rank = 2,name = "id")
    public ObjectId getId() {
        return this.id;
    }

    public Map<String, String> getFormula() {
        return formula;
    }

    public static Map<String, Expr> getNormalizedFormulaExprFrom(CompModule world, Set<String> functions) {
        return getNormalizedFormulaExprFrom(world.getAllFunc().makeConstList(), functions);
    }

    public static Map<String, Expr> getNormalizedFormulaExprFrom(Collection<Func> skolem, Set<String> functions) {
        return AlloyUtil.streamFuncsWithNames(skolem, functions)
                .collect(toUnmodifiableMap(x -> x.label, ExprNormalizer::normalize));
    }

    public static Map<String, String> formulaExprToString(Map<String, Expr> formulaExpr) {
        return formulaExpr.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, x -> ExprStringify.stringify(x.getValue())));
    }

    public static Map<String, Expr> getFormulaExprFrom(Collection<Func> skolem, Set<String> functions) {
        return AlloyUtil.streamFuncsWithNames(skolem, functions).collect(toUnmodifiableMap(x -> x.label, Func::getBody));
    }

    public static Map<String, String> getNormalizedFormulaFrom(Collection<Func> funcs, Set<String> targetNames) {
        return AlloyUtil.streamFuncsWithNames(funcs, targetNames)
                .collect(toUnmodifiableMap(x -> x.label, x -> ExprStringify.stringify(ExprNormalizer.normalize(x))));
    }

    public Map<String, Expr> getParsedFormula(CompModule world) throws IllegalStateException {
        try {
            CompModule target_world = Optional.ofNullable(this.witness).map(Model::getWorld).orElse(world);
            return formula.entrySet().stream().collect(toMap(Map.Entry::getKey, x -> ParseUtil.parseOneExprFromString(target_world, x.getValue())));
        } catch (ErrorSyntax e) {
            throw new IllegalStateException("Syntax Error While Parsing Formula:\"" + this.getFormula().toString().replace("\n", "") + "\" " + e.pos.toString() + " " + e.getMessage(), e);
        } catch (Err e) {
            throw new IllegalStateException("Alloy Error While Parsing Formula:\"" + this.getFormula().toString().replace("\n", "") + "\" " + e.pos.toString() + " " + e.getMessage(), e);
        } catch (UncheckedIOException e) {
            throw new IllegalStateException("IO Error While Parsing Formula:\"" + this.getFormula().toString().replace("\n", "") + "\" " + e.getMessage(), e);
        }
    }

    public Node visit() {
        visits++;
        return this;
    }

    public static Optional<Node> findByMax(ObjectId graph_id, String field) {
        return find(new Document("graph_id", graph_id), new Document(field, -1)).project(Node.class).firstResultOptional();

    }

    public static Optional<Node> findByMin(ObjectId graph_id, String field) {
        return find(new Document("graph_id", graph_id), new Document(field, 1)).project(Node.class).firstResultOptional();
    }

}
