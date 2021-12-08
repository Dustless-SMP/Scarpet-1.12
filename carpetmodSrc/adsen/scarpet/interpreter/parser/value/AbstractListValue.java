package adsen.scarpet.interpreter.parser.value;

import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractListValue extends Value implements Iterable<Value> {

    @Override public abstract Iterator<Value> iterator();
    public abstract List<Value> unpack();
    public void fatality() { }
    public void append(Value v)
    {
        throw new InternalExpressionException("Cannot append a value to an abstract list");
    }
}
