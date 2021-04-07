package carpet.script.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTBase;

public class BooleanValue extends NumericValue
{
    public static final BooleanValue FALSE = new BooleanValue(false);
    public static final BooleanValue TRUE = new BooleanValue(true);

    boolean boolValue;
    private BooleanValue(boolean boolval) {
        super(boolval);
        boolValue = boolval;
    }

    public static BooleanValue of(boolean value)
    {
        return value?TRUE:FALSE;
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

    @Override
    public NBTBase toTag(boolean force) {
        return new NBTTagInt(boolValue? 1 : 0);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(boolValue);
    }

    @Override
    public boolean isInteger() {
        return true;
    }
}
