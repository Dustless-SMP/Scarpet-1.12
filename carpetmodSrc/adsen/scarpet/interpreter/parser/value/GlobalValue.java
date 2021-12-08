package adsen.scarpet.interpreter.parser.value;

public class GlobalValue extends Value
{
    public GlobalValue(Value variable)
    {
        variable.assertAssignable();
        this.boundVariable = variable.boundVariable;
    }



    @Override
    public String getString()
    {
        return boundVariable;
    }

    @Override
    public String getTypeString() {
        return "global variable";
    }

    @Override
    public boolean getBoolean()
    {
        return false;
    }
}
