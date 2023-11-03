package pt.haslab.specassistant.services.treeedit;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.ast.*;
import pt.haslab.specassistant.services.treeedit.apted.node.Node;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.stream.Stream;

public class ExprToEditData extends VisitReturn<Node<EditData>> {


    Stack<Pos> posStack = new Stack<>();

    public ExprToEditData(Pos init) {
        posStack.push(init);
    }

    public static Node<EditData> parse(Expr e) {
        return new ExprToEditData(new Pos(e.pos.filename, 1, 1, Integer.MAX_VALUE, Integer.MAX_VALUE)).visitThis(e);
    }

    public static Node<EditData> parseOrDefault(Expr e) {
        if (e == null)
            return new Node<>(new EditData(ExprConstant.TRUE, Pos.UNKNOWN));
        return parse(e);
    }

    private Pos getValidPos(Pos previous, Pos next) {
        if (next != null && !next.equals(Pos.UNKNOWN) && previous.contains(next))
            return next;
        else
            return previous;
    }

    private Pos peekValidPos(Pos p) {
        return getValidPos(posStack.peek(), p);
    }

    private Pos pushValidPos(Pos p) {
        Pos pushed = getValidPos(posStack.peek(), p);
        posStack.push(pushed);
        return pushed;
    }

    @Override
    public Node<EditData> visit(ExprBinary exprBinary) throws Err {
        Node<EditData> result = new Node<>(new EditData(exprBinary, posStack.peek()));

        result.addChild(visitThis(exprBinary.left));
        result.addChild(visitThis(exprBinary.right));

        return result;
    }

    @Override
    public Node<EditData> visit(ExprList exprList) throws Err {
        Node<EditData> result = new Node<>(new EditData(exprList, posStack.peek()));

        exprList.args.stream().map(this::visitThis).forEach(result::addChild);

        return result;
    }

    @Override
    public Node<EditData> visit(ExprCall exprCall) throws Err {
        return new Node<>(new EditData(exprCall, peekValidPos(exprCall.pos)));
    }

    @Override
    public Node<EditData> visit(ExprConstant exprConstant) throws Err {
        return new Node<>(new EditData(exprConstant, peekValidPos(exprConstant.pos)));
    }

    @Override
    public Node<EditData> visit(ExprITE exprITE) throws Err {
        Node<EditData> result = new Node<>(new EditData(exprITE, posStack.peek()));

        result.addChild(visitThis(exprITE.left));
        result.addChild(visitThis(exprITE.right));

        return result;
    }

    @Override
    public Node<EditData> visit(ExprLet exprLet) throws Err {
        Node<EditData> result = new Node<>(new EditData(exprLet, posStack.peek()));

        result.addChild(visitThis(exprLet.sub));

        return result;
    }

    @Override
    public Node<EditData> visit(ExprQt exprQt) throws Err {
        Pos topValidPos = pushValidPos(exprQt.pos);
        posStack.pop();

        Node<EditData> bottom = visitThis(exprQt.sub);
        Expr bottomExpr = exprQt.sub;

        List<Decl> rv_decls = new ArrayList<>(exprQt.decls);
        Collections.reverse(rv_decls);

        for (Decl d : rv_decls) {
            Pos clusterPos = Stream.concat(d.names.stream().map(Expr::pos), Stream.of(d.expr.pos(), d.span())).map(this::peekValidPos).reduce(Pos::merge).orElse(topValidPos);
            bottomExpr = exprQt.op.make(null, null, List.of(d), bottomExpr);
            Node<EditData> next = new Node<>(new EditData(bottomExpr, peekValidPos(clusterPos)));

            next.addChild(bottom);
            bottom = next;
        }

        return bottom;
    }

    @Override
    public Node<EditData> visit(ExprUnary exprUnary) throws Err {

        if (ExprUnary.Op.NOOP.equals(exprUnary.op)) {
            try {
                pushValidPos(exprUnary.pos());
                return visitThis(exprUnary.sub);
            } finally {
                posStack.pop();
            }
        }

        Node<EditData> result = new Node<>(new EditData(exprUnary, posStack.peek()));
        result.addChild(visitThis(exprUnary.sub));
        return result;
    }

    @Override
    public Node<EditData> visit(ExprVar exprVar) throws Err {
        return new Node<>(new EditData(exprVar, peekValidPos(exprVar.pos())));
    }

    @Override
    public Node<EditData> visit(Sig sig) throws Err {
        return new Node<>(new EditData(sig, peekValidPos(sig.pos())));
    }

    @Override
    public Node<EditData> visit(Sig.Field field) throws Err {
        return new Node<>(new EditData(field, peekValidPos(field.pos())));
    }
}
