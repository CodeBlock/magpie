package com.stuffwithstuff.magpie.interpreter;

import java.util.List;
import java.util.Map.Entry;

import com.stuffwithstuff.magpie.ast.*;

public class ExprEvaluator implements ExprVisitor<Obj, EvalContext> {
  public ExprEvaluator(Interpreter interpreter) {
    mInterpreter = interpreter;
  }
  
  public Obj evaluate(Expr expr, EvalContext context) {
    return expr.accept(this, context);
  }

  @Override
  public Obj visit(AssignExpr expr, EvalContext context) {
    String name = expr.getName();
    
    if (expr.getTarget() == null) {
      // No target means we're just assigning to a variable (or field of this)
      // with the given name.
      Obj value = evaluate(expr.getValue(), context);
      
      // Try to assign to a local.
      if (context.assign(name, value)) return value;
      
      // Otherwise, it must be a setter on this.
      return invokeMethod(expr, context.getThis(), name + "=", value);
    } else {
      // The target of the assignment is an actual expression, like a.b = c
      Obj target = evaluate(expr.getTarget(), context);
      Obj value = evaluate(expr.getValue(), context);

      // If the assignment statement has an argument and a value, like:
      // a.b c = v (c is the arg, v is the value)
      // then bundle them together:
      if (expr.getTargetArg() != null) {
        Obj targetArg = evaluate(expr.getTargetArg(), context);
        value = mInterpreter.createTuple(context, targetArg, value);
      }

      // Invoke the setter method.
      return invokeMethod(expr, target, name + "=", value);
    }
  }

  @Override
  public Obj visit(BlockExpr expr, EvalContext context) {
    Obj result = null;
    
    // Create a lexical scope.
    EvalContext localContext = context.newBlockScope();
    
    // Evaluate all of the expressions and return the last.
    for (Expr thisExpr : expr.getExpressions()) {
      result = evaluate(thisExpr, localContext);
    }
    
    return result;
  }

  @Override
  public Obj visit(BoolExpr expr, EvalContext context) {
    return mInterpreter.createBool(expr.getValue());
  }

  @Override
  public Obj visit(CallExpr expr, EvalContext context) {
    Obj arg = evaluate(expr.getArg(), context);
    
    // Handle a named target.
    if (expr.getTarget() instanceof NameExpr) {
      String name = ((NameExpr)expr.getTarget()).getName();
      
      // Look for a local variable with the name.
      Obj local = context.lookUp(name);
      if (local != null) {
        return invokeMethod(expr, local, "apply", arg);
      }
      
      // Look for an implicit call to a method on this with the name.
      Invokable method = context.getThis().getClassObj().findMethod(name);
      if (method != null) {
        return method.invoke(mInterpreter, context.getThis(), arg);
      }
      
      // Try to call it as a method on the argument. In other words,
      // "abs 123" is equivalent to "123.abs".
      return invokeMethod(expr, arg, name, mInterpreter.nothing());
    }
    
    // Not an explicit named target, so evaluate it and send it an "apply"
    // message.
    Obj target = evaluate(expr.getTarget(), context);
    return invokeMethod(expr, target, "apply", arg);
  }
  
  @Override
  public Obj visit(ClassExpr expr, EvalContext context) {
    // Look up the class if we are extending one, otherwise create it.
    ClassObj metaclass;
    ClassObj classObj;
    if (expr.isExtend()) {
      // TODO(bob): Should this be a generic expression that returns a class?
      // Like: class foo.bar.bang
      Obj obj = context.lookUp(expr.getName());
      
      if (obj == null) {
        mInterpreter.runtimeError(expr,
            "Could not find a class object named \"%s\".", expr.getName());
        return mInterpreter.nothing();
      }
      
      if (!(obj instanceof ClassObj)) {
        mInterpreter.runtimeError(expr, "Object \"%s\" is not a class.",
            expr.getName());
        return mInterpreter.nothing();
      }
      
      classObj = (ClassObj)obj;
      metaclass = classObj.getClassObj();
    } else {
      classObj = mInterpreter.createClass(expr.getName(), context.getScope());
      metaclass = classObj.getClassObj();
      
      // Add the constructor method.
      metaclass.addMethod("new", new NativeMethod.ClassNew(expr.getName()));
    }
    
    // Add the constructors.
    for (FnExpr constructorFn : expr.getConstructors()) {
      FnObj fnObj = mInterpreter.createFn(constructorFn);
      classObj.addConstructor(fnObj);
    }
    
    // Evaluate and define the shared fields.
    EvalContext classContext = context.bindThis(classObj);
    for (Entry<String, Expr> field : expr.getSharedFields().entrySet()) {
      Obj value = evaluate(field.getValue(), classContext);
      
      classObj.setField(field.getKey(), value);
      
      // Add a getter.
      metaclass.addMethod(field.getKey(),
          new NativeMethod.ClassFieldGetter(field.getKey(), null));
      
      // Add a setter.
      metaclass.addMethod(field.getKey() + "=",
          new NativeMethod.ClassFieldSetter(field.getKey(), null));
    }
    
    // Define the shared methods.
    for (Entry<String, List<FnExpr>> methods : expr.getSharedMethods().entrySet()) {
      for (FnExpr method : methods.getValue()) {
        FnObj methodObj = mInterpreter.createFn(method);
        metaclass.addMethod(methods.getKey(), methodObj);
      }
    }
    
    // Define the instance methods.
    for (Entry<String, List<FnExpr>> methods : expr.getMethods().entrySet()) {
      for (FnExpr method : methods.getValue()) {
        FnObj methodObj = mInterpreter.createFn(method);
        classObj.addMethod(methods.getKey(), methodObj);
      }
    }
    
    // Define the getters and setters for the fields.
    for (String field : expr.getFields().keySet()) {
      // Note that we don't provide type expressions for the fields here because
      // we only know the initializer expression. We don't have an actual type
      // annotation.
      
      // Add a getter.
      classObj.addMethod(field,
          new NativeMethod.ClassFieldGetter(field, null));
      
      // Add a setter.
      classObj.addMethod(field + "=",
          new NativeMethod.ClassFieldSetter(field, null));
    }
    
    for (Entry<String, Expr> entry : expr.getFieldDeclarations().entrySet()) {
      // Add a getter.
      classObj.addMethod(entry.getKey(),
          new NativeMethod.ClassFieldGetter(entry.getKey(), entry.getValue()));
      
      // Add a setter.
      classObj.addMethod(entry.getKey() + "=",
          new NativeMethod.ClassFieldSetter(entry.getKey(), entry.getValue()));
    }
    
    // Add the field initializers to the class so it can evaluate them when an
    // object is constructed.
    classObj.defineFields(expr.getFields());
    
    // TODO(bob): Need to add constructors here...
    
    // Define a variable for the class in the current scope.
    context.define(expr.getName(), classObj);
    return classObj;
  }

