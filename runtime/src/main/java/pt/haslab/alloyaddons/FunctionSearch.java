package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class FunctionSearch extends VisitReturn<Void> {

    public final List<Func> result = new ArrayList<>();
    private final Predicate<Func> filter;

    public FunctionSearch(Predicate<Func> filter) {
        this.filter = filter;
    }

    public static List<Func> search(Predicate<Func> filter, Expr expr) {
        FunctionSearch v = new FunctionSearch(filter);
        v.visitThis(expr);
        return v.result;
    }

    public static List<Func> search(Predicate<Func> filter, Command command) {
        return search(filter, command.formula);
    }

    public static List<Func> search(Predicate<Func> filter, Func func) {
        return search(filter, func.getBody());
    }

    @Override
    public Void visit(ExprBinary exprBinary) throws Err {
        visitThis(exprBinary.left);
        visitThis(exprBinary.right);
        return null;
    }

    @Override
    public Void visit(ExprList exprList) throws Err {
        exprList.args.forEach(this::visitThis);
        return null;
    }

    @Override
    public Void visit(ExprCall exprCall) throws Err {
        if (filter.test(exprCall.fun))
            result.add(exprCall.fun);
        else
            visitThis(exprCall.fun.getBody());
        return null;
    }

    @Override
    public Void visit(ExprConstant exprConstant) throws Err {
        return null;
    }

    @Override
    public Void visit(ExprITE exprITE) throws Err {
        visitThis(exprITE.cond);
        visitThis(exprITE.left);
        visitThis(exprITE.right);
        return null;
    }

    @Override
    public Void visit(ExprLet exprLet) throws Err {
        visitThis(exprLet.expr);
        visitThis(exprLet.sub);
        return null;
    }

    @Override
    public Void visit(ExprQt exprQt) throws Err {
        visitThis(exprQt.sub);
        return null;
    }

    @Override
    public Void visit(ExprUnary exprUnary) throws Err {
        visitThis(exprUnary.sub);
        return null;
    }

    @Override
    public Void visit(ExprVar exprVar) throws Err {
        return null;
    }

    @Override
    public Void visit(Sig sig) throws Err {
        return null;
    }

    @Override
    public Void visit(Sig.Field field) throws Err {
        return null;
    }
}
