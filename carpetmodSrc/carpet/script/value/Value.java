package carpet.script.value;


import carpet.script.Tokenizer.Token;
import carpet.script.exception.TempException;
import net.minecraft.nbt.NBTBase;

public abstract class Value implements Comparable<Value>, Cloneable{
    @Override
    public int compareTo(final Value o){
        //if (o instanceof NumericValue || o instanceof ListValue || o instanceof ThreadValue) todo
        //{
        //    return -o.compareTo(this);
        //}
        return getString().compareTo(o.getString());
    }


    //public Value(){} Not necessary, I think... (same for clone, idk what to put)

    public abstract String getString();

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

    public static Value fromToken(Token token){ //todo finish + figure out how scarpet does it

        switch (token.type){
            default:
                return new StringValue(token.surface);
        }
    }

    public static <T> void assertNotNull(T t1, T t2){
        if (t1 == null)
            throw new TempException("First operand may not be null");
        if (t2 == null)
            throw new TempException("Second operand may not be null");
    }
}
