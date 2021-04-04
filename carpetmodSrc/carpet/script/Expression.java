package carpet.script;

import carpet.script.exception.InternalExpressionException;
import carpet.script.language.Operators;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Expression {

    public static final Logger LOGGER = LogManager.getLogger("[SCARPET]");

    public static final Expression none = new Expression("null", false, false);

    public Map<String, Fluff.Operator> operators = new HashMap<>();
    public boolean isAnOperator(String s){
        return operators.containsKey(s);
    }
    public Map<String, Value> variables = new HashMap<>();
    public Map<String, Value> globalVariables = new HashMap<>();
    public boolean isAVariable(String s){
        return variables.containsKey(s) || globalVariables.containsKey(s);
    }

    public Value getOrSetAnyVariable(String name){
        if(globalVariables.containsKey(name))
            return globalVariables.get(name);
        if(variables.containsKey(name))
            return variables.get(name);
        Value val = Value.ZERO;
        setAnyVariable(name, val);
        return val;
    }

    public Value setAnyVariable(String name, Value v){
        if(name.startsWith("global_"))
            globalVariables.put(name, v);
        else
            variables.put(name, v);
        return v;
    }

    public Map<String, Fluff.Functions> functions = new HashMap<>();
    public boolean isAFunction(String s){
        return functions.containsKey(s);
    }
    public Set<String> getFunctionNames() {
        return functions.keySet();
    }


    public String expression;
    public Value ast;
    public Value evalValue;

    public boolean allowComments;
    public boolean allowNewLineMarkers;

    public Expression(String expression, boolean comments, boolean newLineMarkers){

        allowComments = comments;
        allowNewLineMarkers = newLineMarkers;

        Operators.apply(this); //now I get wtf this means!
        //todo rest of functions/operators here before evaluation

        //actually running it
        this.expression = expression;
        this.evalValue = evaluate();
    }

    public List<Tokenizer.Token> shuntingYard(){ // converting from sequence of tokens to RPN (Reverse Polish Notation) so it can be evaluated easier (https://en.wikipedia.org/wiki/Shunting-yard_algorithm)

        List<Tokenizer.Token> outputQueue = new ArrayList<>();
        Stack<Tokenizer.Token> stack = new Stack<>();


        Tokenizer tokenizer = new Tokenizer(this, expression, allowComments, allowNewLineMarkers);
        List<Tokenizer.Token> cleanedTokens = tokenizer.postProcess(); //to get rid of comments (not implemented yet), excess semicolons and $ signs for new lines.
        Tokenizer.Token lastFunction = null; // todo once I implement the basic maths parser
        Tokenizer.Token previousToken = null;

        for(Tokenizer.Token token : cleanedTokens){
            switch (token.type){
                case STRING:
                case NUM:
                case HEX_NUM:
                    if (previousToken != null && (previousToken.type == Tokenizer.Token.TokenType.NUM ||
                            previousToken.type == Tokenizer.Token.TokenType.STRING))
                        throw new InternalExpressionException("Missing operator");
                    outputQueue.add(token);
                    break;
                case VAR: //todo worry about variable and functions properly later
                    outputQueue.add(token);
                    break;
                case FUNC://not checking properly here incase were assigning
                    stack.push(token);
                    lastFunction = token;
                    break;
                case COMMA:
                    if(previousToken != null && previousToken.type == Tokenizer.Token.TokenType.OPERATOR)
                        throw new InternalExpressionException("Missing parameter(s) for operator '" + previousToken.surface +"'");

                    while(!stack.isEmpty() && stack.peek().type != Tokenizer.Token.TokenType.OPEN_PAR)
                        outputQueue.add(stack.pop());

                    if(stack.isEmpty()){
                        if(lastFunction == null)
                            throw new InternalExpressionException("Unexpected comma");
                        else
                            throw new InternalExpressionException("Parse error for function " + lastFunction.surface);
                    }
                    break;
                case OPERATOR: {
                    if (previousToken != null
                            && (previousToken.type == Tokenizer.Token.TokenType.COMMA || previousToken.type == Tokenizer.Token.TokenType.OPEN_PAR)) {
                        throw new InternalExpressionException("Missing parameter(s) for operator '" + token + "'");
                    }
                    Fluff.Operator o1 = operators.get(token.surface);
                    if (o1 == null)
                        throw new InternalExpressionException("Unknown operator '" + token + "'");

                    shuntOperators(outputQueue, stack, o1);
                    stack.push(token);
                    break;
                }

                case UNARY_OPERATOR: {
                    if (previousToken != null && previousToken.type != Tokenizer.Token.TokenType.OPERATOR
                            && previousToken.type != Tokenizer.Token.TokenType.COMMA && previousToken.type != Tokenizer.Token.TokenType.OPEN_PAR)
                        throw new InternalExpressionException("Invalid position for unary operator " + token);

                    Fluff.Operator o1 = operators.get(token.surface);
                    if (o1 == null)
                        throw new InternalExpressionException("Unknown unary operator '" + token.surface.substring(0, token.surface.length() - 1) + "'");

                    shuntOperators(outputQueue, stack, o1);
                    stack.push(token);
                    break;
                }

                case OPEN_PAR:
                    if (previousToken != null) {
                        if (previousToken.type == Tokenizer.Token.TokenType.NUM || previousToken.type == Tokenizer.Token.TokenType.CLOSE_PAR
                                || previousToken.type == Tokenizer.Token.TokenType.VAR
                                || previousToken.type == Tokenizer.Token.TokenType.HEX_NUM) {
                            // Implicit multiplication, e.g. 23(a+b) or (a+b)(a-b)
                            Tokenizer.Token multiplication = new Tokenizer.Token();
                            multiplication.append("*");
                            multiplication.type = Tokenizer.Token.TokenType.OPERATOR;
                            stack.push(multiplication);
                        }
                        // if the ( is preceded by a valid function, then it
                        // denotes the start of a parameter list
                        if (previousToken.type == Tokenizer.Token.TokenType.FUNC)
                            outputQueue.add(token);

                    }
                    stack.push(token);
                    break;

                case CLOSE_PAR:
                    if (previousToken != null && previousToken.type == Tokenizer.Token.TokenType.OPERATOR)
                        throw new InternalExpressionException("Missing parameter(s) for operator " + previousToken);

                    while (!stack.isEmpty() && stack.peek().type != Tokenizer.Token.TokenType.OPEN_PAR)
                        outputQueue.add(stack.pop());

                    if (stack.isEmpty())
                        throw new InternalExpressionException("Mismatched parentheses");

                    stack.pop();
                    if (!stack.isEmpty() && stack.peek().type == Tokenizer.Token.TokenType.FUNC)
                    {
                        outputQueue.add(stack.pop());
                    }

                default:
                    break;
            }
            if (token.type != Tokenizer.Token.TokenType.MARKER) previousToken = token;
        }

        while (!stack.isEmpty()) {
            Tokenizer.Token element = stack.pop();
            if (element.type == Tokenizer.Token.TokenType.OPEN_PAR || element.type == Tokenizer.Token.TokenType.CLOSE_PAR)
                throw new InternalExpressionException("Mismatched parentheses");

            outputQueue.add(element);
        }

        return outputQueue;
    }

    private void shuntOperators(List<Tokenizer.Token> outputQueue, Stack<Tokenizer.Token> stack, Fluff.Operator o1){
        Tokenizer.Token nextToken = stack.isEmpty() ? null : stack.peek();
        while (nextToken != null
                && (nextToken.type == Tokenizer.Token.TokenType.OPERATOR
                || nextToken.type == Tokenizer.Token.TokenType.UNARY_OPERATOR)
                && ((o1.isLeftAssoc() && o1.getPrecedence() <= operators.get(nextToken.surface).getPrecedence())
                || (o1.getPrecedence() < operators.get(nextToken.surface).getPrecedence()))){
            outputQueue.add(stack.pop());
            nextToken = stack.isEmpty() ? null : stack.peek();
        }
    }


    private Value getAST(){
        Stack<Value> stack = new Stack<>();
        List<Tokenizer.Token> rpn = shuntingYard();

        validate(rpn);
        for(final Tokenizer.Token token : rpn){
            switch (token.type){
                case UNARY_OPERATOR:{
                    final Value value = stack.pop();
                    Value result = operators.get(token.surface).apply(value);
                    stack.push(result);
                    break;
                }
                case OPERATOR:
                    final Value v1 = stack.pop();
                    final Value v2 = stack.pop();
                    Value result = operators.get(token.surface).apply(v1, v2);
                    stack.push(result);
                    break;

                case VAR:
                    stack.push(getOrSetAnyVariable(token.surface));
                    break;

                case FUNC:
                    String name = token.surface.toLowerCase(Locale.ROOT);
                    Fluff.Functions f;
                    ArrayList<Value> p;
                    boolean isKnown = functions.containsKey(name);
                    if(isKnown){
                        f = functions.get(name);
                        p = new ArrayList<>(!f.numParamsVaries() ? f.getNumParams() : 0);
                    } else { //potentially unknown function or perhaps a declaration
                        f = functions.get(".");
                        p = new ArrayList<>();
                    }
                    //pop parameters off the stack until we hit the start of this function's parameter list
                    while(!stack.isEmpty() && stack.peek()!= Value.PARAMS_START)
                        p.add(0, stack.pop());

                    if(!isKnown) p.add(new StringValue(name));

                    if(stack.peek() == Value.PARAMS_START)
                        stack.pop();

                    stack.push(f.apply(p));
                    break;

                case OPEN_PAR:
                    stack.push(Value.PARAMS_START);
                     break;
                case NUM:
                    try{
                        stack.push(new NumericValue(token.surface));
                    }
                    catch (NumberFormatException exception) {
                        throw new InternalExpressionException("Not a number");
                    }
                    break;

                case STRING:
                    stack.push(new StringValue(token.surface)); // was originally null
                    break;
                case HEX_NUM:
                    stack.push(new NumericValue(new BigInteger(token.surface.substring(2), 16).doubleValue()));
                    break;
                default:
                    throw new InternalExpressionException("Unexpected token '" + token.surface + "'");
            }
        }
        return stack.pop();
    }

    /**
     * Thanks to Norman Ramsey:
     * <a href="http://stackoverflow.com/questions/789847/postfix-notation-validation">Postfix Notation Validation</a>
     */

    private void validate(List<Tokenizer.Token> rpn) {

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
                    if (stack.peek() < 1)
                    {
                        throw new InternalExpressionException("Missing parameter(s) for operator " + token);
                    }
                    break;
                case OPERATOR:
                    if (stack.peek() < 2){
                        if (token.surface.equalsIgnoreCase(";"))
                            throw new InternalExpressionException("Unnecessary semicolon");

                        throw new InternalExpressionException("Missing parameter(s) for operator " + token);
                    }
                    // pop the operator's 2 parameters and add the result
                    stack.set(stack.size() - 1, stack.peek() - 2 + 1);
                    break;
                case FUNC:
                    Fluff.Functions f = functions.get(token.surface.toLowerCase(Locale.ROOT));// don't validate global - userdef functions
                    int numParams = stack.pop();
                    if (f != null && !f.numParamsVaries() && numParams != f.getNumParams())
                        throw new InternalExpressionException("Function " + token + " expected " + f.getNumParams() + " parameters, got " + numParams);

                    if (stack.size() <= 0)
                        throw new InternalExpressionException("Too many function calls, maximum scope exceeded");

                    // push the result of the function
                    stack.set(stack.size() - 1, stack.peek() + 1);
                    break;
                case OPEN_PAR:
                    stack.push(0);
                    break;
                default:
                    stack.set(stack.size() - 1, stack.peek() + 1);
            }
        }

        if (stack.size() > 1)
            throw new InternalExpressionException("Too many unhandled function parameter lists");
        else if (stack.peek() > 1)
            throw new InternalExpressionException("Too many numbers or variables");
        else if (stack.peek() < 1)
            throw new InternalExpressionException("Empty expression");
    }

    private Value evaluate(){
        if(this.ast==null)
            this.ast = getAST();
        return this.ast;
    }

    public void addBinaryOperator(String surface, int precedence, boolean leftAssoc, BiFunction<Value, Value, Value> fun) {
        operators.put(surface, new Fluff.Operator(
                surface,
                precedence,
                2,
                leftAssoc,
                values -> fun.apply(values.get(0), values.get(1))
        ));
    }

    public void addUnaryOperator(String surface, int precedence, boolean leftAssoc, Function<Value, Value> fun){
        operators.put(surface, new Fluff.Operator(
                surface,
                precedence,
                1,
                leftAssoc,
                values -> fun.apply(values.get(0))
        ));
    }

    public void addUnaryFunction(String name, Function<Value, Value> fun){
        functions.put(name, new Fluff.Functions(
                name,
                1,
                values -> fun.apply(values.get(0))
        ));
    }

    public void addBuiltInFunction(String name, int args, Function<List<Value>, Value> fun){
        functions.put(name, new Fluff.Functions(
                name,
                args,
                fun
        ));
    }

    public void addUserDefinedFunction(String name, int args, int minArgs, boolean varArgs, Function<List<Value>, Value> fun){ //todo
        functions.put(name, new Fluff.Functions(
                name,
                args,
                minArgs,
                varArgs,
                fun
        ));
    }
}
