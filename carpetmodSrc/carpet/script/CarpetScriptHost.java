package carpet.script;

import carpet.CarpetServer;
import carpet.CarpetSettings;
import carpet.script.api.Auxiliary;
import carpet.script.bundled.Module;
import carpet.script.exception.CarpetExpressionException;
import carpet.script.exception.ExpressionException;
import carpet.script.exception.InternalExpressionException;
import carpet.script.exception.InvalidCallbackException;
import carpet.script.value.FunctionValue;
import carpet.script.value.MapValue;
import carpet.script.value.NumericValue;
import carpet.script.value.StringValue;
import carpet.script.value.Value;
import carpet.utils.Messenger;
import com.google.gson.JsonElement;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Math.max;

public class CarpetScriptHost extends ScriptHost
{
    private final CarpetScriptServer scriptServer;
    ICommandSender responsibleSource;

    private NBTBase globalState;
    private int saveTimeout;
    public boolean persistenceRequired;

    public Map<Value, Value> appConfig;

    Function<ICommandSender, Boolean> commandValidator;
    boolean isRuleApp;

    private CarpetScriptHost(CarpetScriptServer server, Module code, boolean perUser, ScriptHost parent, Map<Value, Value> config, Function<ICommandSender, Boolean> commandValidator, boolean isRuleApp)
    {
        super(code, perUser, parent);
        this.saveTimeout = 0;
        this.scriptServer = server;
        persistenceRequired = true;
        if (parent == null && code != null) // app, not a global host
        {
            persistenceRequired = false;
            globalState = loadState();
        }
        else if (parent != null)
        {
            persistenceRequired = ((CarpetScriptHost)parent).persistenceRequired;
        }
        appConfig = config;
        this.commandValidator = commandValidator;
        this.isRuleApp = isRuleApp;
    }

    public static CarpetScriptHost create(CarpetScriptServer scriptServer, Module module, boolean perPlayer, ICommandSender source, Function<ICommandSender, Boolean> commandValidator, boolean isRuleApp)
    {
        CarpetScriptHost host = new CarpetScriptHost(scriptServer, module, perPlayer, null, new HashMap<>(), commandValidator, isRuleApp);
        // parse code and convert to expression
        if (module != null)
        {
            try
            {
                String code = module.getCode();
                if (code == null)
                {
                    Messenger.m(source, "r Unable to load "+module.getName()+" app - code not found");
                    return null;
                }
                host.setChatErrorSnooper(source);
                CarpetExpression ex = new CarpetExpression(host.main, code, source, new BlockPos(0, 0, 0));
                ex.getExpr().asATextSource();
                ex.scriptRunCommand(host, new BlockPos(source.getPosition()));
            }
            catch (CarpetExpressionException e)
            {
                host.handleErrorWithStack("Error while evaluating expression", e);
                host.resetErrorSnooper();
                return null;
            }
            catch (ArithmeticException ae)
            {
                host.handleErrorWithStack("Math doesn't compute", ae);
                return null;
            }
        }
        return host;
    }

    @Override
    protected ScriptHost duplicate()
    {
        return new CarpetScriptHost(scriptServer, main, false, this, appConfig, commandValidator, isRuleApp);
    }

    @Override
    protected void setupUserHost(ScriptHost host)
    {
        super.setupUserHost(host);
        // transfer Events
        CarpetScriptHost child = (CarpetScriptHost) host;
        CarpetEventServer.Event.transferAllHostEventsToChild(child);
        FunctionValue onStart = child.getFunction("__on_start");
        if (onStart != null) child.callNow(onStart, Collections.emptyList());
    }

    @Override
    public void addUserDefinedFunction(Context ctx, Module module, String funName, FunctionValue function)
    {
        super.addUserDefinedFunction(ctx, module, funName, function);
        if (ctx.host.main != module) return; // not dealing with automatic imports / exports /configs / apps from imports
        if (funName.startsWith("__")) // potential fishy activity
        {
            if (funName.startsWith("__on_")) // here we can make a determination if we want to only accept events from main module.
            {
                // this is nasty, we have the host and function, yet we add it via names, but hey - works for now
                String event = funName.replaceFirst("__on_", "");
                if (CarpetEventServer.Event.byName.containsKey(event))
                    scriptServer.events.addBuiltInEvent(event, this, function, null);
            }
            else if (funName.equals("__config"))
            {
                // needs to be added as we read the code, cause other events may be affected.
                if (!readConfig())
                    throw new InternalExpressionException("Invalid app config (via '__config()' function)");
            }
        }
    }

