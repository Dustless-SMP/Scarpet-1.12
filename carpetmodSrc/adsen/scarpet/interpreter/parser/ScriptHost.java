package adsen.scarpet.interpreter.parser;

import adsen.scarpet.interpreter.parser.exception.ExpressionException;
import adsen.scarpet.interpreter.parser.exception.InvalidCallbackException;
import adsen.scarpet.interpreter.parser.value.NumericValue;
import adsen.scarpet.interpreter.parser.value.StringValue;
import adsen.scarpet.interpreter.parser.value.Value;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static java.lang.Math.max;

public class ScriptHost {

    public final Map<String, UserDefinedFunction> globalFunctions = new HashMap<>();

    public final Map<String, LazyValue> globalVariables = new HashMap<>();

    private final String name;

    ScriptHost(String name) {
        this.name = name;
        globalVariables.put("euler", (c, t) -> NumericValue.euler);
        globalVariables.put("avogadro", (c, t) -> NumericValue.avogadro);
        globalVariables.put("pi", (c, t) -> NumericValue.PI);
        globalVariables.put("phi", (c, t) -> NumericValue.PHI);
        globalVariables.put("null", (c, t) -> Value.NULL);
        globalVariables.put("true", (c, t) -> Value.TRUE);
        globalVariables.put("false", (c, t) -> Value.FALSE);

        //special variables for second order functions so we don't need to check them all the time
        globalVariables.put("_", (c, t) -> Value.ZERO);
        globalVariables.put("_i", (c, t) -> Value.ZERO);
        globalVariables.put("_a", (c, t) -> Value.ZERO);
    }

    public String getName() {
        return name;
    }

    public Expression getExpressionForFunction(String name) {
        return globalFunctions.get(name).getExpression();
    }

    public Tokenizer.Token getTokenForFunction(String name) {
        return globalFunctions.get(name).getToken();
    }

    public List<String> getPublicFunctions() {
        return globalFunctions.keySet().stream().filter((str) -> !str.startsWith("_")).collect(Collectors.toList());
    }

    public List<String> getAvailableFunctions() {
        return globalFunctions.keySet().stream().filter((str) -> !str.startsWith("__")).collect(Collectors.toList());
    }

    public String call(String call, String arg) {
        UserDefinedFunction acf = globalFunctions.get(call);
        if (acf == null)
            return "UNDEFINED";
        List<LazyValue> argv = new ArrayList<>();
        String sign = "";
        for (Tokenizer.Token tok : Tokenizer.simplePass(arg)) {
            switch (tok.type) {
                case VARIABLE:
                    if (globalVariables.containsKey(tok.surface.toLowerCase(Locale.ROOT))) {
                        argv.add(globalVariables.get(tok.surface.toLowerCase(Locale.ROOT)));
                        break;
                    }
                case STRING:
                    argv.add((c, t) -> new StringValue(tok.surface));
                    sign = "";
                    break;

                case LITERAL:
                    try {
                        String finalSign = sign;
                        argv.add((c, t) -> new NumericValue(finalSign + tok.surface));
                        sign = "";
                    } catch (NumberFormatException exception) {
                        return "Fail: " + sign + tok.surface + " seems like a number but it is not a number. Use quotes to ensure its a string";
                    }
                    break;
                case HEX_LITERAL:
                    try {
                        String finalSign = sign;
                        argv.add((c, t) -> new NumericValue(new BigInteger(finalSign + tok.surface.substring(2), 16).doubleValue()));
                        sign = "";
                    } catch (NumberFormatException exception) {
                        return "Fail: " + sign + tok.surface + " seems like a number but it is not a number. Use quotes to ensure its a string";
                    }
                    break;
                case OPERATOR:
                case UNARY_OPERATOR:
                    if ((tok.surface.equals("-") || tok.surface.equals("-u")) && sign.isEmpty()) {
                        sign = "-";
                    } else {
                        return "Fail: operators, like " + tok.surface + " are not allowed in invoke";
                    }
                    break;
                case FUNCTION:
                    return "Fail: passing functions like " + tok.surface + "() to invoke is not allowed";
                case OPEN_PAREN:
                case COMMA:
                case CLOSE_PAREN:
                case MARKER:
                    return "Fail: " + tok.surface + " is not allowed in invoke";
            }
        }
        List<String> args = acf.getArguments();
        if (argv.size() != args.size()) {
            StringBuilder error = new StringBuilder("Fail: stored function " + call + " takes " + args.size() + " arguments, not " + argv.size() + ":\n");
            for (int i = 0; i < max(argv.size(), args.size()); i++) {
                error.append(i < args.size() ? args.get(i) : "??").append(" => ").append(i < argv.size() ? argv.get(i).evalValue(null).getString() : "??").append("\n");
            }
            return error.toString();
        }
        try {
            Context context = new Context(this);
            return Expression.evalValue(
                    () -> acf.lazyEval(context, Context.VOID, acf.expression, acf.token, argv),
                    context,
                    Context.VOID
            ).getString();
        } catch (ExpressionException e) {
            return e.getMessage();
        }
    }

    public void callUDF(UserDefinedFunction acf, List<LazyValue> argv) throws InvalidCallbackException {
        List<String> args = acf.getArguments();
        if (argv.size() != args.size()) {
            throw new InvalidCallbackException();
        }
        try {
            Context context = new Context(this);
            Expression.evalValue(
                    () -> acf.lazyEval(context, Context.VOID, acf.expression, acf.token, argv),
                    context,
                    Context.VOID);
        } catch (ExpressionException e) {
            //todo logger
            //ScarpetInterpreterJava.LOG.error("Callback failed: "+e.getMessage());
        }
    }
}
