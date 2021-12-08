package adsen.scarpet.interpreter.parser.exception;

import adsen.scarpet.interpreter.parser.value.Value;

public class ReturnStatement extends ExitStatement {

    public ReturnStatement(Value value) {
        super(value);
    }
}