    private boolean readConfig()
    {
        try
        {
            FunctionValue configFunction = getFunction("__config");
            if (configFunction == null) return false;
            Value ret = callNow(configFunction, Collections.emptyList());
            if (!(ret instanceof MapValue)) return false;
            Map<Value, Value> config = ((MapValue) ret).getMap();
            setPerPlayer(config.getOrDefault(new StringValue("scope"), new StringValue("player")).getString().equalsIgnoreCase("player"));
            persistenceRequired = config.getOrDefault(new StringValue("stay_loaded"), Value.TRUE).getBoolean();

            appConfig = config;
        }
        catch (NullPointerException ignored)
        {
            return false;
        }
        return true;
    }

    static class ListComparator<T extends Comparable<T>> implements Comparator<Pair<List<T>,?>>
    {
        @Override
        public int compare(Pair<List<T>,?> p1, Pair<List<T>,?> p2) {
            List<T> o1 = p1.getKey();
            List<T> o2 = p2.getKey();
            for (int i = 0; i < Math.min(o1.size(), o2.size()); i++) {
                int c = o1.get(i).compareTo(o2.get(i));
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(o1.size(), o2.size());
        }
    }

    public Boolean addAppCommands(Consumer<ITextComponent> notifier) {
        //todo app commands
        return false;
    }

    @Override
    protected Module getModuleOrLibraryByName(String name)
    {
        Module module = scriptServer.getModule(name, true);
        if (module == null || module.getCode() == null)
            throw new InternalExpressionException("Unable to locate package: "+name);
        return module;
    }

    @Override
    protected void runModuleCode(Context c, Module module)
    {
        CarpetContext cc = (CarpetContext)c;
        CarpetExpression ex = new CarpetExpression(module, module.getCode(), cc.s, cc.origin);
        ex.getExpr().asATextSource();
        ex.scriptRunCommand(this, cc.origin);
    }

    @Override
    public void delFunction(Module module, String funName)
    {
        super.delFunction(module, funName);
        // mcarpet
        if (funName.startsWith("__on_"))
        {
            // this is nasty, we have the host and function, yet we add it via names, but hey - works for now
            String event = funName.replaceFirst("__on_","");
            scriptServer.events.removeBuiltInEvent(event, this, funName);
        }
    }

    public List<CarpetScriptHost> retrieveForExecution(ICommandSender source, String optionalTarget)
    {
        List<CarpetScriptHost> targets = new ArrayList<>();
        if (perUser)
        {
            if (optionalTarget == null)
            {
                for (EntityPlayerMP player : source.getServer().getPlayerList().getPlayers())
                {
                    CarpetScriptHost host = (CarpetScriptHost) retrieveForExecution(player.getName());
                    targets.add(host);
                    if (host.errorSnooper == null) host.setChatErrorSnooper(player);
                }
            }
            else
            {
                EntityPlayerMP player = source.getServer().getPlayerList().getPlayerByUsername(optionalTarget);
                if (player != null)
                {
                    CarpetScriptHost host = (CarpetScriptHost) retrieveForExecution(player.getName());
                    targets.add(host);
                    if (host.errorSnooper == null) host.setChatErrorSnooper(player);
                }
            }
        }
        else
        {
            targets.add(this);
            if (this.errorSnooper == null) this.setChatErrorSnooper(source);
        }
        return targets;
    }

    public CarpetScriptHost retrieveOwnForExecution(ICommandSender source)
    {
        if (!perUser)
        {
            if (errorSnooper == null) setChatErrorSnooper(source);
            return this;
        }
        // user based
        EntityPlayerMP player;
        try
        {
            player = (EntityPlayerMP) source.getCommandSenderEntity();
        }
        catch (ClassCastException ignored)
        {
            throw new InternalExpressionException("Cannot run player based apps without the player context");
        }
        CarpetScriptHost userHost = (CarpetScriptHost)retrieveForExecution(player.getName());
        if (userHost.errorSnooper == null) userHost.setChatErrorSnooper(source);
        return userHost;
    }

