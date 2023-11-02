package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.*;

import java.util.stream.Stream;

class StreamFieldNames extends VisitReturn<Stream<String>> {


    @Override
    public Stream<String> visit(ExprBinary exprBinary) throws Err {
        return Stream.concat(visitThis(exprBinary.left), visitThis(exprBinary.right));
    }

    @Override
    public Stream<String> visit(ExprList exprList) throws Err {
        return exprList.args.stream().flatMap(this::visitThis);
    }

    @Override
    public Stream<String> visit(ExprCall exprCall) throws Err {
        return exprCall.args.stream().flatMap(this::visitThis);
    }

    @Override
    public Stream<String> visit(ExprConstant exprConstant) throws Err {
        return Stream.of();
    }

    @Override
    public Stream<String> visit(ExprITE exprITE) throws Err {
        return Stream.of(visitThis(exprITE.cond), visitThis(exprITE.left), visitThis(exprITE.right)).flatMap(i -> i);
    }

    @Override
    public Stream<String> visit(ExprLet exprLet) throws Err {
        return Stream.concat(visitThis(exprLet.expr), visitThis(exprLet.sub));
    }

    @Override
    public Stream<String> visit(ExprQt exprQt) throws Err {
        return Stream.concat(exprQt.decls.stream().map(x -> x.expr).flatMap(this::visitThis), visitThis(exprQt.sub));
    }

    @Override
    public Stream<String> visit(ExprUnary exprUnary) throws Err {
        return visitThis(exprUnary.sub);
    }

    @Override
    public Stream<String> visit(ExprVar exprVar) throws Err {
        return Stream.of(exprVar.label);
    }

    @Override
    public Stream<String> visit(Sig sig) throws Err {
        return Stream.of();
    }

    @Override
    public Stream<String> visit(Sig.Field field) throws Err {
        return visitThis(field.decl().expr);
    }
}
