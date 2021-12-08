package adsen.scarpet.interpreter.parser;

import adsen.scarpet.interpreter.ScarpetInterpreterJava;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScarpetEventServer
{
    public static class Callback
    {
        public String host;
        public String udf;

        public Callback(String host, String udf)
        {
            this.host = host;
            this.udf = udf;
        }

        @Override
        public String toString()
        {
            return udf+((host==null)?"":"(from "+host+")");
        }
    }

    public static class ScheduledCall extends Callback
    {
        public List<LazyValue> args;
        public long dueTime;

        public ScheduledCall(Context context, String udf, List<LazyValue> args, long dueTime)
        {
            super(context.host.getName(), udf);
            this.args = args;
            this.dueTime = dueTime;
        }

        public void execute()
        {
            ScarpetInterpreterJava.scriptServer.runas(host, udf, args);
        }
    }

    public static class CallbackList
    {

        public List<Callback> callList;
        public int reqArgs;

        public CallbackList(int reqArgs)
        {
            this.callList = new ArrayList<>();
            this.reqArgs = reqArgs;
        }

        public void call(Supplier<List<LazyValue>> argumentSupplier)
        {
            if (callList.size() > 0)
            {
                List<LazyValue> argv = argumentSupplier.get(); // empty for onTickDone
                assert argv.size() == reqArgs;
                callList.removeIf(call -> !ScarpetInterpreterJava.scriptServer.runas(call.host, call.udf, argv)); // this actually does the calls
            }
        }
        public boolean addEventCall(String hostName, String funName)
        {
            ScriptHost host = ScarpetInterpreterJava.scriptServer.getHostByName(hostName);
            if (host == null)
            {
                // impossible call to add
                return false;
            }
            UserDefinedFunction udf = host.globalFunctions.get(funName);
            if (udf == null || udf.getArguments().size() != reqArgs)
            {
                // call won't match arguments
                return false;
            }
            //all clear
            //remove duplicates
            removeEventCall(hostName, funName);
            callList.add(new Callback(hostName, funName));
            return true;
        }
        public void removeEventCall(String hostName, String callName)
        {
            callList.removeIf((c)->  c.udf.equalsIgnoreCase(callName) && ( hostName == null || c.host.equalsIgnoreCase(hostName) ) );
        }
    }

    public Map<String, CallbackList> eventHandlers = new HashMap<>();

    public List<ScheduledCall> scheduledCalls = new LinkedList<>();

    public void tick()
    {
        Iterator<ScheduledCall> eventIterator = scheduledCalls.iterator();
        List<ScheduledCall> currentCalls = new ArrayList<>();
        while(eventIterator.hasNext())
        {
            ScheduledCall call = eventIterator.next();
            call.dueTime--;
            if (call.dueTime <= 0)
            {
                currentCalls.add(call);
                eventIterator.remove();
            }
        }
        for (ScheduledCall call: currentCalls)
        {
            call.execute();
        }

    }
    public void scheduleCall(Context context, String function, List<LazyValue> args, long due)
    {
        scheduledCalls.add(new ScheduledCall(context, function, args, due));
    }


    public ScarpetEventServer()
    {
        //todo events
    }

    public boolean addEvent(String event, String host, String funName)
    {
        if (!eventHandlers.containsKey(event))
        {
            return false;
        }
        return eventHandlers.get(event).addEventCall(host, funName);
    }

    public boolean removeEvent(String event, String funName)
    {

        if (!eventHandlers.containsKey(event))
            return false;
        Callback callback= decodeCallback(funName);
        eventHandlers.get(event).removeEventCall(callback.host, callback.udf);
        return true;
    }

    private Callback decodeCallback(String funName)
    {
        Pattern find = Pattern.compile("(\\w+)\\(from (\\w+)\\)");
        Matcher matcher = find.matcher(funName);
        if(matcher.matches())
        {
            return new Callback(matcher.group(2), matcher.group(1));
        }
        return new Callback(null, funName);
    }
}
