package carpet.script;

import adsen.scarpet.interpreter.parser.APIExpression;
import adsen.scarpet.interpreter.parser.Expression;


public class CarpetScarpetExpression extends APIExpression {
    public CarpetScarpetExpression(String expression) {
        super(expression);
        WorldAccess.apply(super.expr);
        Entities.apply(super.expr);
    }
}
