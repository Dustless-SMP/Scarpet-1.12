package adsen.scarpet.interpreter.parser.value;

import adsen.scarpet.interpreter.parser.exception.ExpressionException;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;

import java.util.List;

public class FunctionSignatureValue extends Value
{
    private String identifier;
    private List<String> arguments;
    private List<String> globals;

    @Override
    public String getString()
    {
        throw new ExpressionException("Function "+identifier+" is not defined yet");
    }

    @Override
    public String getTypeString() {
        throw new InternalExpressionException("Cannot query type of scarpet language component");
    }

    @Override
    public boolean getBoolean()
    {
        throw new ExpressionException("Function "+identifier+" is not defined yet");
    }

    @Override
    public Value clone()
    {
        throw new ExpressionException("Function "+identifier+" is not defined yet");
    }
    public FunctionSignatureValue(String name, List<String> args, List<String> globals)
    {
        this.identifier = name;
        this.arguments = args;
        this.globals = globals;
    }
    public String getName()
    {
        return identifier;
    }
    public List<String> getArgs()
    {
        return arguments;
    }
    public List<String> getGlobals() {return globals;}


}
