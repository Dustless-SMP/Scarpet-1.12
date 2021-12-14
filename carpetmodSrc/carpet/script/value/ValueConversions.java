package carpet.script.value;

import adsen.scarpet.interpreter.parser.exception.InternalExpressionException;
import adsen.scarpet.interpreter.parser.value.ListValue;
import adsen.scarpet.interpreter.parser.value.NumericValue;
import adsen.scarpet.interpreter.parser.value.Value;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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

    public static BlockPos blockPos(Value v){
        return new BlockPos(vec3d(v));
    }
}
