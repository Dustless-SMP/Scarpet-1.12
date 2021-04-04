package carpet.script.value;


import carpet.script.exception.InternalExpressionException;
import net.minecraft.nbt.NBTBase;

public abstract class Value implements Comparable<Value>, Cloneable{

    public static NumericValue FALSE = new NumericValue(0);
    public static NumericValue TRUE = new NumericValue(1);
    public static NumericValue ZERO = FALSE;
    public static NumericValue ONE = TRUE;

    public static NullValue NULL = new NullValue();

    public static Value PARAMS_START = null; //This helps to figure out when the start of the parameters of a function. No other use rly

    public static final NumericValue PI = new NumericValue(
            "3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679"
    );

    public static final Value euler = new NumericValue(
            "2.71828182845904523536028747135266249775724709369995957496696762772407663"
    );

    @Override
    public int compareTo(final Value o){
        //if (o instanceof NumericValue || o instanceof ListValue || o instanceof ThreadValue) todo
        //{
        //    return -o.compareTo(this);
        //}
        return getString().compareTo(o.getString());
    }

    public Value reboundedTo(String var){
        Value copy;
        try {
            copy = (Value)clone();
        }
        catch (CloneNotSupportedException e) {
            // should not happen
            e.printStackTrace();
            throw new InternalExpressionException("Variable of type "+getTypeString()+" is not cloneable. Tell Ghoulboy (who will complain to gnembon about it), as this shoudn't happen");
        }
        return copy;
    }

    //public Value(){} Not necessary, I think... (same for clone, idk what to put)

    public void assertAssignable(){
        assertNotNull(this);
        //if (boundVariable == null || boundVariable.startsWith("_"))
        //{
        //    if (boundVariable != null)
        //    {
        //        throw new InternalExpressionException(boundVariable+ " cannot be assigned a new value");
        //    }
        //    throw new InternalExpressionException(getString()+ " is not a variable");
        //
        //}
    }

    public abstract String getString();

    public abstract String getPrettyString();

    public abstract String getTypeString();

    public abstract NBTBase toTag(boolean force);

    public boolean getBoolean(){
        return true;
    }

    public Value add(Value other){//todo
        return StringValue.of(this.getString().concat(other.getString()));
    }

    public Value subtract(Value other){
        return StringValue.of(this.getString().replace(other.getString(),""));
    }
    public Value multiply(Value v) {
        //if (v instanceof NumericValue || v instanceof ListValue) { todo implement these juicy types
        //    return v.multiply(this);
        //}
        return new StringValue(this.getString()+"."+v.getString());
    }

    public Value divide(Value v) {
        //if (v instanceof NumericValue){ todo this as well
        //    String lstr = getString();
        //    return new StringValue(lstr.substring(0, (int)(lstr.length()/ ((NumericValue) v).getDouble())));
        //}
        return new StringValue(getString()+"/"+v.getString());
    }

    public int length(){
        return this.getString().length();
    }

    public static <T> void assertNotNull(T t){
        if(t==null)
            throw new InternalExpressionException("Operand may not be null");
    }

    public static <T> void assertNotNull(T t1, T t2){
        if (t1 == null)
            throw new InternalExpressionException("First operand may not be null");
        if (t2 == null)
            throw new InternalExpressionException("Second operand may not be null");
    }
}
