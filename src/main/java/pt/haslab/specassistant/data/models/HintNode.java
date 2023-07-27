package pt.haslab.specassistant.data.models;


import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.Func;
import edu.mit.csail.sdg.parser.CompModule;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import org.bson.types.ObjectId;
import pt.haslab.alloyaddons.ExprNormalizer;
import pt.haslab.alloyaddons.ExprStringify;
import pt.haslab.alloyaddons.Util;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

@MongoEntity(collection = "HintNode")
public class HintNode extends PanacheMongoEntity {

    public ObjectId graph_id;
    public Map<String, String> formula;

    public String witness;

    public Boolean valid;

    public Integer visits;

    public Integer leaves;

    public Double score;


    public HintNode() {
    }

    public static HintNode create(ObjectId graph_id, Map<String, String> formula, Boolean sat, String witness) {
        HintNode result = new HintNode();

        result.graph_id = graph_id;
        result.formula = formula;
        result.valid = sat;
        result.witness = witness;
        result.visits = result.leaves = 0;

        return result;
    }

    public static Map<String, Expr> getNormalizedFormulaExprFrom(Collection<Func> skolem, Set<String> functions) {
        return Util.streamFuncsWithNames(skolem, functions)
                .collect(toUnmodifiableMap(x -> x.label, ExprNormalizer::normalize));
    }

    public static Map<String, Expr> getFormulaExprFrom(Collection<Func> skolem, Set<String> functions) {
        return Util.streamFuncsWithNames(skolem, functions).collect(toUnmodifiableMap(x -> x.label, Func::getBody));
    }

    public static Map<String, String> getNormalizedFormulaFrom(Collection<Func> funcs, Set<String> targetNames) {
        return Util.streamFuncsWithNames(funcs, targetNames)
                .collect(toUnmodifiableMap(x -> x.label, x -> ExprStringify.stringify(ExprNormalizer.normalize(x))));
    }

    public Map<String, Expr> getParsedFormula(CompModule world) throws RuntimeException {
        return formula.entrySet().stream().collect(toMap(Map.Entry::getKey, x -> Util.parseOneExprFromString(world, x.getValue())));
    }

    public HintNode visit() {
        visits++;
        return this;
    }

    public boolean compareFormula(Map<String, String> x) {
        return this.formula.equals(x);
    }

}
