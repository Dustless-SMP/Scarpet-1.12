package adsen.scarpet.interpreter.parser;

import adsen.scarpet.interpreter.parser.exception.ScarpetExpressionException;
import adsen.scarpet.interpreter.parser.exception.ExpressionException;

/**
 * The class which you use to add your own functions, events, variables etc. to scarpet.
 */
public class APIExpression {
    private final Expression expr;

    /**
     * @param expression expression
     */
    public APIExpression(String expression) {
        this.expr = new Expression(expression);
    }

    Expression getExpr() {
        return expr;
    }
}
