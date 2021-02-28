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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Expression {

    public static final Logger LOGGER = LogManager.getLogger("[SCARPET]");

    public static final Expression none = new Expression("null");

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
    public List<Tokenizer.Token> rpn = new ArrayList<>(); // this is after shunting the yard (so with call to shuntingYard()
    public Value evalValue;

    public Expression(String expression){

        Operators.apply(this); //now I get wtf this means!
        //todo rest of functions/operators here before evaluation


        this.expression = expression.replaceAll("\\r\\n?", "\n").replaceAll("\\t","   ");
        this.rpn = shuntingYard();
        this.evalValue = evaluate();
    }

    public List<Tokenizer.Token> shuntingYard(){ // converting from sequence of tokens to RPN (Reverse Polish Notation) so it can be evaluated easier (https://en.wikipedia.org/wiki/Shunting-yard_algorithm)

        List<Tokenizer.Token> outputQueue = new ArrayList<>();
        Stack<Tokenizer.Token> stack = new Stack<>();


        Tokenizer tokenizer = new Tokenizer(this.expression);
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
                    outputQueue.add(token);

                    stack.push(token);

                    lastFunction = token;
                    break;
                case COMMA:
                    if(previousToken != null && previousToken.type == Tokenizer.Token.TokenType.OPERATOR)
                        throw new InternalExpressionException("Missing parameter(s) for operator '" + previousToken.surface +"'");

                    while(!stack.isEmpty() && stack.peek().type != Tokenizer.Token.TokenType.LPAR)
                        outputQueue.add(stack.pop());

                    if(stack.isEmpty()){
                        if(lastFunction == null)
                            throw new InternalExpressionException("Unexpected comma");
                        else
                            throw new InternalExpressionException("Parse error for function " + lastFunction.surface);
                    }
                    break;
                case OPERATOR:
                case UNARY_OPERATOR:
                    if (previousToken != null && (previousToken.type == Tokenizer.Token.TokenType.COMMA || previousToken.type == Tokenizer.Token.TokenType.LPAR))
                        throw new InternalExpressionException("Missing parameter(s) for operator '" + token.surface +"'");

                    Fluff.Operator o = operators.getOrDefault(token.surface, null);
                    if(o == null)
                        throw new InternalExpressionException("Unknown operator '" + token.surface + "'");

                    Tokenizer.Token nextToken = stack.isEmpty() ? null : stack.peek();

                    while(nextToken!= null &&
                            (nextToken.type == Tokenizer.Token.TokenType.OPERATOR || nextToken.type == Tokenizer.Token.TokenType.UNARY_OPERATOR) &&
                            ((o.isLeftAssoc() && o.getPrecedence() <= operators.get(nextToken.surface).getPrecedence()) || (o.getPrecedence() < operators.get(nextToken.surface).getPrecedence()))
                        ){
                        outputQueue.add(stack.pop());
                        nextToken = stack.isEmpty() ? null : stack.peek();
                    }
                    stack.push(token);
                    break;

                case LPAR:
                    stack.push(token);
                    break;

                case RPAR:
                    Tokenizer.Token lastParen = stack.isEmpty()? null : stack.peek();

                    while(lastParen!=null && lastParen.type != Tokenizer.Token.TokenType.LPAR){
                        outputQueue.add(stack.pop());
                        lastParen = stack.isEmpty() ? null : stack.peek();
                    }

                    if(lastParen == null)
                        throw new InternalExpressionException("Mismatched parentheses");
                    stack.pop(); // discarding the pair of brackets, but only after checking that there are no excess right parentheses, in which case we oops.
                    break;

                default:
                    break;
            }
            if (token.type != Tokenizer.Token.TokenType.MARKER) previousToken = token;
        }

        while(!stack.isEmpty()){
            outputQueue.add(stack.pop());
        }

        return outputQueue;
    }

    /**
     * This is the method which evaluates the RPN expression returned by {@code shuntingYard()} method.
     * It works very simply: At this stage, there are only literals(strings and numbers), functions (unimplemented),
     * variables and operators (which for all intents and purposes I can treat as fancy functions as well). So when I am
     * running through, I push all literals onto the stack. Then when I see a function, I look at the stack to give it
     * its arguments (how many ever it needs). I can then send those to the function to be evaluated (and throw appropriate
     * errors), and put the output onto the stack(cos its gonna be a literal). Then I just continue, and at the end, I
     * have one item on the stack, and that's my final answer, which I can output to the player/user.
     */

    private Value evaluate(){
        Stack<Value> stack = new Stack<>();

        //To push arguments for functions, let's see if it works...

        for(Tokenizer.Token token: this.rpn){
            switch (token.type){
                case STRING:
                    StringValue stringValue = new StringValue(token.surface);

                    stack.push(stringValue);
                    break;
                case NUM:
                    NumericValue numVal;
                    try{
                        numVal = new NumericValue(token.surface);
                    } catch (NumberFormatException nfe) {
                        throw new InternalExpressionException("Not a Number");
                    }
                    stack.push(numVal);
                    break;

                case HEX_NUM:
                    NumericValue hexVal = new NumericValue(new BigInteger(token.surface.substring(2), 16).doubleValue());
                    stack.push(hexVal);
                    break;

                case VAR: //If variable is undefined, set to 0 default value, if not get its value
                    stack.push(getOrSetAnyVariable(token.surface));
                    break;

                case UNARY_OPERATOR: {
                    final Value value = stack.pop();
                    Value result = operators.get(token.surface).apply(value);
                    stack.push(result);
                    break;
                }

                case OPERATOR:
                    Value value2 = stack.pop(); //cos second value is inputted last
                    Value value1 = stack.pop();

                    Fluff.Operator o = operators.get(token.surface);
                    LOGGER.info(o.getSurface());
                    Value result = o.apply(value1, value2);
                    stack.push(result);
                    break;

                case LPAR: //todo figure this out (if its at all necessary)
                    //functionDepth++;
                    //break;

                case FUNC:
                    //if(!isAFunction(token.surface)) //todo function definitions
                    //    throw new InternalExpressionException("Invalid function '"+token.surface+"'");

                    LOGGER.error("Unsupported operation (so far) (Attempted to parse '"+token.type.name()+"')");


                    break;

                default:
                    throw new InternalExpressionException("How is this token even here? "+token.toString());
            }

        }
        return stack.pop();
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
