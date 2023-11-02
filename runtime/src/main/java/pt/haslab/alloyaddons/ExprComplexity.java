package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExprComplexity extends VisitReturn<Void> {

    int number_of_elements = 0;
    Map<String, Integer> independent_vars = new HashMap<>(); // Duplicates do exist, yes
    Set<String> stack = new HashSet<>();


    public Double getComplexity() {
        return Math.pow(number_of_elements, independent_vars.values().stream().reduce(Integer::sum).orElse(1));
    }

    @Override
    public Void visit(ExprBinary exprBinary) throws Err {
        number_of_elements += 1;
        this.visitThis(exprBinary.left);
        this.visitThis(exprBinary.right);
        return null;
    }

    @Override
    public Void visit(ExprList exprList) throws Err {
        number_of_elements += Integer.max(0, exprList.args.size() - 1);
        exprList.args.forEach(this::visitThis);
        return null;
    }

    @Override
    public Void visit(ExprCall exprCall) throws Err {
        number_of_elements += 1;
        return null;
    }

    @Override
    public Void visit(ExprConstant exprConstant) throws Err {
        number_of_elements += 1;
        return null;
    }

    @Override
    public Void visit(ExprITE exprITE) throws Err {
        number_of_elements += 1;
        this.visitThis(exprITE.cond);
        this.visitThis(exprITE.left);
        this.visitThis(exprITE.right);
        return null;
    }

    @Override
    public Void visit(ExprLet exprLet) throws Err {
        number_of_elements += 1;
        this.visitThis(exprLet.expr);
        this.visitThis(exprLet.sub);
        return null;
    }

    @Override
    public Void visit(ExprQt exprQt) throws Err {
        number_of_elements += exprQt.decls.size();

        Set<String> independent = new HashSet<>();

        for (Decl d : exprQt.decls) {
            if (new StreamFieldNames().visitThis(d.expr).noneMatch(x -> independent_vars.containsKey(x) || independent.contains(x)))
                d.names.stream().map(y -> y.label).forEach(independent::add);
        }

        Set<String> new_ = new HashSet<>(independent);
        new_.removeAll(independent_vars.keySet());

        independent.forEach(v -> independent_vars.put(v, independent_vars.getOrDefault(v, 0) + 1));

        stack.addAll(new_);
        this.visitThis(exprQt.sub);
        stack.removeAll(new_);

        return null;
    }

    @Override
    public Void visit(ExprUnary exprUnary) throws Err {
        if (exprUnary.op != ExprUnary.Op.NOOP)
            number_of_elements += 1;
        this.visitThis(exprUnary.sub);
        return null;
    }

    @Override
    public Void visit(ExprVar exprVar) throws Err {
        if (!stack.contains(exprVar.label)) {
            //If there is a varieble outside that interacts with us,
            // otherwise we would have known about it
            independent_vars.put(exprVar.label, independent_vars.getOrDefault(exprVar.label, 0) + 1);
            stack.add(exprVar.label); // it will never be removed from the stack, it will never be wrongly accounted twice
        }
        number_of_elements += 1;
        return null;
    }

    @Override
    public Void visit(Sig sig) throws Err {
        number_of_elements += 1;
        return null;
    }

    @Override
    public Void visit(Sig.Field field) throws Err {
        number_of_elements += 1;
        return null;
    }
}