    public Value handleCommand(ICommandSender source, FunctionValue function, List<Value> args)
    {
        try
        {
            return call(source, function, args);
        }
        catch (CarpetExpressionException exc)
        {
            handleErrorWithStack("Error while running custom command", exc);
            return Value.NULL;
        }
        catch (ArithmeticException ae)
        {
            handleErrorWithStack("Math doesn't compute", ae);
            return Value.NULL;
        }
    }

    public Value callLegacy(ICommandSender source, String call, List<Integer> coords, String arg)
    {
        if (CarpetServer.scriptServer.stopAll)
            throw new CarpetExpressionException("SCARPET PAUSED", null);
        FunctionValue function = getFunction(call);
        if (function == null)
            throw new CarpetExpressionException("UNDEFINED", null);
        List<LazyValue> argv = new ArrayList<>();
        if (coords != null)
            for (Integer i: coords)
                argv.add( (c, t) -> new NumericValue(i));
        String sign = "";
        for (Tokenizer.Token tok : Tokenizer.simplepass(arg))
        {
            switch (tok.type)
            {
                case VARIABLE:
                    LazyValue var = getGlobalVariable(tok.surface);
                    if (var != null)
                    {
                        argv.add(var);
                        break;
                    }
                case STRINGPARAM:
                    argv.add((c, t) -> new StringValue(tok.surface));
                    sign = "";
                    break;

                case LITERAL:
                    try
                    {
                        String finalSign = sign;
                        argv.add((c, t) ->new NumericValue(finalSign+tok.surface));
                        sign = "";
                    }
                    catch (NumberFormatException exception)
                    {
                        throw new CarpetExpressionException("Fail: "+sign+tok.surface+" seems like a number but it is" +
                                " not a number. Use quotes to ensure its a string", null);
                    }
                    break;
                case HEX_LITERAL:
                    try
                    {
                        String finalSign = sign;
                        argv.add((c, t) -> new NumericValue(new BigInteger(finalSign+tok.surface.substring(2), 16).doubleValue()));
                        sign = "";
                    }
                    catch (NumberFormatException exception)
                    {
                        throw new CarpetExpressionException("Fail: "+sign+tok.surface+" seems like a number but it is" +
                                " not a number. Use quotes to ensure its a string", null);
                    }
                    break;
                case OPERATOR:
                case UNARY_OPERATOR:
                    if ((tok.surface.equals("-") || tok.surface.equals("-u")) && sign.isEmpty())
                    {
                        sign = "-";
                    }
                    else
                    {
                        throw new CarpetExpressionException("Fail: operators, like " + tok.surface + " are not " +
                                "allowed in invoke", null);
                    }
                    break;
                case FUNCTION:
                    throw new CarpetExpressionException("Fail: passing functions like "+tok.surface+"() to invoke is " +
                            "not allowed", null);
                case OPEN_PAREN:
                case COMMA:
                case CLOSE_PAREN:
                case MARKER:
                    throw new CarpetExpressionException("Fail: "+tok.surface+" is not allowed in invoke", null);
            }
        }
        List<String> args = function.getArguments();
        if (argv.size() != args.size())
        {
            String error = "Fail: stored function "+call+" takes "+args.size()+" arguments, not "+argv.size()+ ":\n";
            for (int i = 0; i < max(argv.size(), args.size()); i++)
            {
                error += (i<args.size()?args.get(i):"??")+" => "+(i<argv.size()?argv.get(i).evalValue(null).getString():"??")+"\n";
            }
            throw new CarpetExpressionException(error, null);
        }
        try
        {
            // TODO: this is just for now - invoke would be able to invoke other hosts scripts
            Context context = new CarpetContext(this, source, BlockPos.ORIGIN);
            return function.getExpression().evalValue(
                    () -> function.lazyEval(context, Context.VOID, function.getExpression(), function.getToken(), argv),
                    context,
                    Context.VOID
            );
        }
        catch (ExpressionException e)
        {
            throw new CarpetExpressionException(e.getMessage(), e.stack);
        }
    }

