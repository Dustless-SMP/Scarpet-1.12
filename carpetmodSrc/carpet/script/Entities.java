package carpet.script;

import adsen.scarpet.interpreter.parser.Expression;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.ListValue;
import adsen.scarpet.interpreter.parser.value.Value;
import carpet.script.value.EntityValue;

import java.util.Locale;

public class Entities {
    public static void apply(Expression expression) {
        expression.addFunction("query", (lv) -> {
            if (lv.size()<2)
            {
                throw new InternalExpressionException("'query' takes entity as a first argument, and queried feature as a second");
            }
            Value v = lv.get(0);
            if (!(v instanceof EntityValue))
                throw new InternalExpressionException("First argument to query should be an entity");
            String what = lv.get(1).getString().toLowerCase(Locale.ROOT);
            Value retval;
            if (lv.size()==2) {
                try{
                    retval = ((EntityValue) v).get(what, null);
                } catch (NullPointerException npe){
                    throw new InternalExpressionException("''");//todo query function NPE error message
                }
            }
            else if (lv.size()==3)
                retval = ((EntityValue) v).get(what, lv.get(2));
            else
                retval = ((EntityValue) v).get(what, ListValue.wrap(lv.subList(2, lv.size())));
            return retval;
        });
    }
}
