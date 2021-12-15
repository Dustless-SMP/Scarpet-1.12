package carpet.script;

import adsen.scarpet.interpreter.parser.APIExpression;


public class CarpetScarpetExpression extends APIExpression {
    public CarpetScarpetExpression(String expression) {
        super(expression);
        WorldAccess.apply(super.expr);
        Entities.apply(super.expr);
    }
}
