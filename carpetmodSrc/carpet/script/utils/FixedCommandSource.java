package carpet.script.utils;

import carpet.script.exception.InternalExpressionException;
import carpet.script.value.Value;
import com.sun.javafx.geom.Vec2f;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BinaryOperator;

public class FixedCommandSource implements ICommandSender {
    private Vec3d position;
    private WorldServer world;
    private final String simpleName;
    private final ITextComponent name;
    private final MinecraftServer server;
    private Entity entity;
    private ICommandSender resultConsumer;
    private Vec2f rotation;
    private final Value[] error;
    private final List<Value> chatOutput;

    public FixedCommandSource(ICommandSender original, Vec3d pos, Value[] error, List<Value> chatOutput)
    {
        position = pos;
        world = original.getWorldServer();
        simpleName = original.getName();
        name = original.getDisplayName();
        server = original.getServer();
        entity = original.getCommandSenderEntity();
        rotation = new Vec2f(0, 0);
        this.error = error;
        this.chatOutput = chatOutput;
    }

    public FixedCommandSource(WorldServer world, String simpleName, ITextComponent name, MinecraftServer server, Entity entity, Vec3d pos, Value[] error, List<Value> chatOutput){
        this.position = pos;
        this.world = world;
        this.simpleName = simpleName;
        this.name = name;
        this.server = server;
        this.entity = entity;
        rotation = new Vec2f(0, 0);
        this.error = error;
        this.chatOutput = chatOutput;
    }

    public ICommandSender withEntity(Entity entity)
    {
        this.entity = entity;
        return this;
    }

    public ICommandSender withPosition(Vec3d position)
    {
        this.position = position;
        return this;
    }

    public ICommandSender withRotation(Vec2f rotation)
    {
        this.rotation = rotation;
        return this;
    }

    public ICommandSender withConsumer(ICommandSender consumer)
    {
        this.resultConsumer = consumer;
        return this;
    }

    public ICommandSender mergeConsumers(ICommandSender consumer, BinaryOperator<ICommandSender> binaryOperator)
    {
        return this.withConsumer(binaryOperator.apply(this.resultConsumer, consumer));
    }

    public ICommandSender withSilent()
    {
        return this;
    }

    public ICommandSender withLevel(int level)
    {
        return this;
    }

    public ICommandSender withMaxLevel(int level)
    {
        return this;
    }

    public ICommandSender withWorld(WorldServer world) {
        this.world = world;
        return this;
    }

    @Override
    public ITextComponent getDisplayName()
    {
        return name;
    }

    @Override
    public boolean canUseCommand(int permLevel, String commandName) {
        return false;
    }

    @Override
    public String getName()
    {
        return simpleName;
    }

    @Override
    public BlockPos getPosition()
    {
        return new BlockPos(position);
    }

    @Override
    public World getEntityWorld() {
        return null;
    }

    @Nullable
    @Override
    public MinecraftServer getServer() {
        return null;
    }

    public WorldServer getWorld()
    {
        return world;
    }

    public Entity getEntity()
    {
        return entity;
    }

    public Entity getEntityOrThrow() throws InternalExpressionException
    {
        if (entity == null) {
            throw new InternalExpressionException("How did we get here? FixedCommandSource#getEntiyOrThrow");
        } else {
            return entity;
        }
    }

    public EntityPlayerMP getPlayer() throws InternalExpressionException{
        if (!(this.entity instanceof EntityPlayerMP)) {
            throw new InternalExpressionException("How did we get here? FixedCommandSource#getPlayer");
        } else {
            return (EntityPlayerMP)this.entity;
        }
    }

    public Vec2f getRotation()
    {
        return rotation;
    }

    public MinecraftServer getMinecraftServer()
    {
        return server;
    }

}
