package carpet.script.value;

import carpet.script.CarpetContext;
import carpet.script.exception.InternalExpressionException;
import carpet.script.exception.ThrowStatement;
import carpet.script.exception.Throwables;
import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;


public class BlockValue extends Value
{
    public static final BlockValue AIR = new BlockValue(Blocks.AIR.getDefaultState(), null, BlockPos.ORIGIN);
    public static final BlockValue NULL = new BlockValue(null, null, null);
    private IBlockState blockState;
    private final BlockPos pos;
    private final WorldServer world;
    private NBTTagCompound data;

    public static BlockValue fromCoords(CarpetContext c, int x, int y, int z) {
        return fromCoords(c, x, y, z, -1);
    }

    public static BlockValue fromCoords(CarpetContext c, int x, int y, int z, int dimension) {
        BlockPos pos = locateBlockPos(c, x,y,z);
        return new BlockValue(null, c.s.getServer().getWorld(dimension), pos);
    }

    private static final Map<String, BlockValue> bvCache= new HashMap<>();
    public static BlockValue fromString(String str) {
        Block b = Block.getBlockFromName(str);
        if(b == null) throw new ThrowStatement(str, Throwables.UNKNOWN_BLOCK);

        return new BlockValue(b.getDefaultState(), null, BlockPos.ORIGIN);
    }

    public static BlockPos locateBlockPos(CarpetContext c, int xpos, int ypos, int zpos)
    {
        return new BlockPos(c.origin.getX() + xpos, c.origin.getY() + ypos, c.origin.getZ() + zpos);
    }

    public IBlockState getBlockState()
    {
        if (blockState != null)
        {
            return blockState;
        }
        if (world != null && pos != null)
        {
            blockState = world.getBlockState(pos);
            return blockState;
        }
        throw new InternalExpressionException("Attempted to fetch block state without world or stored block state");
    }

    public static TileEntity getBlockEntity(WorldServer world, BlockPos pos)
    {
        if (world.getMinecraftServer().isCallingFromMinecraftThread())
            return world.getTileEntity(pos);
        else
            return world.getChunk(pos).getTileEntity(pos, Chunk.EnumCreateEntityType.IMMEDIATE);
    }


    public NBTTagCompound getData()
    {
        if (data != null)
        {
            if (data.isEmpty())
                return null;
            return data;
        }
        if (world != null && pos != null)
        {
            TileEntity be = getBlockEntity(world, pos);
            NBTTagCompound tag = new NBTTagCompound();
            if (be == null)
            {
                data = tag;
                return null;
            }
            data = be.writeToNBT(tag);
            return data;
        }
        return null;
    }

    public BlockValue(IBlockState state, WorldServer world, BlockPos position)
    {
        this.world = world;
        blockState = state;
        pos = position;
        data = null;
    }

    public BlockValue(IBlockState state, WorldServer world, BlockPos position, NBTTagCompound nbt)
    {
        this.world = world;
        blockState = state;
        pos = position;
        data = nbt;
    }


    @Override
    public String getString()
    {
        return getBlockState().getBlock().getLocalizedName();
    }

    @Override
    public boolean getBoolean()
    {
        return this != NULL && getBlockState().getBlock()!=Blocks.AIR;
    }

    @Override
    public String getTypeString()
    {
        return "block";
    }

    @Override
    public Value clone()
    {
        return new BlockValue(blockState, world, pos, data);
    }

    @Override
    public int hashCode() {
        return ("b"+getString()).hashCode();
    }

    public BlockPos getPos() {
        return pos;
    }

    public WorldServer getWorld() { return world;}

    @Override
    public NBTBase toTag(boolean force)
    {
        if (!force) throw new NBTSerializableValue.IncompatibleTypeException(this);
        // follows falling block convertion
        NBTTagCompound tag =  new NBTTagCompound();
        NBTTagCompound state = new NBTTagCompound();
        IBlockState s = getBlockState();
        state.setString("Name", s.toString());
        ImmutableMap<IProperty<?>, Comparable<?>> properties = s.getProperties();
        if (!properties.isEmpty())
        {
            NBTTagCompound props = new NBTTagCompound();
            for (IProperty<?> p: properties.keySet())
            {
                props.setTag(p.getName(), NBTTagString.of(s.getValue(p).toString().toLowerCase(Locale.ROOT)));
            }
            state.setTag("Properties", props);
        }
        tag.setTag("IBlockState", state);
        NBTTagCompound dataTag = getData();
        if (dataTag != null)
        {
            tag.setTag("TileEntityData", dataTag);
        }
        return tag;
    }

    public enum SpecificDirection {
        UP("up",0.5, 0.0, 0.5, EnumFacing.UP),

        UPNORTH ("up-north", 0.5, 0.0, 0.4, EnumFacing.UP),
        UPSOUTH ("up-south", 0.5, 0.0, 0.6, EnumFacing.UP),
        UPEAST("up-east", 0.6, 0.0, 0.5, EnumFacing.UP),
        UPWEST("up-west", 0.4, 0.0, 0.5, EnumFacing.UP),

        DOWN("down", 0.5, 1.0, 0.5, EnumFacing.DOWN),

        DOWNNORTH ("down-north", 0.5, 1.0, 0.4, EnumFacing.DOWN),
        DOWNSOUTH ("down-south", 0.5, 1.0, 0.6, EnumFacing.DOWN),
        DOWNEAST("down-east", 0.6, 1.0, 0.5, EnumFacing.DOWN),
        DOWNWEST("down-west", 0.4, 1.0, 0.5, EnumFacing.DOWN),


        NORTH ("north", 0.5, 0.4, 1.0, EnumFacing.NORTH),
        SOUTH ("south", 0.5, 0.4, 0.0, EnumFacing.SOUTH),
        EAST("east", 0.0, 0.4, 0.5, EnumFacing.EAST),
        WEST("west", 1.0, 0.4, 0.5, EnumFacing.WEST),

        NORTHUP ("north-up", 0.5, 0.6, 1.0, EnumFacing.NORTH),
        SOUTHUP ("south-up", 0.5, 0.6, 0.0, EnumFacing.SOUTH),
        EASTUP("east-up", 0.0, 0.6, 0.5, EnumFacing.EAST),
        WESTUP("west-up", 1.0, 0.6, 0.5, EnumFacing.WEST);

        public final String name;
        public final Vec3d hitOffset;
        public final EnumFacing facing;

        private static final Map<String, SpecificDirection> DIRECTION_MAP = Arrays.stream(values()).collect(Collectors.toMap(SpecificDirection::getName, d -> d));


        SpecificDirection(String name, double hitx, double hity, double hitz, EnumFacing blockFacing)
        {
            this.name = name;
            this.hitOffset = new Vec3d(hitx, hity, hitz);
            this.facing = blockFacing;
        }
        private String getName()
        {
            return name;
        }
    }
}
