package carpet.script.value;

import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.Value;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Locale;

public class BlockValue extends Value {
    public static final BlockValue AIR = new BlockValue(Blocks.AIR.getDefaultState(), null, BlockPos.ORIGIN);
    public static final BlockValue NULL = new BlockValue(null, null, null);
    private final BlockPos pos;
    private final World world;
    private IBlockState blockState;
    private NBTTagCompound data;

    public BlockValue(IBlockState state, World world, BlockPos position) {
        this.world = world;
        blockState = state;
        pos = position;
        data = null;
    }

    public BlockValue(IBlockState state, World world, BlockPos position, NBTTagCompound nbt) {
        this.world = world;
        blockState = state;
        pos = position;
        data = nbt;
    }

    public static BlockValue fromCoords(World w, int x, int y, int z) {
        BlockPos pos = locateBlockPos(x, y, z);
        return new BlockValue(null, w, pos);
    }

    public static BlockPos locateBlockPos(int xpos, int ypos, int zpos) {
        return new BlockPos(xpos, ypos, zpos);
    }

    public IBlockState getBlockState() {
        if (blockState != null) {
            return blockState;
        }
        if (world != null && pos != null) {
            blockState = world.getBlockState(pos);
            return blockState;
        }
        throw new InternalExpressionException("Attempted to fetch block state without world or stored block state");
    }

    public NBTTagCompound getData() {
        if (data != null) {
            if (data.isEmpty())
                return null;
            return data;
        }
        if (world != null && pos != null) {
            TileEntity be = world.getTileEntity(pos);
            if (be == null) {
                data = new NBTTagCompound();
                return null;
            }
            data = be.writeToNBT(data);
            return data;
        }
        return null;
    }

    @Override
    public String getString() {
        return blockState.toString();
    }

    @Override
    public boolean getBoolean() {
        return this != NULL && !world.isAirBlock(pos);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(getString());
    }

    @Override
    public String getTypeString() {
        return "block";
    }

    @Override
    public Value clone() {
        return new BlockValue(blockState, world, pos, data);
    }

    @Override
    public int hashCode() {
        return ("b" + getString() + (world != null ? world.toString() : "") + (pos != null ? pos.toString() : "")).hashCode();
    }

    public BlockPos getPos() {
        return pos;
    }

    public World getWorld() {
        return world;
    }

    @Override
    public NBTBase toNbt() {
        // follows falling block conversion
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagCompound state = new NBTTagCompound();
        IBlockState s = getBlockState();
        state.setString("Name", blockState.getBlock().toString());

        Collection<IProperty<? >> properties = s.getPropertyKeys();
        if (!properties.isEmpty()) {
            NBTTagCompound props = new NBTTagCompound();
            for (IProperty<?> p : properties) {
                props.setString(p.getName(), s.getValue(p).toString().toLowerCase(Locale.ROOT));
            }
            state.setTag("Properties", props);
        }
        tag.setTag("BlockState", state);
        NBTTagCompound dataTag = getData();
        if (dataTag != null) {
            tag.setTag("TileEntityData", dataTag);
        }
        return tag;
    }
}
