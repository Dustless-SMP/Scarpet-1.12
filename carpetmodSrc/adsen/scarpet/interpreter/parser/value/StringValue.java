package adsen.scarpet.interpreter.parser.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagString;

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

    @Override
    public JsonElement toJson(){
        return new JsonPrimitive(str);
    }

    @Override
    public NBTBase toNbt(){
        return new NBTTagString(str);
    }

    public StringValue(String str)
    {
        this.str = str;
    }
}
