package adsen.scarpet.interpreter.parser.value;

public class BooleanValue extends NumericValue
{
    public static final BooleanValue FALSE = new BooleanValue(false);
    public static final BooleanValue TRUE = new BooleanValue(true);

    boolean boolValue;
    private BooleanValue(boolean boolval) {
        super(boolval ? 1L : 0L);
        boolValue = boolval;
    }

    public static BooleanValue of(boolean value)
    {
        return value ? TRUE : FALSE;
    }

    @Override
    public String getString() {
        return boolValue?"true":"false";
    }

    @Override
    public String getPrettyString() {
        return getString();
    }

    @Override
    public String getTypeString() {
        return "bool";
    }

    @Override
    public Value clone() {
        return new BooleanValue(boolValue);
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(boolValue);
    }
    //todo json
    //@Override
    //public JsonElement toJson() {
    //    return new JsonPrimitive(boolValue);
    //}
    //todo isInteger
    //@Override
    //public boolean isInteger() {
    //    return true;
    //}
}