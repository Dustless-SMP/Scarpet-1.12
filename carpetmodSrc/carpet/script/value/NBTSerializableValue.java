package carpet.script.value;

import carpet.script.CarpetContext;
import carpet.script.LazyValue;
import carpet.script.exception.InternalExpressionException;
import carpet.script.exception.ThrowStatement;
import carpet.script.exception.Throwables;
import carpet.script.utils.EquipmentInventory;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.BlockChest;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTPrimitive;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.nbt.NBTBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class NBTSerializableValue extends Value implements ContainerValueInterface
{
    private String nbtString = null;
    private NBTBase nbtTag = null;
    private Supplier<NBTBase> nbtSupplier = null;
    private boolean owned = false;

    private NBTSerializableValue() {}

    public NBTSerializableValue(String nbtString) {
        nbtSupplier = () -> {
            try {
                return JsonToNBT.getTagFromJson(nbtString);
            }
            catch (NBTException e) {
                throw new InternalExpressionException("Incorrect NBT data: "+nbtString);
            }
        };
        owned = true;
    }

    public NBTSerializableValue(NBTBase tag)
    {
        nbtTag = tag;
        owned = true;
    }

    public NBTSerializableValue(Supplier<NBTBase> tagSupplier)
    {
        nbtSupplier = tagSupplier;
    }

    public static Value fromStack(ItemStack stack)
    {
        if (stack.hasTagCompound())
        {
            NBTSerializableValue value = new NBTSerializableValue();
            value.nbtSupplier = stack::getTagCompound;
            return value;
        }
        return Value.NULL;
    }
    /*todo figure out
    public static String nameFromRegistryId(Identifier id) {
        if (id == null) // should be Value.NULL
            return "";
        if (id.getNamespace().equals("minecraft"))
            return id.getPath();
        return id.toString();
    }
    */
    public static NBTSerializableValue parseString(String nbtString, boolean fail)
    {
        NBTBase tag;
        try {
            tag = JsonToNBT.getTagFromJson(nbtString);
        }
        catch (NBTException e) {
            if (fail) throw new InternalExpressionException("Incorrect NBT data: "+nbtString);
            return null;
        }
        NBTSerializableValue value = new NBTSerializableValue(tag);
        value.nbtString = null;
        return value;
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

    @Override
    public Value deepcopy()
    {
        NBTSerializableValue copy = (NBTSerializableValue) clone();
        copy.owned = false;
        ensureOwnership();
        return copy;
    }

    // stolen from HopperBlockEntity, adjusted for threaded operation
    /*todo getting inventory
    public static IInventory getInventoryAt(ServerWorld world, BlockPos blockPos)
    {
        IInventory inventory = null;
        BlockState blockState = world.getBlockState(blockPos);
        Block block = blockState.getBlock();
        if (block instanceof InventoryProvider) {
            inventory = ((InventoryProvider)block).getInventory(blockState, world, blockPos);
        } else if (block.hasBlockEntity()) {
            BlockEntity blockEntity = BlockValue.getBlockEntity(world, blockPos);
            if (blockEntity instanceof IInventory) {
                inventory = (IInventory)blockEntity;
                if (inventory instanceof ChestBlockEntity && block instanceof ChestBlock) {
                    inventory = ChestBlock.getInventory((ChestBlock)block, blockState, world, blockPos, true);
                }
            }
        }

        if (inventory == null) {
            List<Entity> list = world.getOtherEntities(
                    null,
                    new AxisAlignedBB(
                            blockPos.getX() - 0.5D, blockPos.getY() - 0.5D, blockPos.getZ() - 0.5D,
                            blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D),
                    EntityPredicates.VALID_INVENTORIES
            );
            if (!list.isEmpty()) {
                inventory = (IInventory)list.get(world.random.nextInt(list.size()));
            }
        }

        return inventory;
    }

    public static InventoryLocator locateInventory(CarpetContext c, List<LazyValue> params, int offset)
    {
        try
        {
            Value v1 = params.get(offset).evalValue(c);
            if (v1.isNull())
            {
                offset ++;
                v1 = params.get(offset).evalValue(c);
            }
            else if (v1 instanceof StringValue)
            {
                String strVal = v1.getString().toLowerCase(Locale.ROOT);
                if (strVal.equals("enderchest"))
                {
                    Value v2 = params.get(1 + offset).evalValue(c);
                    ServerPlayerEntity player = EntityValue.getPlayerByValue(c.s.getMinecraftServer(), v2);
                    if (player == null) throw new InternalExpressionException("enderchest inventory requires player argument");
                    return new InventoryLocator(player, player.getBlockPos(), player.getEnderChestInventory(), offset + 2, true);
                }
                if (strVal.equals("equipment"))
                {
                    Value v2 = params.get(1 + offset).evalValue(c);
                    if (!(v2 instanceof EntityValue)) throw new InternalExpressionException("Equipment inventory requires a living entity argument");
                    Entity e = ((EntityValue) v2).getEntity();
                    if (!(e instanceof LivingEntity)) throw new InternalExpressionException("Equipment inventory requires a living entity argument");
                    return new InventoryLocator(e, e.getBlockPos(), new EquipmentInventory((LivingEntity) e), offset + 2);
                }
                boolean isEnder = strVal.startsWith("enderchest_");
                if (isEnder) strVal = strVal.substring(11); // len("enderchest_")
                ServerPlayerEntity player = c.s.getMinecraftServer().getPlayerManager().getPlayer(strVal);
                if (player == null) throw new InternalExpressionException("String description of an inventory should either denote a player or player's enderchest");
                return new InventoryLocator(
                        player,
                        player.getBlockPos(),
                        isEnder ? player.getEnderChestInventory() : player.inventory,
                        offset + 1,
                        isEnder
                );
            }
            if (v1 instanceof EntityValue)
            {
                IInventory inv = null;
                Entity e = ((EntityValue) v1).getEntity();
                if (e instanceof PlayerEntity) inv = ((PlayerEntity) e).inventory;
                else if (e instanceof IInventory) inv = (IInventory) e;
                else if (e instanceof VillagerEntity) inv = ((VillagerEntity) e).getInventory();
                else if (e instanceof InventoryBearerInterface) inv = ((InventoryBearerInterface)e).getCMInventory();
                else if (e instanceof LivingEntity) return new InventoryLocator(e, e.getBlockPos(), new EquipmentInventory((MobEntity) e), offset+1);
                if (inv == null)
                    return null;

                return new InventoryLocator(e, e.getBlockPos(), inv, offset+1);
            }
            if (v1 instanceof BlockValue)
            {
                BlockPos pos = ((BlockValue) v1).getPos();
                if (pos == null)
                    throw new InternalExpressionException("Block to access inventory needs to be positioned in the world");
                IInventory inv = getInventoryAt(c.s.getWorld(), pos);
                if (inv == null)
                    return null;
                return new InventoryLocator(pos, pos, inv, offset+1);
            }
            if (v1 instanceof ListValue)
            {
                List<Value> args = ((ListValue) v1).getItems();
                BlockPos pos = new BlockPos(
                        NumericValue.asNumber(args.get(0)).getDouble(),
                        NumericValue.asNumber(args.get(1)).getDouble(),
                        NumericValue.asNumber(args.get(2)).getDouble());
                IInventory inv = getInventoryAt(c.s.getWorld(), pos);
                if (inv == null)
                    return null;
                return new InventoryLocator(pos, pos, inv, offset+1);
            }
            BlockPos pos = new BlockPos(
                    NumericValue.asNumber(v1).getDouble(),
                    NumericValue.asNumber(params.get(1 + offset).evalValue(c)).getDouble(),
                    NumericValue.asNumber(params.get(2 + offset).evalValue(c)).getDouble());
            IInventory inv = getInventoryAt(c.s.getWorld(), pos);
            if (inv == null)
                return null;
            return new InventoryLocator(pos, pos, inv, offset + 3);
        }
        catch (IndexOutOfBoundsException e)
        {
            throw new InternalExpressionException("IInventory should be defined either by three coordinates, a block value, or an entity");
        }
    }
     */
    /*todo item bs
    private static final Map<String,ItemStack> itemCache = new HashMap<>();

    public static ItemStack parseItem(String itemString)
    {
        return parseItem(itemString, null);
    }

    public static ItemStack parseItem(String itemString, NBTTagCompound customTag)
    {
        try
        {
            ItemStack res = itemCache.get(itemString);
            if (res != null)
                if (customTag == null)
                    return res;
                else
                    return new ItemStack(res.writeToNBT(customTag));

            ItemStringReader parser = (new ItemStringReader(new StringReader(itemString), false)).consume();
            res = new ItemStackArgument(parser.getItem(), parser.getTag());
            itemCache.put(itemString, res);
            if (itemCache.size()>64000)
                itemCache.clear();
            if (customTag == null)
                return res;
            else
                return new ItemStackArgument(res.getItem(), customTag);
        }
        catch (CommandSyntaxException e)
        {
            throw new ThrowStatement(itemString, Throwables.UNKNOWN_ITEM);
        }
    }
    */
    public static int validateSlot(int slot, IInventory inv)
    {
        int invSize = inv.getSizeInventory();
        if (slot < 0)
            slot = invSize + slot;
        if (slot < 0 || slot >= invSize)
            return inv.getSizeInventory(); // outside of inventory
        return slot;
    }

    private static Value decodeTag(NBTBase t)
    {
        if (t instanceof NBTTagCompound)
            return new NBTSerializableValue(() -> t);
        if (t instanceof NBTPrimitive)
            return new NumericValue(((NBTPrimitive) t).getDouble());
        // more can be done here
        return new StringValue(t.toString());
    }

    public Value toValue()
    {
        return decodeTagDeep(this.getTag());
    }

    public static Value fromValue(Value v)
    {
        if (v instanceof NBTSerializableValue)
            return v;
        if (v instanceof NullValue)
            return Value.NULL;
        return NBTSerializableValue.parseString(v.getString(), true);
    }


    private static Value decodeTagDeep(NBTBase t)
    {
        if (t instanceof NBTTagCompound)
        {
            Map<Value, Value> pairs = new HashMap<>();
            NBTTagCompound ctag = (NBTTagCompound)t;
            for (String key: ctag.getKeySet())
            {
                pairs.put(new StringValue(key), decodeTagDeep(ctag.getTag(key)));
            }
            return MapValue.wrap(pairs);
        }
        if (t instanceof NBTTagList)
        {
            List<Value> elems = new ArrayList<>();
            NBTTagList ltag = (NBTTagList)t;
            for (NBTBase elem: ltag.getTagList()) {
                elems.add(decodeTagDeep(elem));
            }
            return ListValue.wrap(elems);
        }
        if (t instanceof NBTPrimitive)
            return new NumericValue(((NBTPrimitive) t).getDouble());
        // more can be done here
        return new StringValue(t.toString());
    }

    public NBTBase getTag()
    {
        if (nbtTag == null)
            nbtTag = nbtSupplier.get();
        return nbtTag;
    }

    @Override
    public boolean equals(final Object o)
    {
        if (o instanceof NBTSerializableValue)
            return getTag().equals(((NBTSerializableValue) o).getTag());
        return super.equals(o);
    }

    @Override
    public String getString()
    {
        if (nbtString == null)
            nbtString = getTag().toString();
        return nbtString;
    }

    @Override
    public boolean getBoolean()
    {
        NBTBase tag = getTag();
        if (tag instanceof NBTTagCompound)
            return !((NBTTagCompound) tag).isEmpty();
        if (tag instanceof NBTTagList)
            return ((NBTTagList) tag).isEmpty();
        if (tag instanceof NBTPrimitive)
            return ((NBTPrimitive) tag).getDouble()!=0.0;
        if (tag instanceof NBTTagString)
            return tag.toString().isEmpty();
        return true;
    }

    public NBTTagCompound getCompoundTag()
    {
        try
        {
            ensureOwnership();
            return (NBTTagCompound) getTag();
        }
        catch (ClassCastException e)
        {
            throw new InternalExpressionException(getString()+" is not a valid compound tag");
        }
    }

    @Override
    public boolean put(Value where, Value value)
    {
        return put(where, value, new StringValue("replace"));
    }

    /* todo modify
    private boolean modify_insert(int index, NbtPathArgumentType.NbtPath nbtPath, NBTBase newElement)
    {
        return modify_insert(index, nbtPath, newElement, this.getTag());
    }

    private boolean modify_insert(int index, NbtPathArgumentType.NbtPath nbtPath, NBTBase newElement, NBTBase currentTag)
    {
        Collection<NBTBase> targets;
        try
        {
            targets = nbtPath.get (currentTag, ListTag::new);
        }
        catch (CommandSyntaxException e)
        {
            return false;
        }

        boolean modified = false;
        for (NBTBase target : targets)
        {
            if (!(target instanceof NBTTagList))
            {
                continue;
            }
            NBTTagIntArray
            try
            {
                NBTTagList<?> targetList = (NBTTagList) target;
                if (!targetList.addTag(index < 0 ? targetList.size() + index + 1 : index, newElement.copy()))
                    return false;
                modified = true;
            }
            catch (IndexOutOfBoundsException ignored)
            {
            }
        }
        return modified;
    }


    private boolean modify_merge(NbtPathArgumentType.NbtPath nbtPath, NBTBase replacement) //nbtPathArgumentType$NbtPath_1, list_1)
    {
        if (!(replacement instanceof NBTTagCompound))
        {
            return false;
        }
        NBTBase ownTag = getTag();
        try
        {
            for (NBTBase target : nbtPath.getOrInit(ownTag, NBTTagCompound::new))
            {
                if (!(target instanceof NBTTagCompound))
                {
                    continue;
                }
                ((NBTTagCompound) target).copyFrom((NBTTagCompound) replacement);
            }
        }
        catch (CommandSyntaxException ignored)
        {
            return false;
        }
        return true;
    }

    private boolean modify_replace(NbtPathArgumentType.NbtPath nbtPath, NBTBase replacement) //nbtPathArgumentType$NbtPath_1, list_1)
    {
        NBTBase tag = getTag();
        String pathText = nbtPath.toString();
        if (pathText.endsWith("]")) // workaround for array replacement or item in the array replacement
        {
            if (nbtPath.remove(tag)==0)
                return false;
            Pattern pattern = Pattern.compile("\\[[^\\[]*]$");
            Matcher matcher = pattern.matcher(pathText);
            if (!matcher.find()) // malformed path
            {
                return false;
            }
            String arrAccess = matcher.group();
            int pos;
            if (arrAccess.length()==2) // we just removed entire array
                pos = 0;
            else
            {
                try
                {
                    pos = Integer.parseInt(arrAccess.substring(1, arrAccess.length() - 1));
                }
                catch (NumberFormatException e)
                {
                    return false;
                }
            }
            NbtPathArgumentType.NbtPath newPath = cachePath(pathText.substring(0, pathText.length()-arrAccess.length()));
            return modify_insert(pos,newPath,replacement, tag);
        }
        try
        {
            nbtPath.put(tag, () -> replacement);
        }
        catch (CommandSyntaxException e)
        {
            return false;
        }
        return true;
    }
    */
    @Override
    public Value get(Value value)
    {
        /*
        NBT path = cachePath(value.getString());
        try
        {
            List<NBTBase> tags = path.get(getTag());
            if (tags.size()==0)
                return Value.NULL;
            if (tags.size()==1)
                return NBTSerializableValue.decodeTag(tags.get(0));
            return ListValue.wrap(tags.stream().map(NBTSerializableValue::decodeTag).collect(Collectors.toList()));
        }
        catch (Exception ignored) { }
        return Value.NULL;
        */
        throw new InternalExpressionException("Unimplemented, cannot get NBT values...");
    }

    @Override
    public boolean has(Value where){
        return ((NBTTagCompound)this.nbtTag).hasKey(where.getString());
    }

    private void ensureOwnership()
    {
        if (!owned)
        {
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
    public boolean delete(Value where)
    {
        /*
        NbtPathArgumentType.NbtPath path = cachePath(where.getString());
        ensureOwnership();
        int removed = path.remove(getTag());
        if (removed > 0)
        {
            dirty();
            return true;
        }
        return false;
         */
        throw new InternalExpressionException("Unimplemented, cannot delete NBT values...");
    }

    public static class InventoryLocator {
        public Object owner;
        public BlockPos position;
        public IInventory inventory;
        public int offset;
        public boolean isEnder;
        InventoryLocator(Object owner, BlockPos pos, IInventory i, int o)
        {
            this(owner, pos, i, o, false);
        }

        InventoryLocator(Object owner, BlockPos pos, IInventory i, int o, boolean isEnder)
        {
            this.owner = owner;
            position = pos;
            inventory = i;
            offset = o;
            this.isEnder = isEnder;
        }
    }
    /*todo cache bs
    private static Map<String, NbtPathArgumentType.NbtPath> pathCache = new HashMap<>();
    private static NbtPathArgumentType.NbtPath cachePath(String arg)
    {
        NbtPathArgumentType.NbtPath res = pathCache.get(arg);
        if (res != null)
            return res;
        try
        {
            res = NbtPathArgumentType.nbtPath().parse(new StringReader(arg));
        }
        catch (CommandSyntaxException exc)
        {
            throw new InternalExpressionException("Incorrect nbt path: "+arg);
        }
        if (pathCache.size() > 1024)
            pathCache.clear();
        pathCache.put(arg, res);
        return res;
    }
    */
    @Override
    public String getTypeString()
    {
        return "nbt";
    }


    @Override
    public NBTBase toTag(boolean force)
    {
        if (!force) throw new NBTSerializableValue.IncompatibleTypeException(this);
        ensureOwnership();
        return getTag();
    }

    public static class IncompatibleTypeException extends RuntimeException
    {
        private IncompatibleTypeException() {}
        public Value val;
        public IncompatibleTypeException(Value val)
        {
            this.val = val;
        }
    };
}
