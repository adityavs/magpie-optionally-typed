package com.stuffwithstuff.magpie.ast;

import com.stuffwithstuff.magpie.parser.Position;

public class UnquoteExpr extends Expr {
  public UnquoteExpr(Position position, Expr body) {
    super(position);
    mBody = body;
  }
  
  public Expr getBody() { return mBody; }
  
  @Override
  public <R, C> R accept(ExprVisitor<R, C> visitor, C context) {
    return visitor.visit(this, context);
  }

  @Override
  public void toString(StringBuilder builder, String indent) {
    builder.append("`(");
    mBody.toString(builder, indent);
    builder.append(")");
  }
  
  private final Expr mBody;
}
