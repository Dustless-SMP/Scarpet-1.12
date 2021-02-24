package carpet.commands;

import carpet.CarpetSettings;
import carpet.script.Expression;
import carpet.script.Tokenizer;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;

import java.util.Arrays;
import java.util.List;

public class CommandScript extends CommandCarpetBase{

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
     * @param args
     */
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!command_enabled("commandScript", sender)) return;
        if (args.length == 0 || !args[0].equals("run")) {
            throw new WrongUsageException(getUsage(sender));
        }
        List<String> scriptList = Arrays.asList(args);
        StringBuilder scriptBuilder = new StringBuilder();

        scriptList.listIterator(1).forEachRemaining(scriptBuilder::append);

        String script = scriptBuilder.toString();


        Expression.LOGGER.info("Initialising Tokeniser...");
        Tokenizer tok = new Tokenizer(script);
        Expression.LOGGER.info("Tokenised script, printing tokens...");

        while(tok.hasNext()){
            Expression.LOGGER.info("Token: " + tok.next().toString());
        }
        Expression.LOGGER.info("Finished printing tokens!");
    }
}
