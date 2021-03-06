package com.stuffwithstuff.magpie.interpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.stuffwithstuff.magpie.ast.*;
import com.stuffwithstuff.magpie.ast.pattern.MatchCase;
import com.stuffwithstuff.magpie.parser.Position;
import com.stuffwithstuff.magpie.util.NotImplementedException;
import com.stuffwithstuff.magpie.util.Pair;

/**
 * Implements the visitor pattern on AST nodes. For any given expression,
 * evaluates the type of object that could result from evaluating that
 * expression. Also reports type errors as they are found.
 */
public class ExprChecker implements ExprVisitor<Obj, EvalContext> {
  public ExprChecker(Interpreter interpreter, Checker checker, EvalContext
      staticContext) {
    mInterpreter = interpreter;
    mChecker = checker;
    mStaticContext = staticContext;
  }
  
  /**
   * Checks the given function body for a function declared within the given
   * lexical context. Returns the type returned by a call to that function. This
   * type will include the type of the expression body itself, combined with the
   * types returned by any "return" expressions found in the function.
   * 
   * @param body     The body of the function.
   * @param context  The type context where the function was declared.
   * @return         The type of object returned by a call to this function.
   */
  public Obj checkFunction(Expr body, EvalContext context) {
    // Get the type implicitly returned by the function.
    Obj evaluatedType = body.accept(this, context);
    
    // And include any early returned types as well.
    for (int i = 0; i < mReturnedTypes.size(); i++) {
      evaluatedType = mInterpreter.orTypes(
          evaluatedType, mReturnedTypes.get(i));
    }
    return evaluatedType;
  }
  
  public Obj check(Expr expr, EvalContext context) {
    if (expr == null) return null;
    return check(expr, context, false);
  }

  public Obj check(Expr expr, EvalContext context, boolean allowNever) {
    Obj result = expr.accept(this, context);
    
    if (!allowNever && result == mInterpreter.getNeverClass()) {
      mChecker.addError(expr.getPosition(),
          "An early return here will cause unreachable code.");
    }
    
    return result;
  }

  @Override
  public Obj visit(AssignExpr expr, EvalContext context) {
    Obj receiverType = check(expr.getReceiver(), context);
    Obj valueType = check(expr.getValue(), context);    

    if (receiverType == null) {
      // Just a name, so maybe it's a local variable.
      Obj existingType = context.lookUp(expr.getName());
      if (existingType != null) {
        
        // Make sure the new value is compatible with the variable's type.
        mChecker.checkTypes(existingType, valueType, expr.getPosition(),
            "Variable of type %s cannot be assigned a value of type %s.");

        // The type doesn't change.
        return existingType;
      }
      
      // Otherwise it must be a property on this.
      receiverType = context.getThis();
    }
    
    Obj setterType = mInterpreter.invokeMethod(receiverType,
        Name.GET_MEMBER_TYPE,
        mInterpreter.createString(expr.getName() + "_="));
    
    if (setterType == mInterpreter.nothing()) {
      mChecker.addError(expr.getPosition(),
          "Could not find a setter \"%s\" on %s when checking.",
          expr.getName(), receiverType);

      return mInterpreter.getNothingClass();
    }
    
    // Make sure the assigned value if compatible with the setter.
    mChecker.checkTypes(setterType, valueType, expr.getPosition(),
        "Setter of type %s cannot be assigned a value of type %s.");

    return setterType;
  }

  @Override
  public Obj visit(BlockExpr expr, EvalContext context) {
    Obj result = mInterpreter.getNothingClass();
    
    // Evaluate all of the expressions and return the last.
    for (int i = 0; i < expr.getExpressions().size(); i++) {
      Expr thisExpr = expr.getExpressions().get(i);
      // Can only early return from the end of a block.
      boolean allowReturn = i == expr.getExpressions().size() - 1;
      result = check(thisExpr, context, allowReturn);
    }
    
    // TODO(bob): Need to check catch clauses.
    
    return result;
  }

  @Override
  public Obj visit(BoolExpr expr, EvalContext context) {
    return mInterpreter.getBoolClass();
  }
  
  @Override
  public Obj visit(BreakExpr expr, EvalContext context) {
    if (context.isInLoop()) {
      return mInterpreter.getNeverClass();
    }
    
    mChecker.addError(expr.getPosition(),
        "A break expression should not appear outside of a loop.");
    return mInterpreter.getNothingClass();
  }

