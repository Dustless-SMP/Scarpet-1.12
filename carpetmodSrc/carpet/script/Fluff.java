package carpet.script;

import carpet.script.value.Value;
import org.apache.commons.lang3.builder.CompareToBuilder;

import java.io.Serializable;
import java.util.List;
import java.util.function.BiFunction;
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

    private static class Quintuple<A, B, C, D, E> implements Comparable<Quintuple<A, B, C, D, E>>, Serializable{ // no need to make public for now
        public A a;
        public B b;
        public C c;
        public D d;
        public E e;

        public Quintuple(A a, B b, C c, D d, E e){
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
        }

        //these methods are kinda useless, but imma keep them in case I ever need a Quintuple outside of this class (doubtful)
        private A getA() {
            return a;
        }
        private B getB() {
            return b;
        }
        private C getC() {
            return c;
        }
        private D getD() {
            return d;
        }
        private E getE() {
            return e;
        }


        @Override
        public int compareTo(Quintuple<A, B, C, D, E> other) {
            return (new CompareToBuilder()).append(this.getA(), other.getA()).append(this.getB(), other.getB()).append(this.getC(), other.getC()).append(this.getD(), other.getD()).append(this.getE(), other.getE()).toComparison();
        }
    }

    public static class Operator{
        private final int precedence;
        private final int arguments;
        private final boolean isLeftAssoc;
        private final String surface;
        public Function<List<Value>, Value> fun;


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
    }

    public static class Functions{ //Not to confuse with Function class

        public final String name;
        public final int arguments;
        private final Function<List<Value>, Value> function;
        public final boolean varArgs;

        public Functions(String name, int arguments, Function<List<Value>, Value> function) {
            this.name = name;
            this.arguments = arguments;
            this.varArgs = arguments==-1; //only for built-in functions for now
            this.function = function;
        }
    }

}
