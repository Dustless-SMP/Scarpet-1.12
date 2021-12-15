package adsen.scarpet.interpreter.parser.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NullValue extends NumericValue {

    public NullValue() {
        super(0.0D);
    }

    @Override
    public String getTypeString() {
        return "null";
    }

    @Override
    public String getString() {
        return "null";
    }

    @Override
    public String getPrettyString() {
        return "null";
    }

    @Override
    public boolean getBoolean() {
        return false;
    }

    @Override
    public Value clone() {
        return new NullValue();
    }

    @Override
    public boolean equals(final Value o) {
        return o instanceof NullValue;
    }

    @Override
    public int compareTo(Value o) {
        return o instanceof NullValue ? 0 : -1;
    }

    @Override
    public JsonElement toJson(){
        return new JsonPrimitive(Double.NaN);
    }
}
