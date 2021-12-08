package adsen.scarpet.interpreter.parser.value;

public class StringValue extends Value
{
    private final String str;

    public static Value of(String s) {
        return new StringValue(s);
    }

    @Override
    public String getTypeString(){
        return "string";
    }

    @Override
    public String getString() {
        return str;
    }

    @Override
    public boolean getBoolean() {
        return str != null && !str.isEmpty();
    }

    @Override
    public Value clone()
    {
        return new StringValue(str);
    }

    public StringValue(String str)
    {
        this.str = str;
    }
}
