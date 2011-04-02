package com.stuffwithstuff.magpie.parser;

import com.stuffwithstuff.magpie.ast.*;

/**
 * Converts an Expr to a Pattern with (more or less) the same structure. Used
 * to get the left-hand side of an assignment expression and convert it to a
 * pattern after it's already been parsed as an expression.
 */
public class ConvertAssignmentExpr implements ExprVisitor<Expr, Expr> {

  public static Expr convert(Expr target, Expr value) {
    ConvertAssignmentExpr converter = new ConvertAssignmentExpr();
    return target.accept(converter, value);
  }
  
  @Override
  public Expr visit(AssignExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(BlockExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(BoolExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(BreakExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(FnExpr expr, Expr value) {
    return invalidExpression(expr);
  }
  
  @Override
  public Expr visit(IntExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(LoopExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(MatchExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(MessageExpr expr, Expr value) {
    // example: point x = 2
    // before:  Msg(      Msg(null, "point"), "x")
    // after:   AssignMsg(Msg(null, "point"), "x", Int(2))
    return Expr.assign(expr.getPosition(), expr.getReceiver(),
        expr.getName(), value);
  }

  @Override
  public Expr visit(MethodExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(NothingExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(RecordExpr expr, Expr value) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Expr visit(ReturnExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(ScopeExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(StringExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  @Override
  public Expr visit(TupleExpr expr, Expr value) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Expr visit(VariableExpr expr, Expr value) {
    return invalidExpression(expr);
  }

  private Expr invalidExpression(Expr expr) {
    throw new ParseException("Expression \"" + expr +
        "\" is not a valid target for assignment.");
  }
}
