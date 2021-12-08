package adsen.scarpet.interpreter.parser.value;

import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

import static java.lang.Math.abs;

public class NumericValue extends Value {

    public static final NumericValue PI = new NumericValue(
            "3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679");

    public static final NumericValue euler = new NumericValue(
            "2.71828182845904523536028747135266249775724709369995957496696762772407663");

    public static final NumericValue PHI = new NumericValue(
            "1.6180339887498948482045868343656381177203091798057628621354486227052604628189024497072072041893911374");

    public static final NumericValue avogadro = new NumericValue("602214076000000000000000");

    final static double epsilon = 1024 * Double.MIN_VALUE;

    private Double value;

    public NumericValue(double value) {
        this.value = value==0 ? 0D : value;//to get rid of -0 issue
    }

    public NumericValue(String value) {
        this(new BigDecimal(value).doubleValue());
    }

    public NumericValue(long value) {
        this.value = (double) value;
    }

    public NumericValue(boolean boolval) {
        this(boolval ? 1.0D : 0.0D);
    }

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
    @Override
    public String getString() {
        try {
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exc) {
            throw new ArithmeticException("Incorrect number format for " + value);
        }
    }

    @Override
    public String getPrettyString() {
        if (getDouble() == (double) getLong()) {
            return Long.toString(getLong());
        } else {
            return String.format("%.3f..", getDouble());
        }
    }

    @Override
    public String getTypeString() {
        return "number";
    }

    @Override
    public boolean getBoolean() {
        return value != null && abs(value) > epsilon;
    }

    public double getDouble() {
        return value;
    }

    public long getLong() {
        return (long) (value + epsilon);
    }

    @Override
    public Value add(Value v) {
        if (v instanceof NumericValue) {
            return new NumericValue(getDouble() + ((NumericValue) v).getDouble());
        }
        return super.add(v);
    }

    @Override
    public Value subtract(Value v) {
        if (v instanceof NumericValue) {
            return new NumericValue(getDouble() - (((NumericValue) v).getDouble()));
        }
        return super.subtract(v);
    }

    @Override
    public Value multiply(Value v) {
        if (v instanceof NumericValue) {
            return new NumericValue(getDouble() * ((NumericValue) v).getDouble());
        }
        if (v instanceof ListValue) {
            return v.multiply(this);
        }
        return new StringValue(StringUtils.repeat(v.getString(), (int) getLong()));
    }

    @Override
    public Value divide(Value v) {
        if (v instanceof NumericValue) {
            return new NumericValue(getDouble() / ((NumericValue) v).getDouble());
        }
        return super.divide(v);
    }

    @Override
    public Value clone() {
        return new NumericValue(value);
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof NullValue) {
            return -o.compareTo(this);
        }
        if (o instanceof NumericValue) {
            return value.compareTo(((NumericValue) o).getDouble());
        }
        return getString().compareTo(o.getString());
    }

    @Override
    public boolean equals(Value o) {
        if (o instanceof NullValue) {
            return o.equals(this);
        }
        if (o instanceof NumericValue) {
            return !this.subtract(o).getBoolean();
        }
        return super.equals(o);
    }

    @Override
    public int length() {
        return Integer.toString(value.intValue()).length();
    }

    @Override
    public double readNumber() {
        return value;
    }

    public int getInt(){
        return (int) getLong();
    }

    @Override
    public long readInteger() {
        return getLong();
    }
}