  @Override
  public Obj visit(CallExpr expr, EvalContext context) {
    Obj targetType = check(expr.getTarget(), context);
    Obj argType = check(expr.getArg(), context);
    
    // If the target is not an actual function, get the type of its "call"
    // message instead of the target itself.
    Obj functionType = mInterpreter.getGlobal(Name.FUNCTION_TYPE);
    if (targetType.getClassObj() != functionType) {
      // It's a functor, so look up the "call" member.
      Obj callTargetType = getMemberType(expr.getPosition(), targetType,
          Name.CALL);

      if (callTargetType.getClassObj() != functionType) {
        mChecker.addError(expr.getPosition(),
            "Target of type %s is not a function and does not have a 'call' method.",
            targetType);
        return mInterpreter.getNothingClass();
      }
      
      targetType = callTargetType;
    }
    
    // Handle the type parameters.
    // TODO(bob): This check here is gross.
    if ((targetType.getValue() instanceof FnExpr) &&
        ((FnExpr)targetType.getValue()).getType().getTypeParams().size() > 0) {
      FunctionType type = ((FnExpr)targetType.getValue()).getType();
      
      Map<String, Obj> typeArgs = new HashMap<String, Obj>();
      // Add empty entries for all of the type parameter names.
      for (Pair<String, Expr> typeParam : type.getTypeParams()) {
        typeArgs.put(typeParam.getKey(), null);
      }

      // Apply the explicit type arguments.
      for (int i = 0; i < expr.getTypeArgs().size(); i++) {
        Obj typeArg = mInterpreter.evaluate(expr.getTypeArgs().get(i),
            mStaticContext);
        typeArgs.put(type.getTypeParams().get(i).getKey(), typeArg);
      }
      
      // Infer the type parameters.
      PatternTypeParamInferrer.infer(mInterpreter, type, argType, typeArgs);

      // Create a new static scope for the type arguments.
      EvalContext staticContext = mStaticContext.pushScope();
      for (Entry<String, Obj> entry : typeArgs.entrySet()) {
        staticContext.define(entry.getKey(), entry.getValue());
      }
      
      // Now we can actually evaluate the function's type.
      targetType = mInterpreter.evaluateFunctionType(type, staticContext);
    }

    // Just a regular function.
    Obj paramType = targetType.getField(Name.PARAM_TYPE);
    Obj returnType = targetType.getField(Name.RETURN_TYPE);
    
    // Make sure the argument type matches the declared parameter type.
    mChecker.checkTypes(paramType, argType, expr.getPosition(), 
        "Function is declared to take %s but is being passed %s.");
    
    // Calling a function results in the function's return type.
    return returnType;
  }
  
  @Override
  public Obj visit(FnExpr expr, EvalContext context) {
    // Check the body and create a context containing any static arguments the
    // function defines.
    return mChecker.checkFunction(expr, context.getScope(),
        (ClassObj)context.getThis(), mStaticContext);
  }

  @Override
  public Obj visit(IntExpr expr, EvalContext context) {
    return mInterpreter.getIntClass();
  }

  @Override
  public Obj visit(LoopExpr expr, EvalContext context) {
    context = context.enterLoop();
    
    // Check the body for errors.
    context = context.pushScope();
    check(expr.getBody(), context);
    
    // Loops always return nothing.
    return mInterpreter.getNothingClass();
  }

  @Override
  public Obj visit(MatchExpr expr, EvalContext context) {
    // Push a new context so that a variable declared in the value expression
    // itself disappears after the match, i.e.:
    // match var i = 123
    // ...
    // end
    // i should be gone here
    context = context.pushScope();
    
    Obj valueType = check(expr.getValue(), context);
    
    Obj returnType = null;
    for (MatchCase matchCase : expr.getCases()) {
      // Bind the pattern's variable types in a new scope for the case body.
      EvalContext caseContext = context.pushScope();
      PatternCheckingBinder.bind(mChecker, expr.getPosition(),
          matchCase.getPattern(), valueType, caseContext);
      
      Obj caseType = check(matchCase.getBody(), caseContext, true);
      
      // A match expression's return type includes all of the case body types.
      if (returnType == null) {
        returnType = caseType;
      } else {
        returnType = mInterpreter.orTypes(returnType, caseType);
      }
    }
    
    return returnType;
  }

