package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.ast.*;

public class ExprNodeEquals extends VisitReturn<Boolean> {

    Expr other;

    public ExprNodeEquals(Expr other) {
        this.other = other;
    }

    public static Boolean equals(Expr expr1, Expr expr2) {
        return new ExprNodeEquals(expr2).visitThis(expr1);
    }

    @Override
    public Boolean visit(ExprBinary exprBinary) throws Err {
        if (!(other instanceof ExprBinary))
            return false;
        ExprBinary other = (ExprBinary) this.other;

        return other.op.equals(exprBinary.op);
    }

    @Override
    public Boolean visit(ExprList exprList) throws Err {
        if (!(other instanceof ExprList))
            return false;
        ExprList other = (ExprList) this.other;

        return other.op.equals(exprList.op);
    }

    @Override
    public Boolean visit(ExprCall exprCall) throws Err {
        if (!(other instanceof ExprCall))
            return false;
        ExprCall other = (ExprCall) this.other;

        return other.fun.pos.equals(exprCall.fun.pos);
    }

    @Override
    public Boolean visit(ExprConstant exprConstant) throws Err {
        if (!(other instanceof ExprConstant))
            return false;
        ExprConstant other = (ExprConstant) this.other;

        return other.op.equals(exprConstant.op);
    }

    @Override
    public Boolean visit(ExprITE exprITE) throws Err {
        if (!(other instanceof ExprITE))
            return false;
        this.other = ((ExprITE) this.other).cond;

        return visitThis(exprITE.cond);
    }

    @Override
    public Boolean visit(ExprLet exprLet) throws Err {
        if (!(other instanceof ExprLet))
            return false;
        ExprLet other = ((ExprLet) this.other);

        this.other = other.var;
        if (visitThis(exprLet.var)) {
            return nextEqual(exprLet.expr, other.expr);
        }
        return false;
    }

    private Boolean nextEqual(Expr thisSub, Expr otherSub) {
        this.other = otherSub;
        return visitThis(thisSub);
    }

    @Override
    public Boolean visit(ExprQt exprQt) throws Err {
        if (!(other instanceof ExprQt))
            return false;
        ExprQt other = (ExprQt) this.other;

        if (other.decls.size() == exprQt.decls.size()) {
            for (int i = 0; i < other.decls.size(); i++) {
                Decl decThis = exprQt.decls.get(i), decOther = other.decls.get(i);

                if ((decThis.disjoint != null) == (decOther.disjoint != null))
                    return false;

                if (decThis.names.size() == decOther.names.size())
                    for (int j = 0; j < decThis.names.size(); j++)
                        if (!nextEqual(decThis.names.get(j), decOther.names.get(j)))
                            return false;

                if (!decThis.expr.isSame(decOther.expr))
                    return false;
            }
            return true;
        }
        return false;

    }

    @Override
    public Boolean visit(ExprUnary exprUnary) throws Err {
        if (!(other instanceof ExprUnary))
            return false;
        ExprUnary other = (ExprUnary) this.other;

        return other.op.equals(exprUnary.op);
    }

    @Override
    public Boolean visit(ExprVar exprVar) throws Err {
        if (!(other instanceof ExprVar))
            return false;
        ExprVar other = (ExprVar) this.other;

        return other.label.equals(exprVar.label);
    }

    @Override
    public Boolean visit(Sig sig) throws Err {
        if (!(other instanceof Sig))
            return false;
        Sig other = (Sig) this.other;

        return other.label.equals(sig.label);
    }

    @Override
    public Boolean visit(Sig.Field field) throws Err {
        if (!(other instanceof Sig.Field))
            return false;
        Sig.Field other = (Sig.Field) this.other;

        return other.label.equals(field.label) && other.sig.label.equals(field.sig.label);
    }
}
