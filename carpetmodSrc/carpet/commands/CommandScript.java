package carpet.commands;

import carpet.script.Expression;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

public class CommandScript extends CommandCarpetBase{


    static{
        String expression = "1+2";
        Expression expr = new Expression(expression, false, true);

        Expression.LOGGER.info("Scarpet evaluation of "+expression + ": " +expr.evalValue.getString());
    }

    /**
     * Gets the name of the command
     */
    @Override
    public String getName() {
        return "script";
    }

    /**
     * Gets the usage string for the command.
     *
     * @param sender
     */
    @Override
    public String getUsage(ICommandSender sender) {
        return "/script run [script]";
    }

    /**
     * Callback for when the command is executed
     *
     * @param server
     * @param sender
     * @param args Basically the script, in scarpet format.
     */
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!command_enabled("commandScript", sender)) return;
        if (args.length == 0 || !args[0].equals("run")) {
            throw new WrongUsageException(getUsage(sender));
        }
        List<String> scriptList = Arrays.asList(args);
        StringBuilder scriptBuilder = new StringBuilder();

        scriptList.listIterator(1).forEachRemaining(scriptBuilder::append); // removing 'run' bit

        String script = scriptBuilder.toString();

        Logger logger = Expression.LOGGER;
        Expression expression = new Expression(script, false, true);
        logger.info(expression.evalValue.getString());
    }
}
