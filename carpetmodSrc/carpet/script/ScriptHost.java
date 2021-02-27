package carpet.script;


import carpet.script.value.Value;

import java.util.Map;

public class ScriptHost extends CarpetScriptHost {

    public ScriptHost(Map<String, Value> variables) {
        super(variables);
    }
}
