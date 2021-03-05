package carpet.script.value;

import carpet.script.exception.InternalExpressionException;
import net.minecraft.nbt.NBTBase;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

import static java.lang.Math.abs;
import static java.lang.Math.signum;

public class NumericValue extends Value{

    private final Double value;
    private Long longValue;
    private final static double epsilon = abs(32*((7*0.1)*10-7));
    private final static MathContext displayRounding = new MathContext(12, RoundingMode.HALF_EVEN);

    public static NumericValue asNumber(Value v1, String id)
    {
        if (!(v1 instanceof NumericValue))
            throw new InternalExpressionException("Argument "+id+" has to be of a numeric type");
        return ((NumericValue) v1);
    }

    public static NumericValue asNumber(Value v1)
    {
        if (!(v1 instanceof NumericValue))
            throw new InternalExpressionException("Operand has to be of a numeric type");
        return ((NumericValue) v1);
    }

    public static <T extends Number> Value of(T value)
    {
        if (value == null) return Value.NULL;
        if (value.doubleValue() == value.longValue()) return new NumericValue(value.longValue());
        if (value instanceof Float) return new NumericValue(0.000_001D * Math.round(1_000_000.0D*value.doubleValue()));
        return new NumericValue(value.doubleValue());
    }

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
    public Value add(Value v){
        if (v instanceof NumericValue)
        {
            NumericValue nv = (NumericValue)v;
            if (longValue != null && nv.longValue != null)
            {
                return new NumericValue(longValue+nv.longValue);
            }
            return new NumericValue(value + nv.value);
        }
        return super.add(v);
    }

    public Value subtract(Value v) {
        if (v instanceof NumericValue)
        {
            NumericValue nv = (NumericValue)v;
            if (longValue != null && nv.longValue != null)
            {
                return new NumericValue(longValue-nv.longValue);
            }
            return new NumericValue(value - nv.value);
        }
        return super.subtract(v);
    }

    public Value multiply(Value v) {
        if (v instanceof NumericValue)
        {
            NumericValue nv = (NumericValue)v;
            if (longValue != null && nv.longValue != null)
            {
                return new NumericValue(longValue*nv.longValue);
            }
            return new NumericValue(value * nv.value);
        }
        //if (v instanceof ListValue){ todo
        //    return v.multiply(this);
        //}
        return new StringValue(StringUtils.repeat(v.getString(), (int) getLong()));
    }

    public Value divide(Value v) {
        if (v instanceof NumericValue){
            return new NumericValue(getDouble() / ((NumericValue) v).getDouble() );
        }
        return super.divide(v);
    }

    @Override
    public String getString() {
        if (longValue != null){
            return Long.toString(getLong());
        }
        try{
            if (value.isInfinite()) return "INFINITY";
            if (value.isNaN()) return "NaN";
            if (abs(value) < epsilon) return (signum(value) < 0)?"-0":"0"; //zero rounding fails with big decimals
            // dobules have 16 point precision, 12 is plenty to display
            return BigDecimal.valueOf(value).round(displayRounding).stripTrailingZeros().toPlainString();
        }
        catch (NumberFormatException exc){
            throw new InternalExpressionException("Incorrect number format for "+value);
        }
    }

    @Override
    public String getPrettyString() {
        if (longValue!= null ||  getDouble() == (double)getLong())
            return Long.toString(getLong());
        else
            return String.format(Locale.ROOT, "%.1f..", getDouble());
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
