package adsen.scarpet.interpreter.parser.language;

import adsen.scarpet.interpreter.parser.Context;
import adsen.scarpet.interpreter.parser.Expression;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.ListValue;
import adsen.scarpet.interpreter.parser.value.NumericValue;
import adsen.scarpet.interpreter.parser.value.StringValue;
import adsen.scarpet.interpreter.parser.value.Value;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemFunctions {
    protected static final Random randomizer = new Random();

    // %[argument_index$][flags][width][.precision][t]conversion
    private static final Pattern formatPattern = Pattern.compile("%(\\d+\\$)?([-#+ 0,(\\<]*)?(\\d+)?(\\.\\d+)?([tT])?([a-zA-Z%])");

    public static void apply(Expression expression) {
        expression.addUnaryFunction("print", v -> {
            String s = v.getString();
            Expression.print(s);
            return StringValue.of(s);
        });
        expression.addLazyFunction("bool", 1, (c, t, lv) -> {
            Value v = lv.get(0).evalValue(c, Context.BOOLEAN);
            if (v instanceof StringValue) {
                String str = v.getString();
                if ("false".equalsIgnoreCase(str) || "null".equalsIgnoreCase(str)) {
                    return (cc, tt) -> Value.FALSE;
                }
            }
            Value retval = new NumericValue(v.getBoolean());
            return (cc, tt) -> retval;
        });
        expression.addUnaryFunction("number", v -> {
            if (v instanceof NumericValue)
                return v;
            double res = v.readNumber();
            if (Double.isNaN(res))
                return Value.NULL;
            return new NumericValue(v.readNumber());
        });
        expression.addFunction("str", lv ->
        {
            if (lv.size() == 0)
                throw new InternalExpressionException("str requires at least one argument");
            String format = lv.get(0).getString();
            if (lv.size() == 1)
                return new StringValue(format);
            int argIndex = 1;
            if (lv.get(1) instanceof ListValue) {
                lv = ((ListValue) lv.get(1)).getItems();
                argIndex = 0;
            }
            List<Object> args = new ArrayList<>();
            Matcher m = formatPattern.matcher(format);
            for (int i = 0, len = format.length(); i < len; ) {
                if (m.find(i)) {
                    // Anything between the start of the string and the beginning
                    // of the format specifier is either fixed text or contains
                    // an invalid format string.
                    // [[scarpet]] but we skip it and let the String.format fail
                    char fmt = m.group(6).toLowerCase().charAt(0);
                    if (fmt == 's') {
                        if (argIndex >= lv.size())
                            throw new InternalExpressionException("Not enough arguments for " + m.group(0));
                        args.add(lv.get(argIndex).getString());
                        argIndex++;
                    } else if (fmt == 'd' || fmt == 'o' || fmt == 'x') {
                        if (argIndex >= lv.size())
                            throw new InternalExpressionException("Not enough arguments for " + m.group(0));
                        args.add(lv.get(argIndex).readInteger());
                        argIndex++;
                    } else if (fmt == 'a' || fmt == 'e' || fmt == 'f' || fmt == 'g') {
                        if (argIndex >= lv.size())
                            throw new InternalExpressionException("Not enough arguments for " + m.group(0));
                        args.add(lv.get(argIndex).readNumber());
                        argIndex++;
                    } else if (fmt == 'b') {
                        if (argIndex >= lv.size())
                            throw new InternalExpressionException("Not enough arguments for " + m.group(0));
                        args.add(lv.get(argIndex).getBoolean());
                        argIndex++;
                    } else if (fmt != '%') {//skip %%
                        throw new InternalExpressionException("format not supported: " + m.group(6));
                    }

                    i = m.end();
                } else {
                    // No more valid format specifiers.  Check for possible invalid
                    // format specifiers.
                    // [[scarpet]] but we skip it and let the String.format fail
                    break;
                }
            }
            try {
                return new StringValue(String.format(format, args.toArray()));
            } catch (IllegalFormatException ife) {
                throw new InternalExpressionException("Illegal string format: " + ife.getMessage());
            }
        });

        expression.addUnaryFunction("length", v -> new NumericValue(v.length()));
        expression.addLazyFunction("rand", 1, (c, t, lv) -> {
            Value argument = lv.get(0).evalValue(c);
            if (argument instanceof ListValue) {
                List<Value> list = ((ListValue) argument).getItems();
                Value retval = list.get(randomizer.nextInt(list.size()));
                return (cc, tt) -> retval;
            }
            if (t == Context.BOOLEAN) {
                double rv = NumericValue.asNumber(argument).getDouble() * randomizer.nextFloat();
                Value retval = rv < 1.0D ? Value.FALSE : Value.TRUE;
                return (cc, tt) -> retval;
            }
            Value retval = new NumericValue(NumericValue.asNumber(argument).getDouble() * randomizer.nextDouble());
            return (cc, tt) -> retval;
        });

        expression.addUnaryFunction("sleep", (v) -> {
            long time = NumericValue.asNumber(v).getLong();
            try {
                Thread.sleep(time);
                Thread.yield();
            } catch (InterruptedException ignored) {
            }
            return v; // pass through for variables
        });

        expression.addLazyFunction("time", 0, (c, t, lv) -> {
            Value time = new NumericValue(System.currentTimeMillis() / 1000.0);
            return (cc, tt) -> time;
        });

        expression.addLazyFunction("var", 1, (c, t, lv) -> {
            String varname = lv.get(0).evalValue(c).getString();
            if (!c.isAVariable(varname))
                c.setVariable(varname, (_c, _t) -> Value.ZERO.reboundedTo(varname));
            return c.getVariable(varname);
        });

        expression.addLazyFunction("undef", 1, (c, t, lv) -> {
            String varname = lv.get(0).evalValue(c).getString();
            if (varname.startsWith("_"))
                throw new InternalExpressionException("Cannot replace local built-in variables, i.e. those that start with '_'");
            if (varname.endsWith("*")) {
                varname = varname.replaceAll("\\*+$", "");
                for (String key : c.host.globalFunctions.keySet()) {
                    if (key.startsWith(varname)) c.host.globalFunctions.remove(key);
                }
                for (String key : c.host.globalVariables.keySet()) {
                    if (key.startsWith(varname)) c.host.globalVariables.remove(key);
                }
                c.clearAll(varname);
            } else {
                c.host.globalFunctions.remove(varname);
                c.delVariable(varname);
            }
            return (cc, tt) -> Value.NULL;
        });

        expression.addLazyFunction("vars", 1, (c, t, lv) -> {
            String prefix = lv.get(0).evalValue(c).getString();
            List<Value> values = new ArrayList<>();
            if (prefix.startsWith("global")) {
                for (String k : c.host.globalVariables.keySet()) {
                    if (k.startsWith(prefix))
                        values.add(new StringValue(k));
                }
            } else {
                for (String k : c.getAllVariableNames()) {
                    if (k.startsWith(prefix))
                        values.add(new StringValue(k));
                }
            }
            Value retval = ListValue.wrap(values);
            return (cc, tt) -> retval;
        });


    }
}
