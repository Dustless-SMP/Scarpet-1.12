package adsen.scarpet.interpreter.parser;

import adsen.scarpet.interpreter.parser.exception.ExpressionException;
import adsen.scarpet.interpreter.parser.exception.InvalidCallbackException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class ScarpetScriptServer {
    //make static for now, but will change that later:
    public ScriptHost globalHost;
    public Map<String, ScriptHost> modules;
    public ScarpetEventServer events;
    long tickStart;

    public ScarpetScriptServer() {
        globalHost = new ScriptHost(null);
        events = new ScarpetEventServer();
        modules = new HashMap<>();
        tickStart = 0L;
        resetErrorSnooper();
    }

    public ScriptHost getHostByName(String name) {
        if (name == null)
            return globalHost;
        return modules.get(name);
    }


    public void addEvents(String hostName) {
        ScriptHost host = modules.get(hostName);
        if (host == null) {
            return;
        }
        for (String fun : host.globalFunctions.keySet()) {
            if (!fun.startsWith("__on_"))
                continue;
            String event = fun.replaceFirst("__on_", "");
            if (!events.eventHandlers.containsKey(event))
                continue;
            events.addEvent(event, hostName, fun);
        }
    }


    public void setChatErrorSnooper() {
        ExpressionException.errorSnooper = (expr, token, message) ->
        {
            String[] lines = expr.getCodeString().split("\n");

            String shebang = message;

            if (lines.length > 1) {
                shebang += " at line " + (token.lineNo + 1) + ", pos " + (token.linePos + 1);
            } else {
                shebang += " at pos " + (token.pos + 1);
            }
            if (expr.getName() != null) {
                shebang += " in " + expr.getName() + "";
            }
            Expression.print("r " + shebang);

            if (lines.length > 1 && token.lineNo > 0) {
                Expression.print("l " + lines[token.lineNo - 1]);
            }
            Expression.print("l " + lines[token.lineNo].substring(0, token.linePos) + "r  HERE>> " + "l " +
                    lines[token.lineNo].substring(token.linePos));

            if (lines.length > 1 && token.lineNo < lines.length - 1) {
                Expression.print("l " + lines[token.lineNo + 1]);
            }
            return new ArrayList<>();
        };
    }

    public void resetErrorSnooper() {
        ExpressionException.errorSnooper = null;
    }

    public boolean removeScriptHost(String name) {
        name = name.toLowerCase(Locale.ROOT);
        if (!modules.containsKey(name)) {
            Expression.print("r No such host found: " + "wb  " + name);
            return false;
        }
        // stop all events associated with name
        modules.remove(name);
        Expression.print("w Removed host " + name);
        return true;
    }

    public boolean runas(String hostname, String udf_name, List<LazyValue> argv) {
        ScriptHost host = globalHost;
        if (hostname != null)
            host = modules.get(hostname);
        try {
            host.callUDF(host.globalFunctions.get(udf_name), argv);
        } catch (NullPointerException | InvalidCallbackException npe) {
            return false;
        }
        return true;
    }
}
