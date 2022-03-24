package adsen.scarpet.interpreter.parser.value;

import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import com.google.gson.JsonElement;
import net.minecraft.nbt.NBTBase;

public class GlobalValue extends Value
{
    public GlobalValue(Value variable)
    {
        variable.assertAssignable();
        this.boundVariable = variable.boundVariable;
    }



    @Override
    public String getString()
    {
        return boundVariable;
    }

    @Override
    public String getTypeString() {
        return "global variable";
    }

    @Override
    public boolean getBoolean()
    {
        return false;
    }

    @Override
    public JsonElement toJson(){ //I rly dk what to do here tbh.
        throw new InternalExpressionException("If you got here, ask Ghoulboy, he'll fix it.");
    }

    @Override
    public NBTBase toNbt(){ //I rly dk what to do here either.
        throw new InternalExpressionException("If you got here, ask Ghoulboy, he'll fix it.");
    }
}