    public Value call(ICommandSender source, FunctionValue function, List<Value> suppliedArgs)
    {
        if (CarpetServer.scriptServer.stopAll)
            throw new CarpetExpressionException("SCARPET PAUSED", null);

        List<LazyValue> argv = FunctionValue.lazify(suppliedArgs);

        List<String> args = function.getArguments();
        if (argv.size() != args.size())
        {
            String error = "Fail: stored function "+function.getPrettyString()+" takes "+args.size()+" arguments, not "+argv.size()+ ":\n";
            for (int i = 0; i < max(argv.size(), args.size()); i++)
            {
                error += (i<args.size()?args.get(i):"??")+" => "+(i<argv.size()?argv.get(i).evalValue(null).getString():"??")+"\n";
            }
            throw new CarpetExpressionException(error, null);
        }
        try
        {
            // TODO: this is just for now - invoke would be able to invoke other hosts scripts
            Context context = new CarpetContext(this, source, BlockPos.ORIGIN);
            return function.getExpression().evalValue(
                    () -> function.lazyEval(context, Context.VOID, function.getExpression(), function.getToken(), argv),
                    context,
                    Context.VOID
            );
        }
        catch (ExpressionException e)
        {
            throw new CarpetExpressionException(e.getMessage(), e.stack);
        }
    }

    public Value callUDF(BlockPos pos, ICommandSender source, FunctionValue fun, List<Value> argv) throws InvalidCallbackException
    {
        if (CarpetServer.scriptServer.stopAll)
            return Value.NULL;
        try { // cause we can't throw checked exceptions in lambda. Left if be until need to handle these more gracefully
            fun.assertArgsOk(argv, (b) -> {
                throw new InternalExpressionException("");
            });
        }
        catch (InternalExpressionException ignored)
        {
            throw new InvalidCallbackException();
        }
        try
        {
            // TODO: this is just for now - invoke would be able to invoke other hosts scripts
            Context context = new CarpetContext(this, source, pos);
            return fun.getExpression().evalValue(
                    () -> fun.lazyEval(context, Context.VOID, fun.getExpression(), fun.getToken(), FunctionValue.lazify(argv)),
                    context,
                    Context.VOID);
        }
        catch (ExpressionException e)
        {
            handleExpressionException("Callback failed", e);
        }
        return Value.NULL;
    }

    public Value callNow(FunctionValue fun, List<Value> arguments)
    {
        EntityPlayerMP player = (user==null)?null:scriptServer.server.getPlayerList().getPlayerByUsername(user);
        ICommandSender source = (player != null)?player:scriptServer.server;
        try
        {
            return callUDF(BlockPos.ORIGIN, source, fun, arguments);
        }
        catch (InvalidCallbackException ignored)
        {
            return Value.NULL;
        }
    }


    @Override
    public void onClose()
    {
        super.onClose();
        FunctionValue closing = getFunction("__on_close");
        if (closing != null && (parent != null || !isPerUser()))
            // either global instance of a global task, or
            // user host in player scoped app
        {
            callNow(closing, Collections.emptyList());
        }
        if (user == null)
        {

            String markerName = Auxiliary.MARKER_STRING + "_" + ((getName() == null) ? "" : getName());
            for (WorldServer world : scriptServer.server.worlds)
            {
                for (Entity e : world.getEntities(EntityArmorStand.class, (as) -> as.getTags().contains(markerName)))
                {
                    e.setDead();
                }
            }
            if (this.saveTimeout > 0)
                dumpState();
        }
    }

    private void dumpState()
    {
        Module.saveData(main, globalState);
    }

    private NBTBase loadState()
    {
        return Module.getData(main);
    }

    public NBTBase readFileTag(FileArgument fdesc)
    {
        if (getName() == null && !fdesc.isShared) return null;
        if (fdesc.resource != null)
            return fdesc.getNbtData(main);
        if (parent == null)
            return globalState;
        return ((CarpetScriptHost)parent).globalState;
    }

