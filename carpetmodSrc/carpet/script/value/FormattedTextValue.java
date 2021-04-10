package carpet.script.value;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentBase;
import net.minecraft.util.text.TextComponentString;

public class FormattedTextValue extends StringValue
{
    ITextComponent text;
    public FormattedTextValue(ITextComponent text) {
        super(null);
        this.text = text;
    }

    public static Value combine(Value left, Value right) {
        TextComponentBase text;
        if (left instanceof FormattedTextValue) {
            text = (TextComponentBase) ((FormattedTextValue) left).getText().createCopy();
        }
        else {
            if (left instanceof NullValue)
                return right;
            text = new TextComponentString("");
        }
        
        if (right instanceof FormattedTextValue)
        {
            text.appendSibling(((FormattedTextValue) right).getText().createCopy());
            return new FormattedTextValue(text);
        }
        else
        {
            if (right instanceof NullValue)
                return left;
            text.appendText(right.getString());
            return new FormattedTextValue(text);
        }
    }

    @Override
    public String getString() {
        return text.toString();
    }

    @Override
    public boolean getBoolean() {
          return text.getSiblings().size() > 0;
    }

    @Override
    public Value clone()
    {
        return new FormattedTextValue(text);
    }

    @Override
    public String getTypeString()
    {
        return "text";
    }

    public ITextComponent getText()
    {
        return text;
    }

    @Override
    public NBTBase toTag(boolean force)
    {
        if (!force) throw new NBTSerializableValue.IncompatibleTypeException(this);
        return NBTTagString.of(ITextComponent.Serializer.componentToJson(text));
    }

    @Override
    public Value add(Value o) {
        return combine(this, o);
    }

    public String serialize()
    {
        return ITextComponent.Serializer.componentToJson(text);
    }

    public static FormattedTextValue deserialize(String serialized)
    {
        return new FormattedTextValue(ITextComponent.Serializer.fromJsonLenient(serialized));
    }
}
