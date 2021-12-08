package adsen.scarpet.interpreter.parser.language;

import adsen.scarpet.interpreter.parser.Context;
import adsen.scarpet.interpreter.parser.Expression;
import adsen.scarpet.interpreter.parser.LazyValue;
import adsen.scarpet.interpreter.parser.UserDefinedFunction;
import adsen.scarpet.interpreter.parser.exception.ExitStatement;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.exception.ReturnStatement;
import adsen.scarpet.interpreter.parser.exception.ThrowStatement;
import adsen.scarpet.interpreter.parser.value.FunctionSignatureValue;
import adsen.scarpet.interpreter.parser.value.GlobalValue;
import adsen.scarpet.interpreter.parser.value.Value;

import java.util.ArrayList;
import java.util.List;


public class FunctionsAndControlFlow {

    public static void apply(Expression expression) {
        // artificial construct to handle user defined functions and function definitions
        expression.addLazyFunction(".", -1, (c, t, lv) -> { // adjust based on c
            String name = lv.get(lv.size() - 1).evalValue(c).getString();
            //lv.remove(lv.size()-1); // ain't gonna cut it // maybe it will because of the eager eval changes
            if (t != Context.SIGNATURE) // just call the function
            {
                if (!c.host.globalFunctions.containsKey(name)) {
                    throw new InternalExpressionException("Function " + name + " is not defined yet");
                }
                List<LazyValue> lvargs = new ArrayList<>(lv.size() - 1);
                for (int i = 0; i < lv.size() - 1; i++) {
                    lvargs.add(lv.get(i));
                }
                UserDefinedFunction acf = c.host.globalFunctions.get(name);
                Value retval = acf.lazyEval(c, t, acf.expression, acf.token, lvargs).evalValue(c);
                return (cc, tt) -> retval; ///!!!! dono might need to store expr and token in statics? (e? t?)
            }

            // gimme signature
            List<String> args = new ArrayList<>();
            List<String> globals = new ArrayList<>();
            for (int i = 0; i < lv.size() - 1; i++) {
                Value v = lv.get(i).evalValue(c, Context.LOCALIZATION);
                if (!v.isBound()) {
                    throw new InternalExpressionException("Only variables can be used in function signature, not  " + v.getString());
                }
                if (v instanceof GlobalValue) {
                    globals.add(v.boundVariable);
                } else {
                    args.add(v.boundVariable);
                }
            }
            Value retval = new FunctionSignatureValue(name, args, globals);
            return (cc, tt) -> retval;
        });

        expression.addLazyFunction("outer", 1, (c, t, lv) -> {
            if (t != Context.LOCALIZATION)
                throw new InternalExpressionException("outer scoping of variables is only possible in function signatures");
            return (cc, tt) -> new GlobalValue(lv.get(0).evalValue(c));
        });

        expression.addFunction("exit", (lv) -> {
            throw new ExitStatement(lv.size() == 0 ? Value.NULL : lv.get(0));
        });
        expression.addFunction("return", (lv) -> {
            throw new ReturnStatement(lv.size() == 0 ? Value.NULL : lv.get(0));
        });
        expression.addFunction("throw", (lv) -> {
            throw new ThrowStatement(lv.size() == 0 ? Value.NULL : lv.get(0));
        });

        expression.addLazyFunction("try", -1, (c, t, lv) ->
        {
            if (lv.size() == 0)
                throw new InternalExpressionException("try needs at least an expression block");
            try {
                Value retval = lv.get(0).evalValue(c, t);
                return (c_, t_) -> retval;
            } catch (ThrowStatement ret) {
                if (lv.size() == 1)
                    return (c_, t_) -> Value.NULL;
                LazyValue __ = c.getVariable("_");
                c.setVariable("_", (__c, __t) -> ret.retval.reboundedTo("_"));
                Value val = lv.get(1).evalValue(c, t);
                c.setVariable("_", __);
                return (c_, t_) -> val;
            }
        });

        // if(cond1, expr1, cond2, expr2, ..., ?default) => value
        expression.addLazyFunction("if", -1, (c, t, lv) ->
        {
            if (lv.size() < 2)
                throw new InternalExpressionException("if statement needs to have at least one condition and one case");
            for (int i = 0; i < lv.size() - 1; i += 2) {
                if (lv.get(i).evalValue(c, Context.BOOLEAN).getBoolean()) {
                    //int iFinal = i;
                    Value ret = lv.get(i + 1).evalValue(c);
                    return (cc, tt) -> ret;
                }
            }
            if (lv.size() % 2 == 1) {
                Value ret = lv.get(lv.size() - 1).evalValue(c);
                return (cc, tt) -> ret;
            }
            return (cc, tt) -> Value.ZERO;
        });
    }

}
