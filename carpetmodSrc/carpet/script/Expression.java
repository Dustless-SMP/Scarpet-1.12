package carpet.script;

import carpet.script.exception.TempException;
import carpet.script.value.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;

public class Expression {

    public static final Logger LOGGER = LogManager.getLogger("[SCARPET]");


    public static Map<String, Fluff.Operator> operators = new HashMap<>();
    public static boolean isAnOperator(String s){
        return operators.containsKey(s);
    }
    public static Map<String, Value> variables = new HashMap<>();
    public static boolean isAVariable(String s){
        return variables.containsKey(s);
    }
    public static Map<String, Fluff.Functions> functions = new HashMap<>();
    public static boolean isAFunction(String s){
        return functions.containsKey(s);
    }


    public String expression;
    public List<Tokenizer.Token> rpn = new ArrayList<>(); // this is after shunting the yard (so with call to shuntingYard()

    public Expression(String expression){

        Operators.apply(this); //now I get wtf this means!
        //todo rest of functions/operators here before evaluation


        this.expression = expression.trim().replaceAll("\\r\\n?", "\n").replaceAll("\\t","   ");
        this.rpn = shuntingYard();
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
                        throw new TempException("Missing operator");
                    outputQueue.add(token);
                    break;
                case VAR: //todo worry about variable and functions properly later
                    outputQueue.add(token);
                    break;
                case FUNC:
                    //stack.push(token);
                    outputQueue.add(token);
                    lastFunction = token;
                    break;
                case COMMA:
                    if(previousToken != null && previousToken.type == Tokenizer.Token.TokenType.OPERATOR)
                        throw new TempException("Missing parameter(s) for operator '" + previousToken.surface +"'");

                    while(!stack.isEmpty() && stack.peek().type != Tokenizer.Token.TokenType.LPAR)
                        outputQueue.add(stack.pop());

                    if(stack.isEmpty()){
                        if(lastFunction == null)
                            throw new TempException("Unexpected comma");
                        else
                            throw new TempException("Parse error for function " + lastFunction.surface);
                    }
                    break;
                case OPERATOR:
                case UNARY_OPERATOR:
                    if (previousToken != null && (previousToken.type == Tokenizer.Token.TokenType.COMMA || previousToken.type == Tokenizer.Token.TokenType.LPAR))
                        throw new TempException("Missing parameter(s) for operator '" + token.surface +"'");

                    Fluff.Operator o = operators.getOrDefault(token.surface, null);
                    if(o == null)
                        throw new TempException("Unknown operator '" + token.surface + "'");

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
                        throw new TempException("Mismatched parentheses");
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
     * It works very simply: At this stage, there are only literals(strings and numbers), functions and operators (which
     * for all intents and purposes I can treat as functions as well). So when I am running through, I push all literals
     * onto the stack. Then when I see a function, I look at the stack to give it its arguments (how many ever it needs).
     * I can then send those to the function to be evaluated (and throw appropriate errors), and put the output onto the
     * stack(cos its gonna be a literal). Then I just continue, and at the end, I have one item on the stack, and that's
     * my final answer. It uses The RPN expression returned from parsing the Tokenised expression.
     */

    private void evaluate(){
        Stack<Tokenizer.Token> stack = new Stack<>();

        for(Tokenizer.Token token:rpn){
            switch (token.type){
                case STRING:
                case NUM:
                case HEX_NUM:
                case LPAR: //cos I push them onto the stack to be evaluated so I can then detect them as the end of a function
                case VAR: //don't define here cos I may be assigning the variable, in which case I check while performing the operation
                    stack.push(token);
                    break;

                case OPERATOR:
                    Fluff.Operator o = operators.get(token.surface);

                    List<Value> args = new ArrayList<>();

                    for (int i = 0; i <o.getArguments(); i++) { //we already added check for number of operators, so no need here. todo more sophisticated check for functions
                        args.add(Value.fromToken(stack.pop()));
                    }
                    o.fun.apply(args);

                case FUNC:
                    if(!isAFunction(token.surface))
                        throw new TempException("Unknown function '"+token.surface+"'");


                default:
                    throw new TempException("How is this token even here?"+token.toString());
            }
        }


    }

    public static void addBinaryOperator(String surface, int precedence, boolean leftAssoc, Function<List<Value>, Value> fun) {
        operators.put(surface, new Fluff.Operator(
                surface,
                precedence,
                2,
                leftAssoc,
                fun
        ));
    }
}
