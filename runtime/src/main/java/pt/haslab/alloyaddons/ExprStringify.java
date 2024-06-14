package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.*;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Objects;

import static edu.mit.csail.sdg.ast.ExprUnary.Op.NOOP;
import static edu.mit.csail.sdg.ast.ExprUnary.Op.ONE;

public class ExprStringify {
    public static String rawStringify(Expr e) {
        return new ExprStringifyVisitReturn().visitThis(e);
    }

    public static String stringify(Expr e) {
        String res = rawStringify(e);
        if ("true".equals(res))
            return "";
        return res;
    }

    @RequiredArgsConstructor
    private static class ExprStringifyVisitReturn extends VisitReturn<String> {

        @Override
        public String visit(ExprBinary exprBinary) throws Err {
            return "(%s %s %s)".formatted(visitThis(exprBinary.left), exprBinary.op.toString(), visitThis(exprBinary.right));
        }

        @Override
        public String visit(ExprList exprList) throws Err {
            List<String> list = exprList.args.stream().map(this::visitThis).toList();

            return switch (exprList.op) {
                case AND -> '(' + ParseUtil.lineCSV(" && ", list) + ')';
                case OR -> '(' + ParseUtil.lineCSV(" || ", list) + ')';
                case DISJOINT -> "disj[" + ParseUtil.lineCSV(",", list) + "]";
                default -> exprList.op + "[" + ParseUtil.lineCSV(",", list) + "]";
            };
        }

        @Override
        public String visit(ExprCall exprCall) throws Err {
            List<String> arguments = exprCall.args.stream().map(this::visitThis).toList();
            String label = exprCall.fun.label.replace("this/", "");
            String res = label + "[" + ParseUtil.lineCSV(",", arguments) + "]";
            if (label.contains("/"))
                return "(" + res + ")";
            return res;
        }

        @Override
        public String visit(ExprConstant exprConstant) throws Err {
            return exprConstant.toString();
        }

        @Override
        public String visit(ExprITE exprITE) throws Err {
            String cond = this.visitThis(exprITE.cond);
            String left = this.visitThis(exprITE.left);
            String right = this.visitThis(exprITE.right);

            return "(%s implies %s else %s)".formatted(cond, left, right);
        }

        @Override
        public String visit(ExprLet exprLet) throws Err {
            return "(let %s=%s {%s})".formatted(visitThis(exprLet.var), visitThis(exprLet.expr), visitThis(exprLet.sub));
        }

        @Override
        public String visit(ExprQt exprQt) throws Err {
            String decString = ParseUtil.lineCSV(",", exprQt.decls.stream().map(e -> ParseUtil.lineCSV(",", e.names.stream().map(x -> x.label).toList()) + ":" + visitThis(e.expr)).toList());
            String sub = visitThis(exprQt.sub);

            if (exprQt.op == ExprQt.Op.COMPREHENSION)
                if (Objects.equals(sub.strip(), "true"))
                    return "{" + decString + "}";
                else return "{" + decString + "|" + sub + "}";
            else return "(" + exprQt.op + " " + decString + "|" + sub + ")";
        }

        public boolean isInt(String s) {
            try {
                Integer.parseInt(s);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        @Override
        public String visit(ExprUnary exprUnary) throws Err {
            String sub = this.visitThis(exprUnary.sub);
            return switch (exprUnary.op) {
                case SOMEOF -> "(some " + sub + ')';
                case LONEOF -> "(lone " + sub + ')';
                case ONEOF -> "(one " + sub + ')';
                case SETOF -> "(set " + sub + ')';
                case EXACTLYOF -> "(exactly " + sub + ')';
                case CAST2INT -> isInt(sub) ? sub : "int[" + sub + "]";
                case CAST2SIGINT -> isInt(sub) ? sub : "Int[" + sub + "]";
                case PRIME -> "(" + sub + ")'";
                case NOOP -> sub;
                default -> '(' + exprUnary.op.toString() + ' ' + sub + ")";
            };
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
}
