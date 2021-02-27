package carpet.script.value;

import net.minecraft.nbt.NBTBase;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static java.lang.Math.abs;
import static java.lang.Math.signum;

public class NumericValue extends Value{

    private final Double value;
    private Long longValue;
    private final static double epsilon = abs(32*((7*0.1)*10-7));
    private final static MathContext displayRounding = new MathContext(12, RoundingMode.HALF_EVEN);

    public NumericValue(double value){
        this.value = value;
    }
    private NumericValue(double value, Long longValue){
        this.value = value;
        this.longValue = longValue;
    }

    public NumericValue(long value){
        this.longValue = value;
        this.value = (double)value;
    }

    public NumericValue(String value){
        BigDecimal decimal = new BigDecimal(value);
        if (decimal.stripTrailingZeros().scale() <= 0){
            try{
                longValue = decimal.longValueExact();
            }
            catch (ArithmeticException ignored) {}
        }
        this.value = decimal.doubleValue();
    }

    public double getDouble(){
        return this.value;
    }

    public int getInt(){
        return (int)getLong();
    }

    public long getLong(){
        return this.longValue;
    }

    @Override
    public String getString() {
        return null;
    }

    @Override
    public String getTypeString() {
        return "number";
    }

    @Override
    public boolean equals(Object o){
        //if (o instanceof NullValue){ todo
        //    return o.equals(this);
        //}
        if (o instanceof NumericValue){
            NumericValue no = (NumericValue)o;
            if (longValue != null && no.longValue != null)
                return longValue.equals(no.longValue);
            return !this.subtract(no).getBoolean();
        }
        return super.equals(o);
    }

    @Override
    public int compareTo(Value o){
        //if (o instanceof NullValue){
        //    return -o.compareTo(this);
        //}
        if (o instanceof NumericValue){
            NumericValue no = (NumericValue)o;
            if (longValue != null && no.longValue != null)
                return longValue.compareTo(no.longValue);
            return value.compareTo(no.value);
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public int length()
    {
        return Long.toString(getLong()).length();
    }

    @Override
    public Value clone()
    {
        return new NumericValue(value, longValue);
    }

    @Override
    public NBTBase toTag(boolean force) {
        return null;
    }

    public NumericValue opposite() {
        if (longValue != null) return new NumericValue(-longValue);
        return new NumericValue(-value);
    }

    public boolean isInteger()
    {
        return longValue!= null ||  getDouble() == (double)getLong();
    }
}
