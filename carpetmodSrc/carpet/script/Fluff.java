package carpet.script;

import carpet.script.value.Value;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public abstract class Fluff {

    @FunctionalInterface
    public interface TriFunction<A, B, C, R> { R apply(A a, B b, C c); }

    @FunctionalInterface
    public interface TriConsumer<A, B, C> { void accept(A a, B b, C c); }

    @FunctionalInterface
    public interface QuadConsumer<A, B, C, D> { void accept(A a, B b, C c, D d); }

    @FunctionalInterface
    public interface QuadFunction<A, B, C, D, R> { R apply(A a, B b, C c, D d);}

    @FunctionalInterface
    public interface QuinnFunction<A, B, C, D, E, R> { R apply(A a, B b, C c, D d, E e);}

    @FunctionalInterface
    public interface SexFunction<A, B, C, D, E, F, R> { R apply(A a, B b, C c, D d, E e, F f);}

    public static class Operator {
        private final int precedence;
        private final int arguments;
        private final boolean isLeftAssoc;
        private final String surface;
        private Function<List<Value>, Value> fun;


        Operator(String surface, int precedence, int args, boolean isLeftAssoc, Function<List<Value>, Value> fun){
            this.precedence = precedence;
            this.arguments = args;
            this.surface = surface;
            this.isLeftAssoc = isLeftAssoc;
            this.fun = fun;
        }

        public int getPrecedence(){
            return this.precedence;
        }

        public int getArguments(){
            return this.arguments;
        }

        public String getSurface(){
            return this.surface;
        }

        public boolean isLeftAssoc(){
            return this.isLeftAssoc;
        }

        public Value apply(List<Value> args){
            return fun.apply(args);
        }

        public Value apply(Value ... args){
            return fun.apply(Arrays.asList(args));
        }

    }

    public static class Functions{ //Not to confuse with Function class

        public final String name;
        public final int arguments;
        private final Function<List<Value>, Value> function;
        public final boolean varArgs;
        public final int minArgs;

        public Functions(String name, int arguments, Function<List<Value>, Value> function) {
            this.name = name;
            this.arguments = arguments;
            this.varArgs = arguments==-1; //cos it's easier this way
            this.minArgs = 0; // cos it's defined differently for different functions
            this.function = function;
        }

        public Functions(String name, int arguments, int minArgs,boolean varArgs, Function<List<Value>, Value> function) {
            this.name = name;
            this.arguments = arguments;
            this.varArgs = varArgs; //for user-defined functions which can have variable arguments
            this.minArgs = minArgs; //cos you can have func(a, b, ...c) -> etc., so it needs at leas 2 args
            this.function = function;
        }

        public int getNumParams(){
            return minArgs;
        }

        public boolean numParamsVaries(){
            return varArgs;
        }

        public Value apply(List<Value> lv){
            return function.apply(lv);
        }
    }

}
