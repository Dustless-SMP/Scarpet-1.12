package carpet.script;

import carpet.script.value.Value;

import java.util.HashMap;
import java.util.Map;

public class CarpetScriptHost { // main scriptHost which is the command line and each app. Each function has its own scripthost for local vars
    public static Map<String, Value> globalVariables = new HashMap<>();
    public Map<String, Value> variables;

    public CarpetScriptHost(Map<String,Value> variables){
        for (Map.Entry<String, Value> variable: variables.entrySet()) {
            if(variable.getKey().startsWith("global_"))
                globalVariables.put(variable.getKey(), variable.getValue());
            else
                variables.put(variable.getKey(), variable.getValue());
        }
    }
}
