package adsen.scarpet.interpreter.parser.exception;

import adsen.scarpet.interpreter.parser.value.Value;

public class ThrowStatement extends ExitStatement {
    public ThrowStatement(Value value) {
        super(value);
    }
}
