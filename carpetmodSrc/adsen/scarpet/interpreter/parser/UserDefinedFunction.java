package adsen.scarpet.interpreter.parser;

import java.util.List;

public abstract class UserDefinedFunction extends Fluff.AbstractLazyFunction implements Fluff.ILazyFunction {
    protected List<String> arguments;
    public Expression expression;
    public Tokenizer.Token token;

    UserDefinedFunction(List<String> args, Expression expr, Tokenizer.Token t) {
        super(args.size());
        arguments = args;
        expression = expr;
        token = t;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public Expression getExpression() {
        return expression;
    }

    public Tokenizer.Token getToken() {
        return token;
    }
}
