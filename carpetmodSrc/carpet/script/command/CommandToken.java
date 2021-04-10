package carpet.script.command;

import carpet.script.CarpetScriptHost;
import carpet.script.exception.InternalExpressionException;
import carpet.script.value.FunctionValue;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


public class CommandToken implements Comparable<CommandToken>
{
    public String surface;
    public boolean isArgument;
    public CommandArgument type;

    private CommandToken(String surface, CommandArgument type )
    {
        this.surface = surface;
        this.type = type;
        isArgument = type != null;
    }

    public static CommandToken getToken(String source, CarpetScriptHost host)
    {
        // todo add more type checking and return null
        if (!source.startsWith("<"))
        {
            if (!source.matches("[_a-zA-Z]+")) return null;
            return new CommandToken(source, null);
        }
        source = source.substring(1, source.length()-1);
        if (!source.matches("[_a-zA-Z]+")) return null;
        CommandArgument arg = CommandArgument.getTypeForArgument(source, host);
        return new CommandToken(source, arg);
    }

    public static List<CommandToken> parseSpec(String spec, CarpetScriptHost host)
    {
        spec = spec.trim();
        if (spec.isEmpty()) return Collections.emptyList();
        List<CommandToken> elements = new ArrayList<>();
        for (String el: spec.split("\\s+"))
        {
            CommandToken tok = CommandToken.getToken(el, host);
            if (tok == null) throw new InternalExpressionException("Unrecognized command token: "+ el);
            elements.add(tok);
        }
        return elements;
    }

    public static String specFromSignature(FunctionValue function)
    {
        List<String> tokens = Lists.newArrayList(function.getString());
        for (String arg : function.getArguments()) tokens.add("<"+arg+">");
        return String.join(" ", tokens);
    }


    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommandToken that = (CommandToken) o;
        return surface.equals(that.surface) &&
                Objects.equals(type, that.type);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(surface, type);
    }

    @Override
    public int compareTo(CommandToken o)
    {
        if (isArgument && !o.isArgument) return 1;
        if (!isArgument && o.isArgument) return -1;
        return surface.compareTo(o.surface);
    }
}
