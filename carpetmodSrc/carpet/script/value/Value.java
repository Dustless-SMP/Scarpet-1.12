package carpet.script.value;


public abstract class Value implements Comparable<Value>, Cloneable{
    @Override
    public int compareTo(Value o) {
        return 0;
    }

    public abstract String getString();
}