  @Override
  public Obj visit(DefineExpr expr, EvalContext context) {
    Obj value = evaluate(expr.getValue(), context);

    context.define(expr.getName(), value);
    return value;
  }

  @Override
  public Obj visit(FnExpr expr, EvalContext context) {
    return mInterpreter.createFn(expr);
  }

  @Override
  public Obj visit(IfExpr expr, EvalContext context) {
    // Put it in a block so that variables declared in conditions end when the
    // if expression ends.
    context = context.newBlockScope();
    
    // Evaluate all of the conditions.
    boolean passed = true;
    for (Condition condition : expr.getConditions()) {
      if (condition.isLet()) {
        // "let" condition.
        Obj result = evaluate(condition.getBody(), context);
        
        // If it evaluates to nothing, the condition fails. Otherwise, bind the
        // result to a name and continue.
        if (result != mInterpreter.nothing()) {
          // Success, bind the result.
          context.define(condition.getName(), result);
        } else {
          // Condition failed.
          passed = false;
          break;
        }
      } else {
        // Regular "if" condition.
        Obj result = evaluate(condition.getBody(), context);
        if (!((Boolean)result.getPrimitiveValue()).booleanValue()) {
          // Condition failed.
          passed = false;
          break;
        }
      }
    }
    
    // Evaluate the body.
    if (passed) {
      return evaluate(expr.getThen(), context);
    } else {
      return evaluate(expr.getElse(), context);
    }
  }

  @Override
  public Obj visit(IntExpr expr, EvalContext context) {
    return mInterpreter.createInt(expr.getValue());
  }

  @Override
  public Obj visit(LoopExpr expr, EvalContext context) {
    boolean done = false;
    while (true) {
      // Evaluate the conditions.
      for (Expr conditionExpr : expr.getConditions()) {
        // See if the while clause is still true.
        Obj condition = evaluate(conditionExpr, context);
        if (((Boolean)condition.getPrimitiveValue()).booleanValue() != true) {
          done = true;
          break;
        }
      }
      
      // If any clause failed, stop the loop.
      if (done) break;
      
      evaluate(expr.getBody(), context);
    }
    
    // TODO(bob): It would be cool if loops could have "else" clauses and then
    // reliably return a value.
    return mInterpreter.nothing();
  }

  @Override
  public Obj visit(MethodExpr expr, EvalContext context) {
    Obj receiver = evaluate(expr.getReceiver(), context);
    Obj arg = evaluate(expr.getArg(), context);
    
    return invokeMethod(expr, receiver, expr.getMethod(), arg);
  }

  @Override
  public Obj visit(NameExpr expr, EvalContext context) {
    // Look up a named variable.
    Obj variable = context.lookUp(expr.getName());
    if (variable != null) return variable;
    
    // Try to find a no-arg method on this.
    return invokeMethod(expr, context.getThis(), expr.getName(), mInterpreter.nothing());
  }

  @Override
  public Obj visit(NothingExpr expr, EvalContext context) {
    return mInterpreter.nothing();
  }

  @Override
  public Obj visit(StringExpr expr, EvalContext context) {
    return mInterpreter.createString(expr.getValue());
  }

  @Override
  public Obj visit(ThisExpr expr, EvalContext context) {
    return context.getThis();
  }

  @Override
  public Obj visit(TupleExpr expr, EvalContext context) {
    // Evaluate the fields.
    Obj[] fields = new Obj[expr.getFields().size()];
    for (int i = 0; i < fields.length; i++) {
      fields[i] = evaluate(expr.getFields().get(i), context);
    }

    return mInterpreter.createTuple(context, fields);
  }

  private Obj invokeMethod(Expr expr, Obj receiver, String name, Obj arg) {
    Invokable method = receiver.getClassObj().findMethod(name);
    
    if (method == null) {
      mInterpreter.runtimeError(expr,
          "Could not find a variable or method named \"%s\" on %s.",
          name, receiver.getClassObj());
      
      return mInterpreter.nothing();
    }
    
    return method.invoke(mInterpreter, receiver, arg);
  }

  private final Interpreter mInterpreter;
}