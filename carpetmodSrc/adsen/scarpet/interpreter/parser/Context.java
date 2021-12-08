package adsen.scarpet.interpreter.parser;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Context {
    public static final int NONE = 0;
    public static final int VOID = 1;
    public static final int BOOLEAN = 2;
    public static final int NUMBER = 3;
    public static final int STRING = 4;
    public static final int CONTAINER = 5;
    public static final int ITERATOR = 6;
    public static final int SIGNATURE = 7;
    public static final int LOCALIZATION = 8;
    public ScriptHost host;
    private Map<String, LazyValue> variables = new HashMap<>();

    Context(ScriptHost host) {
        this.host = host;
    }

    public static Context simpleParse() {
        return new Context(new ScriptHost("command line"));
    }

    public LazyValue getVariable(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        return host.globalVariables.get(name);
    }

    public void setVariable(String name, LazyValue lv) {
        if (name.startsWith("global_")) {
            host.globalVariables.put(name, lv);
            return;
        }
        variables.put(name, lv);
    }


    public boolean isAVariable(String name) {
        return variables.containsKey(name) || host.globalVariables.containsKey(name);
    }


    public void delVariable(String variable) {
        if (variable.startsWith("global_")) {
            host.globalVariables.remove(variable);
            return;
        }
        variables.remove(variable);
    }

    public void clearAll(String variable) {
        if (variable.startsWith("global_")) {
            host.globalVariables.remove(variable);
            return;
        }
        variables.remove(variable);
    }

    public Context with(String variable, LazyValue lv) {
        variables.put(variable, lv);
        return this;
    }

    public Set<String> getAllVariableNames() {
        return variables.keySet();
    }

    public Context recreate() {
        return new Context(this.host);
    }
}
