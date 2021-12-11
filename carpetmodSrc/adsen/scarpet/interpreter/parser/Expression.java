package adsen.scarpet.interpreter.parser;

import adsen.scarpet.interpreter.parser.Fluff.AbstractFunction;
import adsen.scarpet.interpreter.parser.Fluff.AbstractLazyFunction;
import adsen.scarpet.interpreter.parser.Fluff.AbstractLazyOperator;
import adsen.scarpet.interpreter.parser.Fluff.AbstractOperator;
import adsen.scarpet.interpreter.parser.Fluff.AbstractUnaryOperator;
import adsen.scarpet.interpreter.parser.Fluff.ILazyFunction;
import adsen.scarpet.interpreter.parser.Fluff.ILazyOperator;
import adsen.scarpet.interpreter.parser.Fluff.QuadFunction;
import adsen.scarpet.interpreter.parser.Fluff.SexFunction;
import adsen.scarpet.interpreter.parser.Fluff.TriFunction;
import adsen.scarpet.interpreter.parser.exception.ExpressionException;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.exception.ReturnStatement;
import adsen.scarpet.interpreter.parser.exception.ScarpetExpressionException;
import adsen.scarpet.interpreter.parser.exception.ThrowStatement;
import adsen.scarpet.interpreter.parser.language.Arithmetic;
import adsen.scarpet.interpreter.parser.language.FunctionsAndControlFlow;
import adsen.scarpet.interpreter.parser.language.LoopsAndHigherOrderFunctions;
import adsen.scarpet.interpreter.parser.language.Operators;
import adsen.scarpet.interpreter.parser.language.SystemFunctions;
import adsen.scarpet.interpreter.parser.value.NumericValue;
import adsen.scarpet.interpreter.parser.value.StringValue;
import adsen.scarpet.interpreter.parser.value.Value;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Expression implements Cloneable {
    static Expression none = new Expression("null");
    /**
     * script specific operators and built-in functions
     */
    private final Map<String, ILazyOperator> operators = new HashMap<>();
    private final Map<String, ILazyFunction> functions = new HashMap<>();
    /**
     * The function used to print, must accept a string, and can be use to display results however you want.
     * By default set to {@link System#out#println(String)}, so it prints to command line, but can be set to whatever you want.
     */
    private final Consumer<String> printFunction = System.out::println;
    /**
     * The current infix expression
     */
    private String expression;
    private String name;
    /**
     * Cached AST (Abstract Syntax Tree) (root) of the expression
     */
    private LazyValue ast = null;

    /**
     * @param expression .
     */
    public Expression(String expression) {
        this.expression = expression.trim().
                replaceAll("\\r\\n?", "\n").
                replaceAll(";+$", "");

        FunctionsAndControlFlow.apply(this);
        Operators.apply(this);
        Arithmetic.apply(this);
        SystemFunctions.apply(this);
        LoopsAndHigherOrderFunctions.apply(this);
    }

    static List<String> getExpressionSnippet(Tokenizer.Token token, Expression expr) {
        String code = expr.getCodeString();
        List<String> output = new ArrayList<>(getExpressionSnippetLeftContext(token, code));
        List<String> context = getExpressionSnippetContext(token, code);
        output.add(context.get(0) + " HERE>> " + context.get(1));
        output.addAll(getExpressionSnippetRightContext(token, code));
        return output;
    }

    private static List<String> getExpressionSnippetLeftContext(Tokenizer.Token token, String expr) {
        List<String> output = new ArrayList<>();
        String[] lines = expr.split("\n");
        if (lines.length == 1) return output;
        for (int lno = token.lineNo - 1; lno >= 0 && output.size() < 1; lno--) {
            output.add(lines[lno]);
        }
        Collections.reverse(output);
        return output;
    }

    private static List<String> getExpressionSnippetContext(Tokenizer.Token token, String expr) {
        List<String> output = new ArrayList<>();
        String[] lines = expr.split("\n");
        if (lines.length > 1) {
            output.add(lines[token.lineNo].substring(0, token.linePos));
            output.add(lines[token.lineNo].substring(token.linePos));
        } else {
            output.add(expr.substring(max(0, token.pos - 40), token.pos));
            output.add(expr.substring(token.pos, min(token.pos + 1 + 40, expr.length())));
        }
        return output;
    }

    private static List<String> getExpressionSnippetRightContext(Tokenizer.Token token, String expr) {
        List<String> output = new ArrayList<>();
        String[] lines = expr.split("\n");
        if (lines.length == 1) {
            return output;
        }
        for (int lno = token.lineNo + 1; lno < lines.length && output.size() < 1; lno++) {
            output.add(lines[lno]);
        }
        return output;
    }

    static Value evalValue(Supplier<LazyValue> exprProvider, Context c, Integer expectedType) {
        try {
            return exprProvider.get().evalValue(c, expectedType);
        } catch (StackOverflowError ignored) {
            throw new ExpressionException("Your thoughts are too deep");
        } catch (InternalExpressionException exc) {
            throw new ExpressionException("Your expression result is incorrect:" + exc.getMessage());
        } catch (ArithmeticException exc) {
            throw new ExpressionException("The final result is incorrect, " + exc.getMessage());
        }
    }

    /**
     * Prints a function to the screen, in a manner specified by {@link Expression#printFunction}
     *
     * @param s The string to display to the user.
     */
    public void print(String s) {
        printFunction.accept(s);
    }

    String getCodeString() {
        return expression;
    }

    String getName() {
        return name;
    }

    boolean isAnOperator(String opname) {
        return operators.containsKey(opname) || operators.containsKey(opname + "u");
    }

    Set<String> getFunctionNames() {
        return functions.keySet();
    }

    @Override
    protected Expression clone() throws CloneNotSupportedException {
        // very very shallow copy for global functions to grab the context for error messages
        Expression copy = (Expression) super.clone();
        copy.expression = this.expression;
        copy.name = this.name;
        return copy;
    }

    public void addLazyUnaryOperator(String surface, String precedence, boolean leftAssoc,
                                     TriFunction<Context, Integer, LazyValue, LazyValue> lazyfun) {
        operators.put(surface + "u", new AbstractLazyOperator(Operators.precedence.get(precedence), leftAssoc) {
            @Override
            public LazyValue lazyEval(Context c, Integer t, Expression e, Tokenizer.Token token, LazyValue v, LazyValue v2) {
                try {
                    if (v2 != null) {
                        throw new ExpressionException(e, token, "Did not expect a second parameter for unary operator");
                    }
                    Value.assertNotNull(v);
                    return lazyfun.apply(c, t, v);
                } catch (InternalExpressionException exc) {
                    throw new ExpressionException(e, token, exc.getMessage());
                } catch (ArithmeticException exc) {
                    throw new ExpressionException(e, token, "Your math is wrong, " + exc.getMessage());
                }
            }
        });
    }

    public void addLazyBinaryOperatorWithDelegation(String surface, String precedence, boolean leftAssoc,
                                                    SexFunction<Context, Integer, Expression, Tokenizer.Token, LazyValue, LazyValue, LazyValue> lazyfun) {
        operators.put(surface, new AbstractLazyOperator(Operators.precedence.get(precedence), leftAssoc) {
            @Override
            public LazyValue lazyEval(Context c, Integer type, Expression e, Tokenizer.Token t, LazyValue v1, LazyValue v2) {
                try {
                    Value.assertNotNull(v1, v2);
                    return lazyfun.apply(c, type, e, t, v1, v2);
                } catch (InternalExpressionException exc) // might not actually throw it
                {
                    throw new ExpressionException(e, t, exc.getMessage());
                } catch (ArithmeticException exc) {
                    throw new ExpressionException(e, t, "Your math is wrong, " + exc.getMessage());
                }
            }
        });
    }

    public void addLazyBinaryOperator(String surface, String precedence, boolean leftAssoc,
                                      QuadFunction<Context, Integer, LazyValue, LazyValue, LazyValue> lazyfun) {
        operators.put(surface, new AbstractLazyOperator(Operators.precedence.get(precedence), leftAssoc) {
            @Override
            public LazyValue lazyEval(Context c, Integer t, Expression e, Tokenizer.Token token, LazyValue v1, LazyValue v2) {
                try {
                    Value.assertNotNull(v1, v2);
                    return lazyfun.apply(c, t, v1, v2);
                } catch (InternalExpressionException exc) {
                    throw new ExpressionException(e, token, exc.getMessage());
                } catch (ArithmeticException exc) {
                    throw new ExpressionException(e, token, "Your math is wrong, " + exc.getMessage());
                }
            }
        });
    }

    public void addUnaryOperator(String surface, boolean leftAssoc, Function<Value, Value> fun) {
        operators.put(surface + "u", new AbstractUnaryOperator(Operators.precedence.get("unary+-!"), leftAssoc) {
            @Override
            public Value evalUnary(Value v1) {
                return fun.apply(Value.assertNotNull(v1));
            }
        });
    }

    public void addBinaryOperator(String surface, String precedence, boolean leftAssoc, BiFunction<Value, Value, Value> fun) {
        operators.put(surface, new AbstractOperator(Operators.precedence.get(precedence), leftAssoc) {
            @Override
            public Value eval(Value v1, Value v2) {
                Value.assertNotNull(v1, v2);
                return fun.apply(v1, v2);
            }
        });
    }

    public void addUnaryFunction(String name, Function<Value, Value> fun) {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name, new AbstractFunction(1) {
            @Override
            public Value eval(List<Value> parameters) {
                return fun.apply(Value.assertNotNull(parameters.get(0)));
            }
        });
    }

    public void addBinaryFunction(String name, BiFunction<Value, Value, Value> fun) {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name, new AbstractFunction(2) {
            @Override
            public Value eval(List<Value> parameters) {
                Value v1 = parameters.get(0);
                Value v2 = parameters.get(1);
                Value.assertNotNull(v1, v2);
                return fun.apply(v1, v2);
            }
        });
    }

    public void addFunction(String name, int numParams, Function<List<Value>, Value> fun) {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name, new AbstractFunction(numParams) {
            @Override
            public Value eval(List<Value> parameters) {
                for (Value v : parameters)
                    Value.assertNotNull(v);
                return fun.apply(parameters);
            }
        });
    }

    public void addFunction(String name, Function<List<Value>, Value> fun) {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name, new AbstractFunction(-1) {
            @Override
            public Value eval(List<Value> parameters) {
                for (Value v : parameters)
                    Value.assertNotNull(v);
                return fun.apply(parameters);
            }
        });
    }

    public void addMathematicalUnaryFunction(String name, Function<Double, Double> fun) {
        addUnaryFunction(name, (v) -> new NumericValue(fun.apply(NumericValue.asNumber(v).getDouble())));
    }

    public void addMathematicalBinaryFunction(String name, BiFunction<Double, Double, Double> fun) {
        addBinaryFunction(name, (w, v) ->
                new NumericValue(fun.apply(NumericValue.asNumber(w).getDouble(), NumericValue.asNumber(v).getDouble())));
    }

    public void addLazyFunction(String name, int num_params, TriFunction<Context, Integer, List<LazyValue>, LazyValue> fun) {
        name = name.toLowerCase(Locale.ROOT);
        functions.put(name, new AbstractLazyFunction(num_params) {
            @Override
            public LazyValue lazyEval(Context c, Integer i, Expression e, Tokenizer.Token t, List<LazyValue> lazyParams) {
                try {
                    return fun.apply(c, i, lazyParams);
                } catch (InternalExpressionException exc) {
                    throw new ExpressionException(e, t, exc.getMessage());
                } catch (ArithmeticException exc) {
                    throw new ExpressionException(e, t, "Your math is wrong, " + exc.getMessage());
                }
            }
        });
    }

    public void addContextFunction(Context context, String name, Expression expr, Tokenizer.Token token, List<String> arguments, List<String> globals, LazyValue code) {
        name = name.toLowerCase(Locale.ROOT);
        if (functions.containsKey(name))
            throw new ExpressionException(expr, token, "Function " + name + " would mask a built-in function");
        Expression function_context;
        try {
            function_context = expr.clone();
            function_context.name = name;
        } catch (CloneNotSupportedException e) {
            throw new ExpressionException(expr, token, "Problems in allocating global function " + name);
        }

        context.host.globalFunctions.put(name, new UserDefinedFunction(arguments, function_context, token) {
            @Override
            public LazyValue lazyEval(Context c, Integer type, Expression e, Tokenizer.Token t, List<LazyValue> lazyParams) {
                if (arguments.size() != lazyParams.size()) // something that might be subject to change in the future
                {
                    throw new ExpressionException(e, t,
                            "Incorrect number of arguments for function " + name +
                                    ". Should be " + arguments.size() + ", not " + lazyParams.size() + " like " + arguments
                    );
                }
                Context newFrame = c.recreate();

                for (String global : globals) {
                    LazyValue lv = c.getVariable(global);
                    if (lv == null) {
                        Value zero = Value.ZERO.reboundedTo(global);
                        newFrame.setVariable(global, (cc, tt) -> zero);
                    } else {
                        newFrame.setVariable(global, lv);
                    }
                }
                for (int i = 0; i < arguments.size(); i++) {
                    String arg = arguments.get(i);
                    Value val = lazyParams.get(i).evalValue(c).reboundedTo(arg);
                    newFrame.setVariable(arg, (cc, tt) -> val);
                }
                Value retVal;
                boolean rethrow = false;
                try {
                    retVal = code.evalValue(newFrame, type); // todo not sure if we need to propagate type / consider boolean context in defined functions - answer seems ye
                } catch (ReturnStatement returnStatement) {
                    retVal = returnStatement.retval;
                } catch (ThrowStatement throwStatement) {
                    retVal = throwStatement.retval;
                    rethrow = true;
                } catch (InternalExpressionException exc) {
                    throw new ExpressionException(function_context, t, exc.getMessage());
                } catch (ArithmeticException exc) {
                    throw new ExpressionException(function_context, t, "Your math is wrong, " + exc.getMessage());
                }
                for (String global : globals) {
                    LazyValue lv = newFrame.getVariable(global);
                    if (lv != null) {
                        c.setVariable(global, lv);
                    }
                }
                if (rethrow) {
                    throw new ThrowStatement(retVal);
                }
                Value otherRetVal = retVal;
                return (cc, tt) -> otherRetVal;
            }
        });
    }

    private List<Tokenizer.Token> shuntingYard() {
        List<Tokenizer.Token> outputQueue = new ArrayList<>();
        Stack<Tokenizer.Token> stack = new Stack<>();

        Tokenizer tokenizer = new Tokenizer(this, expression, false, true);

        Tokenizer.Token lastFunction = null;
        Tokenizer.Token previousToken = null;
        while (tokenizer.hasNext()) {
            Tokenizer.Token token;
            try {
                token = tokenizer.next();
            } catch (StringIndexOutOfBoundsException e) {
                throw new ExpressionException("Script ended prematurely");
            }
            switch (token.type) {
                case STRING:
                case LITERAL:
                case HEX_LITERAL: {
                    if (previousToken != null && (
                            previousToken.type == Tokenizer.Token.TokenType.LITERAL ||
                                    previousToken.type == Tokenizer.Token.TokenType.HEX_LITERAL ||
                                    previousToken.type == Tokenizer.Token.TokenType.STRING)) {
                        throw new ExpressionException(this, token, "Missing operator");
                    }
                    outputQueue.add(token);
                    break;
                }
                case VARIABLE:
                    outputQueue.add(token);
                    break;
                case FUNCTION: {
                    stack.push(token);
                    lastFunction = token;
                    break;
                }
                case COMMA: {
                    if (previousToken != null && previousToken.type == Tokenizer.Token.TokenType.OPERATOR) {
                        throw new ExpressionException(this, previousToken, "Missing parameter(s) for operator ");
                    }
                    while (!stack.isEmpty() && stack.peek().type != Tokenizer.Token.TokenType.OPEN_PAREN) {
                        outputQueue.add(stack.pop());
                    }
                    if (stack.isEmpty()) {
                        if (lastFunction == null) {
                            throw new ExpressionException(this, token, "Unexpected comma");
                        } else {
                            throw new ExpressionException(this, lastFunction, "Parse error for function");
                        }
                    }
                    break;
                }
                case OPERATOR: {
                    if (previousToken != null
                            && (previousToken.type == Tokenizer.Token.TokenType.COMMA || previousToken.type == Tokenizer.Token.TokenType.OPEN_PAREN)) {
                        throw new ExpressionException(this, token, "Missing parameter(s) for operator '" + token + "'");
                    }
                    ILazyOperator o1 = operators.get(token.surface);
                    if (o1 == null) {
                        throw new ExpressionException(this, token, "Unknown operator '" + token + "'");
                    }

                    shuntOperators(outputQueue, stack, o1);
                    stack.push(token);
                    break;
                }
                case UNARY_OPERATOR: {
                    if (previousToken != null && previousToken.type != Tokenizer.Token.TokenType.OPERATOR
                            && previousToken.type != Tokenizer.Token.TokenType.COMMA && previousToken.type != Tokenizer.Token.TokenType.OPEN_PAREN) {
                        throw new ExpressionException(this, token, "Invalid position for unary operator " + token);
                    }
                    ILazyOperator o1 = operators.get(token.surface);
                    if (o1 == null) {
                        throw new ExpressionException(this, token, "Unknown unary operator '" + token.surface.substring(0, token.surface.length() - 1) + "'");
                    }

                    shuntOperators(outputQueue, stack, o1);
                    stack.push(token);
                    break;
                }
                case OPEN_PAREN: {
                    if (previousToken != null) {
                        if (previousToken.type == Tokenizer.Token.TokenType.LITERAL || previousToken.type == Tokenizer.Token.TokenType.CLOSE_PAREN
                                || previousToken.type == Tokenizer.Token.TokenType.VARIABLE
                                || previousToken.type == Tokenizer.Token.TokenType.HEX_LITERAL) {
                            // Implicit multiplication, e.g. 23(a+b) or (a+b)(a-b)
                            Tokenizer.Token multiplication = new Tokenizer.Token();
                            multiplication.append("*");
                            multiplication.type = Tokenizer.Token.TokenType.OPERATOR;
                            stack.push(multiplication);
                        }
                        // if the ( is preceded by a valid function, then it
                        // denotes the start of a parameter list
                        if (previousToken.type == Tokenizer.Token.TokenType.FUNCTION) {
                            outputQueue.add(token);
                        }
                    }
                    stack.push(token);
                    break;
                }
                case CLOSE_PAREN: {
                    if (previousToken != null && previousToken.type == Tokenizer.Token.TokenType.OPERATOR) {
                        throw new ExpressionException(this, previousToken, "Missing parameter(s) for operator " + previousToken);
                    }
                    while (!stack.isEmpty() && stack.peek().type != Tokenizer.Token.TokenType.OPEN_PAREN) {
                        outputQueue.add(stack.pop());
                    }
                    if (stack.isEmpty()) {
                        throw new ExpressionException("Mismatched parentheses");
                    }
                    stack.pop();
                    if (!stack.isEmpty() && stack.peek().type == Tokenizer.Token.TokenType.FUNCTION) {
                        outputQueue.add(stack.pop());
                    }
                    break;
                }
                case MARKER: {
                    if ("$".equals(token.surface)) {
                        StringBuilder sb = new StringBuilder(expression);
                        sb.setCharAt(token.pos, '\n');
                        expression = sb.toString();
                    }
                    break;
                }
            }
            if (token.type != Tokenizer.Token.TokenType.MARKER) previousToken = token;
        }

        while (!stack.isEmpty()) {
            Tokenizer.Token element = stack.pop();
            if (element.type == Tokenizer.Token.TokenType.OPEN_PAREN || element.type == Tokenizer.Token.TokenType.CLOSE_PAREN) {
                throw new ExpressionException(this, element, "Mismatched parentheses");
            }
            outputQueue.add(element);
        }
        return outputQueue;
    }

    private void shuntOperators(List<Tokenizer.Token> outputQueue, Stack<Tokenizer.Token> stack, ILazyOperator o1) {
        Tokenizer.Token nextToken = stack.isEmpty() ? null : stack.peek();
        while (nextToken != null
                && (nextToken.type == Tokenizer.Token.TokenType.OPERATOR
                || nextToken.type == Tokenizer.Token.TokenType.UNARY_OPERATOR)
                && ((o1.isLeftAssoc() && o1.getPrecedence() <= operators.get(nextToken.surface).getPrecedence())
                || (o1.getPrecedence() < operators.get(nextToken.surface).getPrecedence()))) {
            outputQueue.add(stack.pop());
            nextToken = stack.isEmpty() ? null : stack.peek();
        }
    }

    public void displayOutput() {
        displayOutput(System.out::println);
    }

    public void displayOutput(Consumer<String> printerFunction) {//Displays the output, i.e the finally evaluated expression.
        try {
            printFunction.accept(" = " + eval(Context.simpleParse()).getString());
        } catch (ExpressionException e) {
            throw new ScarpetExpressionException(e.getMessage());
        } catch (ArithmeticException ae) {
            throw new ScarpetExpressionException("math doesn't compute... " + ae.getMessage());
        }
    }

    Value eval(Context c) {
        return eval(c, Context.NONE);
    }

    private Value eval(Context c, Integer expectedType) {
        if (ast == null) {
            ast = getAST();
        }
        return evalValue(() -> ast, c, expectedType);
    }

    private LazyValue getAST() {
        Stack<LazyValue> stack = new Stack<>();
        List<Tokenizer.Token> rpn = shuntingYard();
        validate(rpn);
        for (final Tokenizer.Token token : rpn) {
            switch (token.type) {
                case UNARY_OPERATOR: {
                    final LazyValue value = stack.pop();
                    LazyValue result = (c, t) -> operators.get(token.surface).lazyEval(c, t, this, token, value, null).evalValue(c);
                    stack.push(result);
                    break;
                }
                case OPERATOR: {
                    final LazyValue v1 = stack.pop();
                    final LazyValue v2 = stack.pop();
                    LazyValue result = (c, t) -> operators.get(token.surface).lazyEval(c, t, this, token, v2, v1).evalValue(c);
                    stack.push(result);
                    break;
                }
                case VARIABLE:
                    stack.push((c, t) -> {
                        if (!c.isAVariable(token.surface)) // new variable
                        {
                            c.setVariable(token.surface, (cc, tt) -> Value.ZERO.reboundedTo(token.surface));
                        }
                        LazyValue lazyVariable = c.getVariable(token.surface);
                        return lazyVariable.evalValue(c);
                    });
                    break;
                case FUNCTION: {
                    String name = token.surface.toLowerCase(Locale.ROOT);
                    ILazyFunction f;
                    ArrayList<LazyValue> p;
                    boolean isKnown = functions.containsKey(name); // globals will be evaluated lazily, not at compile time via .
                    if (isKnown) {
                        f = functions.get(name);
                        p = new ArrayList<>(!f.numParamsVaries() ? f.getNumParams() : 0);
                    } else // potentially unknown function or just unknown function
                    {
                        f = functions.get(".");
                        p = new ArrayList<>();
                    }
                    // pop parameters off the stack until we hit the start of
                    // this function's parameter list
                    while (!stack.isEmpty() && stack.peek() != LazyValue.PARAMS_START) {
                        p.add(0, stack.pop());
                    }
                    if (!isKnown) p.add((c, t) -> new StringValue(name));
                    if (stack.peek() == LazyValue.PARAMS_START) {
                        stack.pop();
                    }
                    stack.push((c, t) -> f.lazyEval(c, t, this, token, p).evalValue(c));
                    break;
                }
                case OPEN_PAREN:
                    stack.push(LazyValue.PARAMS_START);
                    break;
                case LITERAL:
                    stack.push((c, t) ->
                    {
                        try {
                            return new NumericValue(token.surface);
                        } catch (NumberFormatException exception) {
                            throw new ExpressionException(this, token, "Not a number");
                        }
                    });
                    break;
                case STRING:
                    stack.push((c, t) -> new StringValue(token.surface)); // was originally null
                    break;
                case HEX_LITERAL:
                    stack.push((c, t) -> new NumericValue(new BigInteger(token.surface.substring(2), 16).doubleValue()));
                    break;
                default:
                    throw new ExpressionException(this, token, "Unexpected token '" + token.surface + "'");
            }
        }
        return stack.pop();
    }

    private void validate(List<Tokenizer.Token> rpn) {
        /*-
         * Thanks to Norman Ramsey:
         * http://http://stackoverflow.com/questions/789847/postfix-notation-validation
         */
        // each push on to this stack is a new function scope, with the value of
        // each
        // layer on the stack being the count of the number of parameters in
        // that scope
        Stack<Integer> stack = new Stack<>();

        // push the 'global' scope
        stack.push(0);

        for (final Tokenizer.Token token : rpn) {
            switch (token.type) {
                case UNARY_OPERATOR:
                    if (stack.peek() < 1) {
                        throw new ExpressionException(this, token, "Missing parameter(s) for operator " + token);
                    }
                    break;
                case OPERATOR:
                    if (stack.peek() < 2) {
                        if (token.surface.equalsIgnoreCase(";")) {
                            throw new ExpressionException(this, token, "Unnecessary semicolon");
                        }
                        throw new ExpressionException(this, token, "Missing parameter(s) for operator " + token);
                    }
                    // pop the operator's 2 parameters and add the result
                    stack.set(stack.size() - 1, stack.peek() - 2 + 1);
                    break;
                case FUNCTION:
                    ILazyFunction f = functions.get(token.surface.toLowerCase(Locale.ROOT));// don't validate global - userdef functions
                    int numParams = stack.pop();
                    if (f != null && !f.numParamsVaries() && numParams != f.getNumParams()) {
                        throw new ExpressionException(this, token, "Function " + token + " expected " + f.getNumParams() + " parameters, got " + numParams);
                    }
                    if (stack.size() <= 0) {
                        throw new ExpressionException(this, token, "Too many function calls, maximum scope exceeded");
                    }
                    // push the result of the function
                    stack.set(stack.size() - 1, stack.peek() + 1);
                    break;
                case OPEN_PAREN:
                    stack.push(0);
                    break;
                default:
                    stack.set(stack.size() - 1, stack.peek() + 1);
            }
        }

        if (stack.size() > 1) {
            throw new ExpressionException("Too many unhandled function parameter lists");
        } else if (stack.peek() > 1) {
            throw new ExpressionException("Too many numbers or variables");
        } else if (stack.peek() < 1) {
            throw new ExpressionException("Empty expression");
        }
    }
}
