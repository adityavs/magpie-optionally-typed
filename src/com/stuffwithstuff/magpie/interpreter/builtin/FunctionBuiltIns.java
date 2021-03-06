package com.stuffwithstuff.magpie.interpreter.builtin;

import com.stuffwithstuff.magpie.interpreter.FnObj;
import com.stuffwithstuff.magpie.interpreter.Interpreter;
import com.stuffwithstuff.magpie.interpreter.Obj;

public class FunctionBuiltIns {
  @Getter("type Type")
  public static class Type implements BuiltInCallable {
    public Obj invoke(Interpreter interpreter, Obj thisObj, Obj arg) {
      FnObj function = thisObj.asFn();
      return function.getCallable().getType(interpreter);
    }
  }
}
