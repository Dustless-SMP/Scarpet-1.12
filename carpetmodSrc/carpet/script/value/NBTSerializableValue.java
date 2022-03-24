package carpet.script.value;

import adsen.scarpet.interpreter.parser.Fluff;
import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.ContainerValueInterface;
import adsen.scarpet.interpreter.parser.value.ListValue;
import adsen.scarpet.interpreter.parser.value.MapValue;
import adsen.scarpet.interpreter.parser.value.NumericValue;
import adsen.scarpet.interpreter.parser.value.StringValue;
import adsen.scarpet.interpreter.parser.value.Value;
import com.google.gson.JsonElement;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagEnd;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class NBTSerializableValue extends Value implements ContainerValueInterface {
    private String nbtString = null;
    private NBTBase nbtTag = null;
    private Supplier<NBTBase> nbtSupplier = null;
    private boolean owned = false;

    private NBTSerializableValue() {
    }

    public NBTSerializableValue(String nbtString) {
        nbtSupplier = () ->
        {
            try {
                return ValueConversions.nbtFromString(nbtString);
            } catch (NBTException e) {
                throw new InternalExpressionException("Incorrect NBT data: " + nbtString);
            }
        };
        owned = true;
    }

    public NBTSerializableValue(NBTBase tag) {
        nbtTag = tag;
        owned = true;
    }

    public NBTSerializableValue(Supplier<NBTBase> tagSupplier) {
        nbtSupplier = tagSupplier;
    }

    public static Value of(NBTBase tag) {
        if (tag == null) return Value.NULL;
        return new NBTSerializableValue(tag);
    }

    public static Value fromStack(ItemStack stack) {
        if (stack.hasTagCompound()) {
            NBTSerializableValue value = new NBTSerializableValue();
            value.nbtSupplier = stack::writeToNBT;
            return value;
        }
        return Value.NULL;
    }

    public static String nameFromRegistryId(ResourceLocation id) {
        if (id == null) // should be Value.NULL
            return "";
        if (id.getNamespace().equals("minecraft"))
            return id.getPath();
        return id.toString();
    }

    //todo parse nbt from string
    public static NBTSerializableValue parseString(String nbtString, boolean fail) {
        NBTBase tag;
        try {
            tag = ValueConversions.nbtFromString(nbtString);
        } catch (NBTException e) {
            if (fail) throw new InternalExpressionException("Incorrect NBT tag: " + nbtString);
            return null;
        }
        NBTSerializableValue value = new NBTSerializableValue(tag);
        value.nbtString = null;
        return value;
    }

    public static Container getInventoryAt(World world, BlockPos blockPos) {
        Container inventory = null;
        //todo getting inventory at block
        //IBlockState blockState = world.getBlockState(blockPos);
        //Block block = blockState.getBlock();
        //TileEntity tileEntity = world.getTileEntity(blockPos);
        //if(tileEntity!=null && tileEntity instanceof TileEntityLockable){
        //    TileEntityLockable tel = (TileEntityLockable) tileEntity;
        //
        //}
        //
        //if (block instanceof BlockContainer) {
        //    inventory = ((BlockContainer)block).
        //} else if (blockState.hasBlockEntity()) {
        //    TileEntity blockEntity = BlockValue.getBlockEntity(world, blockPos);
        //    if (blockEntity instanceof Container) {
        //        inventory = (Container) blockEntity;
        //        if (inventory instanceof TileEntityChest && block instanceof BlockChest) {
        //            inventory = BlockChest.getContainer((BlockChest) block, blockState, world, blockPos, true);
        //        }
        //    }
        //}
        //
        //if (inventory == null) {
        //    List<Entity> list = world.getEntities(
        //            (Entity) null,
        //            new AxisAlignedBB(
        //                    blockPos.getX() - 0.5D, blockPos.getY() - 0.5D, blockPos.getZ() - 0.5D,
        //                    blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D)
        //    );
        //    if (!list.isEmpty()) {
        //        inventory = (Container) list.get(world.random.nextInt(list.size()));
        //    }
        //}

        return inventory;
    }

    public static int validateSlot(int slot, Container inv) {
        int invSize = inv.getInventory().size();
        if (slot < 0)
            slot = invSize + slot;
        if (slot < 0 || slot >= invSize)
            return invSize; // outside of inventory
        return slot;
    }

    private static Value decodeSimpleTag(NBTBase t) {
        if (t instanceof NBTPrimitive) {
            if (t instanceof NBTTagLong || t instanceof NBTTagInt) {// short and byte will never exceed float's precision, even int won't
                return NumericValue.of(((NBTPrimitive) t).getLong());
            }
            return NumericValue.of(((NBTPrimitive) t).getDouble());
        }
        if (t instanceof NBTTagString)
            return StringValue.of(((NBTTagString) t).getString());
        if (t instanceof NBTTagEnd)
            return Value.NULL;

        throw new InternalExpressionException("How did we get here: Unexpected nbt element class: " + NBTBase.getTypeName(t.getId()));

    }

    private static Value decodeTag(NBTBase t) {
        if (t instanceof NBTTagCompound || t instanceof NBTTagList)
            return new NBTSerializableValue(() -> t);
        return decodeSimpleTag(t);
    }

    private static Value decodeTagDeep(NBTBase t) {
        if (t instanceof NBTTagCompound) {
            Map<Value, Value> pairs = new HashMap<>();
            NBTTagCompound ctag = (NBTTagCompound) t;
            for (String key : ctag.getKeySet()) {
                pairs.put(new StringValue(key), decodeTagDeep(ctag.getTag(key)));
            }
            return MapValue.wrap(pairs);
        }
        if (t instanceof NBTTagList) {
            List<Value> elems = new ArrayList<>();
            NBTTagList ltag = (NBTTagList) t;
            for (NBTBase elem : ltag.getTagList()) {
                elems.add(decodeTagDeep(elem));
            }
            return ListValue.wrap(elems);
        }
        return decodeSimpleTag(t);
    }

    public static Value fromValue(Value v) {
        if (v instanceof NBTSerializableValue)
            return v;
        if (v == null)
            return Value.NULL;
        return NBTSerializableValue.parseString(v.getString(), true);
    }

    /**
     * Allows me to do a DFS search through compound tags
     */
    public static void operateOnCompound(NBTTagCompound compoundTag, Fluff.TriConsumer<NBTTagCompound, String, NBTBase> consumer) {
        NBTBase nbtBase;
        for (String s : compoundTag.getKeySet()) {
            nbtBase = compoundTag.getTag(s);
            consumer.accept(compoundTag, s, nbtBase);
            if (nbtBase instanceof NBTTagCompound)
                operateOnCompound((NBTTagCompound) nbtBase, consumer);
        }
    }

    @Override
    public Value clone() {
        // sets only nbttag, even if emtpy;
        NBTSerializableValue copy = new NBTSerializableValue(nbtTag);
        copy.nbtSupplier = this.nbtSupplier;
        copy.nbtString = this.nbtString;
        copy.owned = this.owned;
        return copy;
    }

    public Value deepcopy() {
        NBTSerializableValue copy = (NBTSerializableValue) clone();
        copy.owned = false;
        ensureOwnership();
        return copy;
    }

    public Value fromConstant() {
        return deepcopy();
    }

    public Value toValue() {
        return decodeTagDeep(this.getTag());
    }

    public NBTBase getTag() {
        if (nbtTag == null)
            nbtTag = nbtSupplier.get();
        return nbtTag;
    }

    @Override
    public boolean equals(final Object o) {
        if (o instanceof NBTSerializableValue)
            return getTag().equals(((NBTSerializableValue) o).getTag());
        return super.equals(o);
    }

    @Override
    public String getString() {
        if (nbtString == null)
            nbtString = getTag().toString();
        return nbtString;
    }

    @Override
    public boolean getBoolean() {
        NBTBase tag = getTag();
        if (tag instanceof NBTTagCompound)
            return !tag.isEmpty();
        if (tag instanceof NBTTagList)
            return !tag.isEmpty();
        if (tag instanceof NBTPrimitive)
            return ((NBTPrimitive) tag).getDouble() != 0.0;
        if (tag instanceof NBTTagString)
            return !((NBTTagString) tag).getString().isEmpty();
        return true;
    }

    @Override
    public JsonElement toJson() {
        return null;
    }

    public NBTTagCompound getCompoundTag() {
        try {
            ensureOwnership();
            return (NBTTagCompound) getTag();
        } catch (ClassCastException e) {
            throw new InternalExpressionException(getString() + " is not a valid compound tag");
        }
    }

    @Override
    public boolean put(Value where, Value value) {
        return put(where, value, new StringValue("replace"));
    }

    @Override
    public boolean put(Value where, Value value, Value conditions) {
        /* todo putting values into nbt - like map ig?
        //ensureOwnership();
        //NbtPathArgument.NbtPath path = cachePath(where.getString());
        //NBTBase tagToInsert = value instanceof NBTSerializableValue ?
        //        ((NBTSerializableValue) value).getTag() :
        //        new NBTSerializableValue(value.getString()).getTag();
        //boolean modifiedTag;
        //if (conditions instanceof NumericValue) {
        //    modifiedTag = modify_insert((int) ((NumericValue) conditions).getLong(), path, tagToInsert);
        //} else {
        //    String ops = conditions.getString();
        //    if (ops.equalsIgnoreCase("merge")) {
        //        modifiedTag = modify_merge(path, tagToInsert);
        //    } else if (ops.equalsIgnoreCase("replace")) {
        //        modifiedTag = modify_replace(path, tagToInsert);
        //    } else {
        //        return false;
        //    }
        //}
        //if (modifiedTag) dirty();
        //return modifiedTag;


    }

    private boolean modify_insert(int index, NbtPathArgument.NbtPath nbtPath, NBTBase newElement) {
        return modify_insert(index, nbtPath, newElement, this.getTag());
    }

    private boolean modify_insert(int index, NbtPathArgument.NbtPath nbtPath, NBTBase newElement, NBTBase currentTag) {
        Collection<NBTBase> targets;
        try {
            targets = nbtPath.getOrCreate(currentTag, NBTTagList::new);
        } catch (CommandSyntaxException e) {
            return false;
        }

        boolean modified = false;
        for (NBTBase target : targets) {
            if (!(target instanceof NBTTagList)) {
                continue;
            }
            try {
                NBTTagList targetList = (NBTTagList) target;
                if (!targetList.addTag(index < 0 ? targetList.size() + index + 1 : index, newElement.copy()))
                    return false;
                modified = true;
            } catch (IndexOutOfBoundsException ignored) {
            }
        }
        return modified;
    }

    private boolean modify_merge(NbtPathArgument.NbtPath nbtPath, NBTBase replacement) //nbtPathArgumentType$NbtPath_1, list_1)
    {
        if (!(replacement instanceof NBTTagCompound)) {
            return false;
        }
        NBTBase ownTag = getTag();
        try {
            for (NBTBase target : nbtPath.getOrCreate(ownTag, NBTTagCompound::new)) {
                if (!(target instanceof NBTTagCompound)) {
                    continue;
                }
                ((NBTTagCompound) target).merge((NBTTagCompound) replacement);
            }
        } catch (CommandSyntaxException ignored) {
            return false;
        }
        return true;
    }

    private boolean modify_replace(NbtPathArgument.NbtPath nbtPath, NBTBase replacement) //nbtPathArgumentType$NbtPath_1, list_1)
    {
        NBTBase tag = getTag();
        String pathText = nbtPath.toString();
        if (pathText.endsWith("]")) // workaround for array replacement or item in the array replacement
        {
            if (nbtPath.remove(tag) == 0)
                return false;
            Pattern pattern = Pattern.compile("\\[[^\\[]*]$");
            Matcher matcher = pattern.matcher(pathText);
            if (!matcher.find()) // malformed path
            {
                return false;
            }
            String arrAccess = matcher.group();
            int pos;
            if (arrAccess.length() == 2) // we just removed entire array
                pos = 0;
            else {
                try {
                    pos = Integer.parseInt(arrAccess.substring(1, arrAccess.length() - 1));
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            NbtPathArgument.NbtPath newPath = cachePath(pathText.substring(0, pathText.length() - arrAccess.length()));
            return modify_insert(pos, newPath, replacement, tag);
        }
        try {
            nbtPath.set(tag, () -> replacement);
        } catch (CommandSyntaxException e) {
            return false;
        }
        return true;
    }

    */
        return false;
    }

    @Override
    public Value get(Value value) {
        NBTBase tag;

        if (nbtTag != null) {
            tag = nbtTag;
        } else {
            tag = nbtSupplier.get();
        }

        if (tag instanceof NBTTagCompound) {
            return NBTSerializableValue.decodeTag(((NBTTagCompound) tag).getTag(value.getString()));
        }
        if (tag instanceof NBTTagList) {
            return NBTSerializableValue.decodeTag(((NBTTagList) tag).get(value.readInt()));
        }

        return Value.NULL;
    }

    @Override
    public boolean has(Value where) {
        return ((NBTTagCompound) this.nbtTag).hasKey(where.getString());
    }

    private void ensureOwnership() {
        if (!owned) {
            nbtTag = getTag().copy();
            nbtString = null;
            nbtSupplier = null;  // just to be sure
            owned = true;
        }
    }

    private void dirty() {
        nbtString = null;
    }

    @Override
    public boolean delete(Value where) {
        // todo deleting from nbt - removing key from map and index from list
        //NbtPathArgument.NbtPath path = cachePath(where.getString());
        //ensureOwnership();
        //int removed = path.remove(getTag());
        //if (removed > 0) {
        //    dirty();
        //    return true;
        //}
        if (nbtTag instanceof NBTTagList) {
            ((NBTTagList) nbtTag).removeTag(where.readInt());
        }

        return false;
    }

    @Override
    public String getTypeString() {
        return "nbt";
    }

    @Override
    public NBTBase toNbt() {
        ensureOwnership();
        return getTag();
    }

}
