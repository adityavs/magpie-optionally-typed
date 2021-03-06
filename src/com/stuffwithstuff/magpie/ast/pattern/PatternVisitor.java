package com.stuffwithstuff.magpie.ast.pattern;

public interface PatternVisitor<R, C> {
  R visit(RecordPattern pattern, C context);
  R visit(TuplePattern pattern, C context);
  R visit(ValuePattern pattern, C context);
  R visit(VariablePattern pattern, C context);
}
