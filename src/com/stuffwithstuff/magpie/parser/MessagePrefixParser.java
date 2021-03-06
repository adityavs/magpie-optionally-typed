package com.stuffwithstuff.magpie.parser;

import com.stuffwithstuff.magpie.ast.Expr;

public class MessagePrefixParser extends PrefixParser {
  @Override
  public Expr parse(MagpieParser parser, Token token) {
    // Parse the whole fully-qualified name.
    Token fullName = parser.parseName(true);
    return Expr.name(fullName.getPosition(), fullName.getString());
  }
}
