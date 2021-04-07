package carpet.script.value;


import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.NBTBase;

public class StringValue extends Value
{
    public static Value EMPTY = StringValue.of("");

    private String str;

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

    public static Value of(String value)
    {
        if (value == null) return Value.NULL;
        return new StringValue(value);
    }

    @Override
    public String getTypeString()
    {
        return "string";
    }

    @Override
    public NBTBase toTag(boolean force) {
        return new NBTTagString(str);
    }
}
