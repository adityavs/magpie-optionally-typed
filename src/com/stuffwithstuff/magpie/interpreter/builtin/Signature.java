package com.stuffwithstuff.magpie.interpreter.builtin;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Signature {
  String value();
}
