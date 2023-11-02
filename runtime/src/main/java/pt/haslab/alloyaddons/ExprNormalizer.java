package pt.haslab.alloyaddons;

import edu.mit.csail.sdg.alloy4.Err;
import edu.mit.csail.sdg.alloy4.Pos;
import edu.mit.csail.sdg.ast.*;
import io.quarkus.logging.Log;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static edu.mit.csail.sdg.ast.ExprBinary.Op.*;
import static edu.mit.csail.sdg.ast.ExprUnary.Op.NOOP;

public class ExprNormalizer {
    public static Expr normalize(Expr expr) {
        return new ExprVisitReturn().visitThis(expr);
    }

    public static Expr normalize(Func func) {
        return new ExprVisitReturn().visitThis(func.getBody());
    }


    private static class ExprVisitReturn extends VisitReturn<Expr> {


        private static final Map<ExprBinary.Op, ExprBinary.Op> BinOpSym = Map.of(GTE, LT, LTE, GT, NOT_LTE, NOT_GT, NOT_GTE, NOT_LT);
        private static final List<ExprBinary.Op> UnorderedOp = List.of(INTERSECT, PLUS, MUL, EQUALS, NOT_EQUALS, OR, IFF);
        Map<String, Expr> var_context = new HashMap<>();
        Integer var_counter = 0;

        @Override
        public Expr visit(ExprBinary exprBinary) throws Err {
            ExprBinary.Op op = exprBinary.op;

            Expr left = this.visitThis(exprBinary.left);
            Expr right = this.visitThis(exprBinary.right);
            Expr swap = right;

            if (Objects.equals(left.toString(), "true") ) { // Yes, it is possible
                switch (op) {
                    case IMPLIES, AND, IFF, EQUALS -> {
                        return right;
                    }
                    case OR -> {
                        return left;
                    }
                    case NOT_EQUALS -> {
                        return ExprUnary.Op.NOT.make(exprBinary.pos(), right);
                    }
                }
            }
            if (Objects.equals(right.toString(), "true")) { // Yes, it is possible
                switch (op) {
                    case AND, IFF, EQUALS -> {
                        return left;
                    }
                    case OR, IMPLIES -> {
                        return right;
                    }
                    case NOT_EQUALS -> {
                        return ExprUnary.Op.NOT.make(exprBinary.pos(), left);
                    }
                }
            }

            if (UnorderedOp.contains(exprBinary.op) && left.toString().compareTo(right.toString()) > 0) {
                right = left;
                left = swap;
            } else {
                if (BinOpSym.containsKey(exprBinary.op)) {
                    right = left;
                    left = swap;
                    op = BinOpSym.get(exprBinary.op);
                }
            }


            return op.make(exprBinary.pos, exprBinary.closingBracket, left, right);

        }


        @Override
        public Expr visit(ExprList exprList) throws Err {

            List<Expr> exprReturnObjs = exprList.args.stream().map(this::visitThis).sorted(Comparator.comparing(Expr::toString)).toList();

            return ExprList.make(exprList.pos(), exprList.closingBracket, exprList.op, exprReturnObjs.stream().toList());
        }

        @Override
        public Expr visit(ExprCall exprCall) throws Err {

            List<Expr> args = exprCall.args.stream().map(this::visitThis).toList();

            if (!exprCall.fun.pos.sameFile(exprCall.pos)) {

                Expr make = ExprCall.make(exprCall.pos(), exprCall.closingBracket, exprCall.fun, args, exprCall.extraWeight);

                if (ExprStringify.stringify(make).equals("(ordering/max[(Person . (c . (Course <: grades)))])"))
                    Log.info("dfs");
                return make;
            }

            Map<String, Expr> backup = new HashMap<>(var_context);

            for (int i = 0; i < exprCall.fun.decls.size(); i++) {
                int finalI = i;
                exprCall.fun.decls.get(i).names.forEach(n -> var_context.put(n.label, args.get(finalI)));
            }

            Expr result = visitThis(exprCall.fun.getBody());

            var_context = backup;

            return result;
        }

        @Override
        public Expr visit(ExprConstant exprConstant) throws Err {
            return exprConstant;
        }

        @Override
        public Expr visit(ExprITE exprITE) throws Err {
            Expr cond = this.visitThis(exprITE.cond);
            Expr left = this.visitThis(exprITE.left);
            Expr right = this.visitThis(exprITE.right);

            if (cond.toString().equals("true"))
                return left;

            return ExprITE.make(exprITE.pos(), cond, left, right);
        }