  @Override
  public Obj visit(MessageExpr expr, EvalContext context) {
    Obj receiver = check(expr.getReceiver(), context);
    
    if (receiver == null) {
      // Just a name, so maybe it's a variable.
      Obj variableType = context.lookUp(expr.getName());
      if (variableType != null) return variableType;
      
      // Otherwise it must be a property on this.
      receiver = context.getThis();
    }
    
    return getMemberType(expr.getPosition(), receiver, expr.getName());
  }

  @Override
  public Obj visit(NothingExpr expr, EvalContext context) {
    return mInterpreter.getNothingClass();
  }
    
  @Override
  public Obj visit(QuotationExpr expr, EvalContext context) {
    return mInterpreter.getGlobal(Name.EXPRESSION);
  }

  @Override
  public Obj visit(RecordExpr expr, EvalContext context) {
    // The type of a record is a record of its types.
    Map<String, Obj> fields = new HashMap<String, Obj>();
    for (Pair<String, Expr> entry : expr.getFields()) {
      Obj type = check(entry.getValue(), context);
      fields.put(entry.getKey(), type);
    }
    
    return mInterpreter.createRecord(fields);
  }

  @Override
  public Obj visit(ReturnExpr expr, EvalContext context) {
    // Get the type of value being returned.
    Obj returnedType = check(expr.getValue(), context);
    mReturnedTypes.add(returnedType);
    
    return mInterpreter.getNeverClass();
  }
  
  @Override
  public Obj visit(ScopeExpr expr, EvalContext context) {
    context = context.pushScope();
    return check(expr.getBody(), context);
  }
  
  @Override
  public Obj visit(StringExpr expr, EvalContext context) {
    return mInterpreter.getStringClass();
  }

  @Override
  public Obj visit(ThisExpr expr, EvalContext context) {
    return context.getThis();
  }

  @Override
  public Obj visit(TupleExpr expr, EvalContext context) {
    // The type of a tuple type is just a tuple of its types.
    List<Obj> fields = new ArrayList<Obj>();
    for (Expr field : expr.getFields()) {
      fields.add(check(field, context));
    }
    
    return mInterpreter.createTuple(fields);
  }

  @Override
  public Obj visit(TypeofExpr expr, EvalContext context) {
    // TODO(bob): This should eventually return Type | Nothing
    return mInterpreter.getNothingClass();
  }

  @Override
  public Obj visit(UnquoteExpr expr, EvalContext context) {
    // TODO(bob): Check that it evaluates to an expression?
    throw new NotImplementedException();
  }

  @Override
  public Obj visit(UnsafeCastExpr expr, EvalContext context) {
    Obj type = mInterpreter.evaluate(expr.getType(), mStaticContext);
    
    // Check the value, but ignore it's evaluated type.
    check(expr.getValue(), context);
    
    return type;
  }

  @Override
  public Obj visit(UsingExpr expr, EvalContext context) {
    context.getScope().useNamespace(expr.getName());
    return mInterpreter.nothing();
  }

  @Override
  public Obj visit(VariableExpr expr, EvalContext context) {
    Obj valueType = check(expr.getValue(), context);

    PatternCheckingBinder.bind(mChecker, expr.getPosition(), expr.getPattern(),
        valueType, context);
    return valueType;
  }
  
  public Obj getMemberType(Position position, Obj receiverType, String name) {
    Obj memberType = mInterpreter.invokeMethod(receiverType,
        Name.GET_MEMBER_TYPE, mInterpreter.createString(name));
    
    if (memberType == mInterpreter.nothing()) {
      mChecker.addError(position,
          "Could not find a member named \"%s\" on %s when checking.",
          name, receiverType);
      
      return mInterpreter.getNothingClass();
    }

    return memberType;
  }

  private final Interpreter mInterpreter;
  private final Checker mChecker;
  private final List<Obj> mReturnedTypes = new ArrayList<Obj>();
  // The context in which type annotations and other static expressions are
  // evaluated during type-checking.
  // TODO(bob): Everything that evaluates during check time should use this.
  private final EvalContext mStaticContext;
}
