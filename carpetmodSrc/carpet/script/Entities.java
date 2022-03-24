package carpet.script;

import adsen.scarpet.interpreter.parser.Expression;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.ContainerValueInterface;
import adsen.scarpet.interpreter.parser.value.ListValue;
import adsen.scarpet.interpreter.parser.value.Value;
import carpet.CarpetServer;
import carpet.script.value.EntityValue;
import carpet.script.value.ValueConversions;
import net.minecraft.command.CommandException;
import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Entities {
    public static void apply(Expression expression) {
        expression.addFunction("entity_selector", lv -> {
            if (lv.size() < 1 || lv.size() > 2)
                throw new InternalExpressionException("'entity_selector' takes either one selector or a selector and target entity");
            String selector = lv.get(0).getString();
            if (!EntitySelector.isSelector(selector))
                throw new InternalExpressionException("Invalid entity selector '" + selector + "'");

            Class<? extends Entity> entityClass = Entity.class;
            if (lv.size() == 2)
                entityClass = ValueConversions.entityFromString(lv.get(1).getString());

            List<? extends Entity> entityList;
            try {
                entityList = EntitySelector.matchEntities(CarpetServer.minecraft_server, selector, entityClass);
            } catch (CommandException e) {
                throw new InternalExpressionException("Unknown error when executing 'entity_selector'");
            }
            List<Value> retList = new ArrayList<>();
            entityList.forEach(e -> retList.add(EntityValue.of(e)));
            return ListValue.wrap(retList);
        });

        expression.addFunction("query", (lv) -> {
            if (lv.size() < 2) {
                throw new InternalExpressionException("'query' takes entity as a first argument, and queried feature as a second");
            }
            Value v = lv.get(0);
            if (!(v instanceof EntityValue))
                throw new InternalExpressionException("First argument to query should be an entity");
            String what = lv.get(1).getString().toLowerCase(Locale.ROOT);
            Value retval;
            if (lv.size() == 2)
                retval = ((EntityValue) v).get(what, null);
            else if (lv.size() == 3)
                retval = ((EntityValue) v).get(what, lv.get(2));
            else
                retval = ((EntityValue) v).get(what, ListValue.wrap(lv.subList(2, lv.size())));
            return retval;
        });

        expression.addFunction("modify", -1, (lv) -> {
            if (lv.size() < 2) {
                throw new InternalExpressionException("'modify' takes entity as a first argument, and queried feature as a second");
            }
            Value v = lv.get(0);
            if (!(v instanceof EntityValue))
                throw new InternalExpressionException("First argument to modify should be an entity");
            String what = lv.get(1).getString();
            if (lv.size() == 2)
                ((EntityValue) v).set(what, null);
            else if (lv.size() == 3)
                ((EntityValue) v).set(what, lv.get(2));
            else
                ((EntityValue) v).set(what, ListValue.wrap(lv.subList(2, lv.size())));
            return v;
        });

        expression.addBinaryOperator("~", 80, false, Value::in);
        expression.addBinaryOperator(":", 80, false, (v1, v2) -> {
            if (v1 instanceof ContainerValueInterface) {
                ContainerValueInterface cvi = (ContainerValueInterface) v1;
                return cvi.get(v2);
            }
            throw new InternalExpressionException("Cannot access element of non-container value");
        });
    }
}