    public boolean writeTagFile(NBTBase tag, FileArgument fdesc)
    {
        if (getName() == null && !fdesc.isShared) return false; // if belongs to an app, cannot be default host.

        if (fdesc.resource != null)
        {
            return fdesc.saveNbtData(main, tag);
        }

        CarpetScriptHost responsibleHost = (parent != null)?(CarpetScriptHost) parent:this;
        responsibleHost.globalState = tag;
        if (responsibleHost.saveTimeout == 0)
        {
            responsibleHost.dumpState();
            responsibleHost.saveTimeout = 200;
        }
        return true;
    }

    public boolean removeResourceFile(FileArgument fdesc)
    {
        if (getName() == null && !fdesc.isShared) return false; //
        return fdesc.dropExistingFile(main);
    }

    public boolean appendLogFile(FileArgument fdesc, List<String> data)
    {
        if (getName() == null && !fdesc.isShared) return false; // if belongs to an app, cannot be default host.
        return fdesc.appendToTextFile(main, data);
    }

    public List<String> readTextResource(FileArgument fdesc)
    {
        if (getName() == null && !fdesc.isShared) return null;
        return fdesc.listFile(main);
    }
    
    public JsonElement readJsonFile(FileArgument fdesc)
    {
        if (getName() == null && !fdesc.isShared) return null;
        return fdesc.readJsonFile(main);
    }

    public Stream<String> listFolder(FileArgument fdesc)
    {
        if (getName() == null && !fdesc.isShared) return null; //
        return fdesc.listFolder(main);
    }


    public void tick()
    {
        if (this.saveTimeout > 0)
        {
            this.saveTimeout --;
            if (this.saveTimeout == 0)
            {
                dumpState();
            }
        }
    }

    public void setChatErrorSnooper(ICommandSender source)
    {
        responsibleSource = source;
        errorSnooper = (expr, /*Nullable*/ token, message) -> {
            source.getCommandSenderEntity();


            String shebang = message;
            if (expr.module != null)
            {
                shebang += " in " + expr.module.getName() + "";
            }
            else
            {
                shebang += " in system chat";
            }
            if (token != null)
            {
                String[] lines = expr.getCodeString().split("\n");

                if (lines.length > 1)
                {
                    shebang += " at line " + (token.lineno + 1) + ", pos " + (token.linepos + 1);
                }
                else
                {
                    shebang += " at pos " + (token.pos + 1);
                }
                Messenger.m(source, "r " + shebang);
                if (lines.length > 1 && token.lineno > 0)
                {
                    Messenger.m(source, "l " + lines[token.lineno - 1]);
                }
                Messenger.m(source, "l " + lines[token.lineno].substring(0, token.linepos), "r  HERE>> ", "l " +
                        lines[token.lineno].substring(token.linepos));
                if (lines.length > 1 && token.lineno < lines.length - 1)
                {
                    Messenger.m(source, "l " + lines[token.lineno + 1]);
                }
            }
            else
            {
                Messenger.m(source, "r " + shebang);
            }
            return new ArrayList<>();
        };
    }

    @Override
    public void resetErrorSnooper()
    {
        responsibleSource = null;
        super.resetErrorSnooper();
    }

    public void handleErrorWithStack(String intro, Exception exception)
    {
        if (responsibleSource != null)
        {
            if (exception instanceof CarpetExpressionException) ((CarpetExpressionException) exception).printStack(responsibleSource);
            String message = exception.getMessage();
            Messenger.m(responsibleSource, "r "+intro+(message.isEmpty()?"":": "+message));
        }
        else
        {
            CarpetSettings.LOG.error(intro+": "+exception.getMessage());
        }
    }

    @Override
    public void handleExpressionException(String message, ExpressionException exc)
    {
        handleErrorWithStack(message, new CarpetExpressionException(exc.getMessage(), exc.stack));
    }

    public CarpetScriptServer getScriptServer()
    {
        return scriptServer;
    }

    @Override
    public boolean issueDeprecation(String feature)
    {
        if(super.issueDeprecation(feature))
        {
            Messenger.m(responsibleSource, "rb '"+feature+"' is deprecated and soon will be removed. Please consult the docs for their replacement");
            return true;
        }
        return false;
    }
}