        @Override
        public Expr visit(ExprLet exprLet) throws Err {
            String var_label = exprLet.var.label;
            Expr var_expr = this.visitThis(exprLet.expr);
            var_context.put(var_label, var_expr);

            return this.visitThis(exprLet.sub);
        }

        @Override
        public Expr visit(ExprQt exprQt) throws Err {
            Expr current = exprQt;
            ExprQt.Op op = exprQt.op;

            Map<Integer, List<Decl>> identifiers = new HashMap<>();
            Map<String, Integer> namedeps = new HashMap<>();

            if (op == ExprQt.Op.SUM)
                IntStream.range(0, exprQt.decls.size()).forEach(i -> identifiers.put(i, List.of(exprQt.decls.get(i))));
            else
                while (current instanceof ExprQt currentExprQT && currentExprQT.op.equals(op)) {
                    for (Decl decl : currentExprQT.decls) {
                        int depth = new StreamFieldNames().visitThis(decl.expr).collect(Collectors.toSet()).stream().map(namedeps::get).filter(Objects::nonNull).map(i -> i + 1).reduce(0, Integer::max);
                        decl.names.stream().map(x -> x.label).forEach(name -> namedeps.put(name, depth));
                        List<Decl> t = identifiers.getOrDefault(depth, new ArrayList<>());
                        t.add(decl);
                        identifiers.put(depth, t);
                    }
                    current = currentExprQT.sub;
                    while (current instanceof ExprUnary exprUnary && exprUnary.op == NOOP)
                        current = exprUnary.sub;
                }


            Map<String, Expr> rollbacks = new HashMap<>();
            List<Decl> sortedDecls = new ArrayList<>();
            List<Expr> disjoints = new ArrayList<>();

            identifiers.entrySet()
                    .stream()
                    .sorted(Comparator.comparingInt(Map.Entry::getKey))
                    .map(Map.Entry::getValue).forEach(ds -> ds.stream()
                            .map(d -> Map.entry(this.visitThis(d.expr), d))
                            .sorted(Comparator.comparing(x -> x.getKey().toString()))
                            .forEach(e -> {
                                List<ExprVar> replacements = e.getValue().names.stream().filter(x -> x instanceof ExprVar).map(x -> {
                                    ExprVar replacement = ExprVar.make(x.pos(), "ref" + var_counter++);
                                    rollbacks.putIfAbsent(x.label, var_context.put(x.label, replacement));
                                    return replacement;
                                }).toList();

                                if (e.getValue().disjoint != null)
                                    disjoints.add(ExprList.make(e.getValue().disjoint, e.getValue().disjoint2, ExprList.Op.DISJOINT, replacements));

                                sortedDecls.addAll(replacements.stream().map(x -> new Decl(e.getValue().isPrivate, null, null, e.getValue().isVar, List.of(x), e.getKey())).toList());
                            }));


            Expr result = visitThis(current);

            if (!disjoints.isEmpty())
                result = switch (op) {
                    case COMPREHENSION, ALL, NO ->
                            ExprList.make(Pos.UNKNOWN, Pos.UNKNOWN, ExprList.Op.OR, Stream.concat(disjoints.stream().map(x -> ExprUnary.Op.NOT.make(x.pos(), x)), Stream.of(result)).sorted(Comparator.comparing(Expr::toString)).toList());
                    case LONE, ONE, SOME, SUM ->
                            ExprList.make(Pos.UNKNOWN, Pos.UNKNOWN, ExprList.Op.AND, Stream.concat(disjoints.stream(), Stream.of(result)).sorted(Comparator.comparing(Expr::toString)).toList());
                };

            result = op.make(exprQt.pos, exprQt.closingBracket, sortedDecls, result);

            rollbacks.forEach((key, value) -> {
                if (value == null) var_context.remove(key);
                else var_context.put(key, value);
            });
            return result;
        }

        @Override
        public Expr visit(ExprUnary exprUnary) throws Err {
            //NOOP ARE KEEPED FOR PROPER POSITION MAPPING
            return exprUnary.op.make(exprUnary.pos, this.visitThis(exprUnary.sub));
        }

        @Override
        public Expr visit(ExprVar exprVar) throws Err {
            return Optional.ofNullable(var_context.get(exprVar.label)).orElse(exprVar);
        }

        @Override
        public Expr visit(Sig sig) throws Err {
            return sig;
        }

        @Override
        public Expr visit(Sig.Field field) throws Err {
            return field;
        }
    }

}
