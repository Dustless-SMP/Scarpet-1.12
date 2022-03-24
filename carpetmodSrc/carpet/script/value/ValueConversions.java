package carpet.script.value;

import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.ListValue;
import adsen.scarpet.interpreter.parser.value.NumericValue;
import adsen.scarpet.interpreter.parser.value.StringValue;
import adsen.scarpet.interpreter.parser.value.Value;
import carpet.CarpetServer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

public class ValueConversions {
    public static Value of(Vec3d vec) {
        return ListValue.of(new NumericValue(vec.x), new NumericValue(vec.y), new NumericValue(vec.z));
    }

    public static Value of(BlockPos blockPos) {
        return ListValue.of(new NumericValue(blockPos.getX()), new NumericValue(blockPos.getY()), new NumericValue(blockPos.getZ()));
    }

    public static Value of(double x, double y, double z) {
        return ListValue.of(new NumericValue(x), new NumericValue(y), new NumericValue(z));
    }

    public static Value of(ItemStack item) {
        return ListValue.of(StringValue.of(item.getItem().toString()), new NumericValue(item.getCount()), NBTSerializableValue.fromStack(item));
    }

    public static Value of(NBTBase nbt) {
        return new NBTSerializableValue(nbt);
    }

    public static Vec3d vec3d(Value v) {
        return vec3d((ListValue) v);
    }

    public static Vec3d vec3d(ListValue lv) {
        if (lv.length() != 3 || lv.getItems().stream().allMatch(v -> v instanceof NumericValue))
            throw new InternalExpressionException("A block-type argument must have 3 numeric coordinates");
        double x = lv.getItems().get(0).readNumber();
        double y = lv.getItems().get(1).readNumber();
        double z = lv.getItems().get(2).readNumber();
        return new Vec3d(x, y, z);
    }

    public static BlockPos blockPos(Value v) {
        return new BlockPos(vec3d(v));
    }

    public static Class<? extends Entity> entityFromString(String entity) {
        NBTTagCompound nbtTagCompound = new NBTTagCompound();
        nbtTagCompound.setString("id", entity);
        Entity e = AnvilChunkLoader.readWorldEntityPos(nbtTagCompound, CarpetServer.minecraft_server.getWorld(0), 0, 0, 0, false);
        if (e == null)
            throw new InternalExpressionException("Unable to parse entity");

        return e.getClass();
    }

    public static NBTBase nbtFromString(String nbt) throws NBTException {
        return JsonToNBT.getTagFromJson(nbt);
    }
}
