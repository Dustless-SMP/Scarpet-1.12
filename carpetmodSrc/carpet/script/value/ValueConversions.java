package carpet.script.value;

import carpet.script.exception.InternalExpressionException;
import carpet.script.exception.ThrowStatement;
import carpet.script.exception.Throwables;
import carpet.script.utils.ColumnPos;
import carpet.utils.BlockInfo;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandParticle;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.scoreboard.IScoreCriteria;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ValueConversions
{
    public static Value of(BlockPos pos)
    {
        return ListValue.of(new NumericValue(pos.getX()), new NumericValue(pos.getY()), new NumericValue(pos.getZ()));
    }

    public static Value ofOptional(BlockPos pos)
    {
        if (pos == null) return Value.NULL;
        return ListValue.of(new NumericValue(pos.getX()), new NumericValue(pos.getY()), new NumericValue(pos.getZ()));
    }

    public static Value of(Vec3d vec)
    {
        return ListValue.of(new NumericValue(vec.x), new NumericValue(vec.y), new NumericValue(vec.z));
    }

    public static Value of(ColumnPos cpos) { return ListValue.of(new NumericValue(cpos.x), new NumericValue(cpos.z));}

    public static Value of(MapColor color) {return ListValue.of(StringValue.of(BlockInfo.getMapColourName(color)), ofRGB(color.colorValue));}

    public static Value of(ItemStack stack)
    {
        if (stack == null || stack.isEmpty())
            return Value.NULL;
        return ListValue.of(
                new StringValue(stack.getItem().getTranslationKey()),//todo test this to check for garbage and trim it
                new NumericValue(stack.getCount()),
                NBTSerializableValue.fromStack(stack)
        );
    }

    public static Value of(ScoreObjective objective)
    {
        return ListValue.of(
                StringValue.of(objective.getName()),
                StringValue.of(objective.getCriteria().getName())
                );
    }


    public static Value of(IScoreCriteria criteria)
    {
        return ListValue.of(
                StringValue.of(criteria.getName()),
                new NumericValue(criteria.isReadOnly())
        );
    }


    public static Value of(CommandParticle particle)
    {
        String repr = particle.getName();
        if (repr.startsWith("minecraft:")) return StringValue.of(repr.substring(10));
        return StringValue.of(repr);
    }

    public static Value ofRGB(int value) {return new NumericValue(value* 256L + 255 );}

    public static World dimFromValue(Value dimensionValue, MinecraftServer server)
    {
        if (dimensionValue instanceof EntityValue)
        {
            return ((EntityValue)dimensionValue).getEntity().getEntityWorld();
        }
        else if (dimensionValue instanceof BlockValue)
        {
            BlockValue bv = (BlockValue)dimensionValue;
            if (bv.getWorld() != null)
            {
                return bv.getWorld();
            }
            else
            {
                throw new InternalExpressionException("dimension argument accepts only world-localized block arguments");
            }
        }
        else
        {
            String dimString = dimensionValue.getString().toLowerCase(Locale.ROOT);
            switch (dimString)
            {
                case "nether":
                case "the_nether":
                    return server.getWorld(DimensionType.NETHER.getId());
                case "end":
                case "the_end":
                    return server.getWorld(DimensionType.THE_END.getId());
                case "overworld":
                case "over_world":
                    return server.getWorld(DimensionType.OVERWORLD.getId());
                default:
                    throw new ThrowStatement(dimString, Throwables.UNKNOWN_DIMENSION);
            }
        }
    }


    public static Value fromPath(WorldServer world,  Path path)
    {
        List<Value> nodes = new ArrayList<>();
        //for (PathNode node: path.getNodes())
        for (int i = 0, len = path.getCurrentPathLength(); i < len; i++)
        {
            PathPoint node = path.getPathPointFromIndex(i);
            nodes.add( ListValue.of(
                    new BlockValue(null, world, node.getPos()),//todo test if this actually works or if I need to add start pos
                    new StringValue(node.nodeType.name().toLowerCase(Locale.ROOT)),
                    new NumericValue(node.cost),
                    new NumericValue(node.visited)
            ));
        }
        return ListValue.wrap(nodes);
    }

    private static Value ofUUID(WorldServer entityWorld, UUID uuid)
    {
        Entity current = entityWorld.getEntityFromUuid(uuid);
        return ListValue.of(
                current == null?Value.NULL:new EntityValue(current),
                new StringValue(uuid.toString())
        );
    }

    public static Value fromProperty(IBlockState state, IProperty<?> p)
    {
        Comparable<?> object = state.getValue(p);
        if (object instanceof Boolean || object instanceof Number) return StringValue.of(object.toString());

        throw new InternalExpressionException("Unknown property type: "+p.getName());
    }


    private static final Map<Integer, ListValue> slotIdsToSlotParams = new HashMap<Integer, ListValue>() {{
        int n;
        //covers blocks, player hotbar and inventory, and all default inventories
        for(n = 0; n < 54; ++n) {
            put(n, ListValue.of(Value.NULL, NumericValue.of(n)));
        }
        for(n = 0; n < 27; ++n) {
            put(200+n, ListValue.of(StringValue.of("enderchest"), NumericValue.of(n)));
        }

        // villager
        for(n = 0; n < 8; ++n) {
            put(300+n, ListValue.of(Value.NULL, NumericValue.of(n)));
        }

        // horse, llamas, donkeys, etc.
        // two first slots are for saddle and armour
        for(n = 0; n < 15; ++n) {
            put(500+n, ListValue.of(Value.NULL, NumericValue.of(n+2)));
        }
        Value equipment = StringValue.of("equipment");
        // weapon main hand
        put(98, ListValue.of(equipment, NumericValue.of(0)));
        // offhand
        put(99, ListValue.of(equipment, NumericValue.of(5)));
        // feet, legs, chest, head
        for(n = 0; n < 4; ++n) {
            put(100+n, ListValue.of(equipment, NumericValue.of(n+1)));
        }
        //horse defaults saddle
        put(400, ListValue.of(Value.NULL, NumericValue.of(0)));
        // armor
        put(401, ListValue.of(Value.NULL, NumericValue.of(1)));
        // chest itself on the donkey is wierd - use NBT to alter that.
        //hashMap.put("horse.chest", 499);
    }};

    public static Value ofVanillaSlotResult(int itemSlot)
    {
        Value ret = slotIdsToSlotParams.get(itemSlot);
        if (ret == null) return ListValue.of(Value.NULL, NumericValue.of(itemSlot));
        return ret;
    }
}
