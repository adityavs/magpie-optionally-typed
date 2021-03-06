^title Variables

Variables are named slots for storing values.

## Defining Variables

Variables in Magpie must be explicitly declared. This avoids a lot of annoying
issues that can crop up with typos in names and unintended variable scope
without adding too much overhead. Variables are created using `var`:

    :::magpie
    var a = 1 + 2

This creates a new variable `a` in the current scope and initializes it with the
result of the expression following the `=`. A variable definition expression
returns the defined value:

    :::magpie
    print(var a = "hi") // prints "hi"

### Initialization

You cannot declare a variable in Magpie without initializing it to a value. This
helps avoid errors from forgetting to initialize a variable. Because Magpie
doesn't have statements, this is less of a limitation than it might be in other
languages. Where in C, you might do:

    :::c
    int i;
    if (something || other) {
      i = 1;
    } else if (another) {
      if (also) {
        i = 2;
      } else {
        i = 3;
      }
    } else {
      i = 4;
    }

In Magpie, you can do:

    var i = if something or other then 1
            else if another then
                if also then 2 else 3
            else 4

In return for the limitation of having to always initialize your variables,
Magpie will give you something back: you don't have to declare the types of
variables. It will infer them from the initializing expression. Even though all
variables are declared using `var`, from the type-checker's perspective, they
are still strongly statically typed.

### Scope

Variables in Magpie have true block scope: the exist from the point they are defined until the end of the [block](blocks.html) in which they are defined.

    :::magpie
    do
        print(a) // "nothing"
        var a = 123
        print(a) // "123"
    end
    print(a) // "nothing"

### Shadowing

Declaring a variable in an inner scope with the same name as an outer one is called *shadowing* and is not an error (although it's not something you likely intend to do much):

    :::magpie
    var a = "outer"
    if true then
        var a = "inner"
        print(a) // Prints "inner".
    end
    print(a) // Prints "outer".

Declaring a variable with the same name in the *same* scope *is* an error:

    :::magpie
    var a = "hi"
    var a = "again" // Error!

### Named Functions

You can create a function and assign it to a variable like any other value:

    :::magpie
    var double = fn(i) i * 2

But, since this is something you do frequently, Magpie has a shorter form that accomplishes the same thing:

    :::magpie
    def double(i) i * 2

## Assignment

After a variable has been declared, you can assign to it using `=`:

    var a = 123
    a = 234

An assignment will walk up the scope stack to find where the named variable is declared. If it can't find a variable with that name, it will generate a runtime error.

Like variable definition, an assignment expression returns the assigned value:

    var a = "before"
    print(a = "after") // prints "after"

<p class="future">
I need to document assigment messages here. Need to update now that patterns are used.
</p>
