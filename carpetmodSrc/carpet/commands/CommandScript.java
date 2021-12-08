package carpet.commands;

import carpet.utils.Messenger;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommandScript extends CommandCarpetBase {

    /**
     * Gets the name of the command
     */
    @Override
    public String getName() {
        return "script";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    /**
     * Gets the usage string for the command.
     *
     * @param sender
     */
    @Override
    public String getUsage(ICommandSender sender) {
        return "/script run [code]";
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
        if (!command_enabled("commandScript", sender))
            return;

        if (args.length<2)
            throw new WrongUsageException(getUsage(sender));

        Messenger.m(sender, (Object[]) args);
        Messenger.m(sender, "This what you wanted?");
    }

    /**
     * Get a list of options for when the user presses the TAB key.
     * When using /script run, works as a kind of crappy IDE
     * todo do dis
     */
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if(args.length == 1) return Collections.singletonList("run");
        return Collections.emptyList();
    }
}
