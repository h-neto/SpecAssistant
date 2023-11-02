package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.*;

import java.util.List;

import static pt.haslab.alloyaddons.ParseUtil.lineCSV;


public class ExprNodeStringify extends VisitReturn<String> {

    public static String stringify(Expr expr) {
        return new ExprNodeStringify().visitThis(expr);
    }

    @Override
    public String visit(ExprBinary exprBinary) throws Err {
        return exprBinary.op.toString();
    }

    @Override
    public String visit(ExprList exprList) throws Err {
        return switch (exprList.op) {
            case AND -> "&&";
            case OR -> "||";
            case DISJOINT -> "disj";
            case TOTALORDER -> "TOTALORDER";
        };
    }

    @Override
    public String visit(ExprCall exprCall) throws Err {
        List<String> arguments = exprCall.args.stream().map(this::visitThis).toList();
        return exprCall.fun.label.replace("this/", "") + "[" + lineCSV(",", arguments) + "]";
    }

    @Override
    public String visit(ExprConstant exprConstant) throws Err {
        return exprConstant.toString();
    }

    @Override
    public String visit(ExprITE exprITE) throws Err {
        return "if";
    }

    @Override
    public String visit(ExprLet exprLet) throws Err {
        return "let";
    }

    @Override
    public String visit(ExprQt exprQt) throws Err {
        String decString = ParseUtil.lineCSV(",",
                exprQt.decls.stream()
                        .map(e -> ParseUtil.lineCSV(",", e.names.stream()
                                .map(x -> x.label).toList()) + ":" + ExprStringify.rawStringify(e.expr))
                        .toList()
        );

        String op = (exprQt.op == ExprQt.Op.COMPREHENSION ? ExprQt.Op.ALL : exprQt.op).toString();

        return op + " " + decString;
    }

    @Override
    public String visit(ExprUnary exprUnary) throws Err {
        return exprUnary.op.toString();
    }

    @Override
    public String visit(ExprVar exprVar) throws Err {
        return exprVar.label.replace("this/", "");
    }

    @Override
    public String visit(Sig sig) throws Err {
        return sig.label.replace("this/", "");
    }

    @Override
    public String visit(Sig.Field field) throws Err {
        return "(" + field.sig.label.replace("this/", "") + " <: " + field.label.replace("this/", "") + ")";
    }
}

