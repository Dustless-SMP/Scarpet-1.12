package adsen.scarpet.interpreter.parser.language;

import adsen.scarpet.interpreter.parser.Context;
import adsen.scarpet.interpreter.parser.Expression;
import adsen.scarpet.interpreter.parser.LazyValue;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.BooleanValue;
import adsen.scarpet.interpreter.parser.value.ContainerValueInterface;
import adsen.scarpet.interpreter.parser.value.LazyListValue;
import adsen.scarpet.interpreter.parser.value.ListValue;
import adsen.scarpet.interpreter.parser.value.MapValue;
import adsen.scarpet.interpreter.parser.value.NumericValue;
import adsen.scarpet.interpreter.parser.value.StringValue;
import adsen.scarpet.interpreter.parser.value.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class LoopsAndHigherOrderFunctions {
    public static void apply(Expression expression) {
        expression.addFunction("l", lv -> {
            if (lv.size() == 1 && lv.get(0) instanceof LazyListValue)
                return ListValue.wrap(((LazyListValue) lv.get(0)).unroll());
            return new ListValue.ListConstructorValue(lv);
        });

        expression.addFunction("m", lv -> {
            Value ret;
            if (lv.size() == 1 && lv.get(0) instanceof LazyListValue) {
                ret = new MapValue(((LazyListValue) lv.get(0)).unroll());
            } else {
                ret = new MapValue(lv);
            }
            return ret;
        });


        expression.addFunction("join", (lv) ->
        {
            if (lv.size() < 2)
                throw new InternalExpressionException("join takes at least 2 arguments");
            String delimiter = lv.get(0).getString();
            List<Value> toJoin;
            if (lv.size() == 2 && lv.get(1) instanceof LazyListValue) {
                toJoin = ((LazyListValue) lv.get(1)).unroll();

            } else if (lv.size() == 2 && lv.get(1) instanceof ListValue) {
                toJoin = new ArrayList<>(((ListValue) lv.get(1)).getItems());
            } else {
                toJoin = lv.subList(1, lv.size());
            }
            return new StringValue(toJoin.stream().map(Value::getString).collect(Collectors.joining(delimiter)));
        });

        expression.addBinaryFunction("split", (d, v) -> {
            String delimiter = d.getString();
            String hwat = v.getString();
            return ListValue.wrap(Arrays.stream(hwat.split(delimiter)).map(StringValue::new).collect(Collectors.toList()));
        });

        expression.addFunction("slice", (lv) -> {

            if (lv.size() != 2 && lv.size() != 3)
                throw new InternalExpressionException("slice takes 2 or 3 arguments");
            Value hwat = lv.get(0);
            long from = NumericValue.asNumber(lv.get(1)).getLong();
            long to = -1;
            if (lv.size() == 3)
                to = NumericValue.asNumber(lv.get(2)).getLong();
            return hwat.slice(from, to);
        });

        expression.addFunction("sort", (lv) ->
        {
            List<Value> toSort = lv;
            if (lv.size() == 1 && lv.get(0) instanceof ListValue) {
                toSort = new ArrayList<>(((ListValue) lv.get(0)).getItems());
            }
            Collections.sort(toSort);
            return ListValue.wrap(toSort);
        });

        expression.addLazyFunction("sort_key", 2, (c, t, lv) ->  //get working with iterators
        {
            Value v = lv.get(0).evalValue(c);
            if (!(v instanceof ListValue))
                throw new InternalExpressionException("First argument for sort_key should be a List");
            LazyValue sortKey = lv.get(1);
            //scoping
            LazyValue __ = c.getVariable("_");

            List<Value> toSort = new ArrayList<>(((ListValue) v).getItems());

            toSort.sort((v1, v2) -> {
                c.setVariable("_", (cc, tt) -> v1);
                Value ev1 = sortKey.evalValue(c);
                c.setVariable("_", (cc, tt) -> v2);
                Value ev2 = sortKey.evalValue(c);
                return ev1.compareTo(ev2);
            });
            //revering scope
            c.setVariable("_", __);
            return (cc, tt) -> ListValue.wrap(toSort);
        });

        expression.addFunction("range", (lv) ->
        {
            long from = 0;
            long to;
            long step = 1;
            int argsize = lv.size();
            if (argsize == 0 || argsize > 3)
                throw new InternalExpressionException("range accepts from 1 to 3 arguments, not " + argsize);
            to = NumericValue.asNumber(lv.get(0)).getLong();
            if (lv.size() > 1) {
                from = to;
                to = NumericValue.asNumber(lv.get(1)).getLong();
                if (lv.size() > 2) {
                    step = NumericValue.asNumber(lv.get(2)).getLong();
                }
            }
            return LazyListValue.range(from, to, step);
        });

        expression.addLazyFunction("get", 2, (c, t, lv) ->
        {
            Value container = lv.get(0).evalValue(c, Context.CONTAINER);
            if (!(container instanceof ContainerValueInterface))
                throw new InternalExpressionException("First argument to 'get' function must be a container");
            Value ret = ((ContainerValueInterface) container).get(lv.get(1).evalValue(c));
            return (cc, tt) -> ret;
        });

        expression.addLazyFunction("has", 2, (c, t, lv) ->
        {
            Value container = lv.get(0).evalValue(c, Context.CONTAINER);
            if (!(container instanceof ContainerValueInterface))
                throw new InternalExpressionException("First argument to 'has' function must be a container");
            Value ret = BooleanValue.of(((ContainerValueInterface) container).has(lv.get(1).evalValue(c)));
            return (cc, tt) -> ret;
        });

        expression.addLazyFunction("put", 3, (c, t, lv) ->
        {
            Value container = lv.get(0).evalValue(c, Context.CONTAINER);

            if (!(container instanceof ContainerValueInterface)) {
                return LazyValue.NULL;
            }
            Value where = lv.get(1).evalValue(c);
            Value what = lv.get(2).evalValue(c);
            Value retVal = BooleanValue.of((lv.size() > 3)
                    ? ((ContainerValueInterface) container).put(where, what, lv.get(3).evalValue(c))
                    : ((ContainerValueInterface) container).put(where, what));
            return (cc, tt) -> retVal;
        });

        //condition and expression will get a bound 'i'
        //returns last successful expression or false
        // while(cond, limit, expr) => ??
        expression.addLazyFunction("while", 3, (c, t, lv) ->
        {
            long limit = NumericValue.asNumber(lv.get(1).evalValue(c)).getLong();
            LazyValue condition = lv.get(0);
            LazyValue expr = lv.get(2);
            long i = 0;
            Value lastOne = Value.NULL;
            //scoping
            LazyValue _val = c.getVariable("_");
            c.setVariable("_", (cc, tt) -> new NumericValue(0).bindTo("_"));
            while (i < limit && condition.evalValue(c, Context.BOOLEAN).getBoolean()) {
                lastOne = expr.evalValue(c);
                i++;
                long seriously = i;
                c.setVariable("_", (cc, tt) -> new NumericValue(seriously).bindTo("_"));
            }
            //revering scope
            c.setVariable("_", _val);
            Value lastValueNoKidding = lastOne;
            return (cc, tt) -> lastValueNoKidding;
        });

        // loop(Num, expr, exit_condition) => last_value
        // loop(list, expr)
        // expr receives bounded variable '_' indicating iteration
        expression.addLazyFunction("loop", -1, (c, t, lv) ->
        {
            if (lv.size() < 2 || lv.size() > 3) {
                throw new InternalExpressionException("Incorrect number of attributes for loop, should be 2 or 3, not " + lv.size());
            }
            long limit = NumericValue.asNumber(lv.get(0).evalValue(c)).getLong();
            Value lastOne = Value.NULL;
            LazyValue expr = lv.get(1);
            LazyValue cond = null;
            if (lv.size() > 2) cond = lv.get(2);
            //scoping
            LazyValue _val = c.getVariable("_");
            for (long i = 0; i < limit; i++) {
                long whyYouAsk = i;
                c.setVariable("_", (cc, tt) -> new NumericValue(whyYouAsk).bindTo("_"));
                lastOne = expr.evalValue(c);
                if (cond != null && cond.evalValue(c).getBoolean())
                    break;
            }
            //revering scope
            c.setVariable("_", _val);
            Value trulyLastOne = lastOne;
            return (cc, tt) -> trulyLastOne;
        });

        // map(list or Num, expr) => list_results
        // receives bounded variable '_' with the expression
        expression.addLazyFunction("map", -1, (c, t, lv) ->
        {
            if (lv.size() < 2 || lv.size() > 3) {
                throw new InternalExpressionException("Incorrect number of attributes for map, should be 2 or 3, not " + lv.size());
            }

            Value rval = lv.get(0).evalValue(c);

            if (!(rval instanceof ListValue))
                throw new InternalExpressionException("First argument of map function should be a list or iterator");
            Iterator<Value> iterator = ((ListValue) rval).iterator();
            LazyValue expr = lv.get(1);
            LazyValue cond = null;
            if (lv.size() > 2) cond = lv.get(2);
            //scoping
            LazyValue _val = c.getVariable("_");
            LazyValue _iter = c.getVariable("_i");
            List<Value> result = new ArrayList<>();
            for (int i = 0; iterator.hasNext(); i++) {
                Value next = iterator.next();
                String var = next.boundVariable;
                next.bindTo("_");
                int doYouReally = i;
                c.setVariable("_", (cc, tt) -> next);
                c.setVariable("_i", (cc, tt) -> new NumericValue(doYouReally).bindTo("_i"));
                result.add(expr.evalValue(c));
                if (cond != null && cond.evalValue(c).getBoolean()) {
                    next.boundVariable = var;
                    break;
                }
                next.boundVariable = var;
            }
            ((ListValue) rval).fatality();
            LazyValue ret = (cc, tt) -> ListValue.wrap(result);
            //revering scope
            c.setVariable("_", _val);
            c.setVariable("_i", _iter);
            return ret;
        });

        // grep(list or num, expr, exit_expr) => list
        // receives bounded variable '_' with the expression, and "_i" with index
        // produces list of values for which the expression is true
        expression.addLazyFunction("filter", -1, (c, t, lv) ->
        {
            if (lv.size() < 2 || lv.size() > 3) {
                throw new InternalExpressionException("Incorrect number of attributes for filter, should be 2 or 3, not " + lv.size());
            }

            Value rval = lv.get(0).evalValue(c);
            if (!(rval instanceof ListValue))
                throw new InternalExpressionException("First argument of filter function should be a list or iterator");
            Iterator<Value> iterator = ((ListValue) rval).iterator();
            LazyValue expr = lv.get(1);
            LazyValue cond = null;
            if (lv.size() > 2) cond = lv.get(2);
            //scoping
            LazyValue _val = c.getVariable("_");
            LazyValue _iter = c.getVariable("_i");
            List<Value> result = new ArrayList<>();
            for (int i = 0; iterator.hasNext(); i++) {
                Value next = iterator.next();
                String var = next.boundVariable;
                next.bindTo("_");
                int seriously = i;
                c.setVariable("_", (cc, tt) -> next);
                c.setVariable("_i", (cc, tt) -> new NumericValue(seriously).bindTo("_i"));
                if (expr.evalValue(c).getBoolean())
                    result.add(next);
                if (cond != null && cond.evalValue(c).getBoolean()) {
                    next.boundVariable = var;
                    break;
                }
                next.boundVariable = var;
            }
            ((ListValue) rval).fatality();
            LazyValue ret = (cc, tt) -> ListValue.wrap(result); // might be a trap - lazy evaluation
            //revering scope
            c.setVariable("_", _val);
            c.setVariable("_i", _iter);
            return ret;
        });

        // first(list, expr) => elem or null
        // receives bounded variable '_' with the expression, and "_i" with index
        // returns first element on the list for which the expr is true
        expression.addLazyFunction("first", 2, (c, t, lv) ->
        {

            Value rval = lv.get(0).evalValue(c);
            if (!(rval instanceof ListValue))
                throw new InternalExpressionException("First argument of 'first' function should be a list or iterator");
            Iterator<Value> iterator = ((ListValue) rval).iterator();
            LazyValue expr = lv.get(1);
            //scoping
            LazyValue _val = c.getVariable("_");
            LazyValue _iter = c.getVariable("_i");
            Value result = Value.NULL;
            for (int i = 0; iterator.hasNext(); i++) {
                Value next = iterator.next();
                String var = next.boundVariable;
                next.bindTo("_");
                int seriously = i;
                c.setVariable("_", (cc, tt) -> next);
                c.setVariable("_i", (cc, tt) -> new NumericValue(seriously).bindTo("_i"));
                if (expr.evalValue(c).getBoolean()) {
                    result = next;
                    next.boundVariable = var;
                    break;
                }
                next.boundVariable = var;
            }
            //revering scope
            ((ListValue) rval).fatality();
            Value whyWontYouTrustMeJava = result;
            c.setVariable("_", _val);
            c.setVariable("_i", _iter);
            return (cc, tt) -> whyWontYouTrustMeJava;
        });

        // all(list, expr) => boolean
        // receives bounded variable '_' with the expression, and "_i" with index
        // returns true if expr is true for all items
        expression.addLazyFunction("all", 2, (c, t, lv) ->
        {
            Value rval = lv.get(0).evalValue(c);
            if (!(rval instanceof ListValue))
                throw new InternalExpressionException("First argument of 'all' function should be a list or iterator");
            Iterator<Value> iterator = ((ListValue) rval).iterator();
            LazyValue expr = lv.get(1);
            //scoping
            LazyValue _val = c.getVariable("_");
            LazyValue _iter = c.getVariable("_i");
            LazyValue result = LazyValue.TRUE;
            for (int i = 0; iterator.hasNext(); i++) {
                Value next = iterator.next();
                String var = next.boundVariable;
                next.bindTo("_");
                int seriously = i;
                c.setVariable("_", (cc, tt) -> next);
                c.setVariable("_i", (cc, tt) -> new NumericValue(seriously).bindTo("_i"));
                if (!expr.evalValue(c).getBoolean()) {
                    result = LazyValue.FALSE;
                    next.boundVariable = var;
                    break;
                }
                next.boundVariable = var;
            }
            //revering scope
            ((ListValue) rval).fatality();
            c.setVariable("_", _val);
            c.setVariable("_i", _iter);
            return result;
        });

        // similar to map, but returns total number of successes
        // for(list, expr, exit_expr) => success_count
        // can be substituted for first and all, but first is more efficient and all doesn't require knowing list size
        expression.addLazyFunction("for", -1, (c, t, lv) ->
        {
            if (lv.size() < 2 || lv.size() > 3) {
                throw new InternalExpressionException("Incorrect number of attributes for 'for', should be 2 or 3, not " + lv.size());
            }
            Value rval = lv.get(0).evalValue(c);
            if (!(rval instanceof ListValue))
                throw new InternalExpressionException("Second argument of 'for' function should be a list or iterator");
            Iterator<Value> iterator = ((ListValue) rval).iterator();
            LazyValue expr = lv.get(1);
            LazyValue cond = null;
            if (lv.size() > 2) cond = lv.get(2);

            //scoping
            LazyValue _val = c.getVariable("_");
            LazyValue _iter = c.getVariable("_i");
            int successCount = 0;
            for (int i = 0; iterator.hasNext(); i++) {
                Value next = iterator.next();
                String var = next.boundVariable;
                next.bindTo("_");
                int seriously = i;
                c.setVariable("_", (cc, tt) -> next);
                c.setVariable("_i", (cc, tt) -> new NumericValue(seriously).bindTo("_i"));
                if (expr.evalValue(c).getBoolean())
                    successCount++;
                if (cond != null && cond.evalValue(c).getBoolean()) {
                    next.boundVariable = var;
                    break;
                }
                next.boundVariable = var;
            }
            //revering scope
            ((ListValue) rval).fatality();
            c.setVariable("_", _val);
            c.setVariable("_i", _iter);
            long promiseWontChange = successCount;
            return (cc, tt) -> new NumericValue(promiseWontChange);
        });


        // reduce(list, expr, ?acc) => value
        // reduces values in the list with expression that gets accumulator
        // each iteration expr receives acc - accumulator, and '_' - current list value
        // returned value is substituted to the accumulator
        expression.addLazyFunction("reduce", 3, (c, t, lv) ->
        {
            LazyValue expr = lv.get(1);

            Value acc = lv.get(2).evalValue(c);
            Value rval = lv.get(0).evalValue(c);
            if (!(rval instanceof ListValue))
                throw new InternalExpressionException("First argument of 'reduce' should be a list or iterator");
            Iterator<Value> iterator = ((ListValue) rval).iterator();

            if (!iterator.hasNext()) {
                Value seriouslyWontChange = acc;
                return (cc, tt) -> seriouslyWontChange;
            }

            //scoping
            LazyValue _val = c.getVariable("_");
            LazyValue _acc = c.getVariable("_a");

            while (iterator.hasNext()) {
                Value next = iterator.next();
                String var = next.boundVariable;
                next.bindTo("_");
                Value promiseWontChangeYou = acc;
                c.setVariable("_a", (cc, tt) -> promiseWontChangeYou.bindTo("_a"));
                c.setVariable("_", (cc, tt) -> next);
                acc = expr.evalValue(c);
                next.boundVariable = var;
            }
            //reverting scope
            ((ListValue) rval).fatality();
            c.setVariable("_a", _acc);
            c.setVariable("_", _val);

            Value hopeItsEnoughPromise = acc;
            return (cc, tt) -> hopeItsEnoughPromise;
        });
    }
}
