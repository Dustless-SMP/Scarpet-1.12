package carpet.script;

import adsen.scarpet.interpreter.parser.APIExpression;
import adsen.scarpet.interpreter.parser.Context;
import adsen.scarpet.interpreter.parser.Expression;
import adsen.scarpet.interpreter.parser.exception.ExpressionException;
import adsen.scarpet.interpreter.parser.exception.ScarpetExpressionException;
import net.minecraft.world.World;

import java.util.function.Consumer;
import java.util.function.Function;


public class CarpetScarpetExpression extends APIExpression {
    public static World world;


    public CarpetScarpetExpression(String expression) {
        super(expression);
        WorldAccess.apply(super.expr);
        Entities.apply(super.expr);
    }

    public void displayOutput(Consumer<String> printerFunction, Function<Context, Context> contextModifier) {
        super.expr.printFunction = printerFunction;
        try {
            printerFunction.accept(" = " + super.expr.eval(contextModifier.apply(Context.simpleParse())).getString());
        } catch (ExpressionException e) {
            throw new ScarpetExpressionException(e.getMessage());
        } catch (ArithmeticException ae) {
            throw new ScarpetExpressionException("math doesn't compute... " + ae.getMessage());
        }
    }
}
