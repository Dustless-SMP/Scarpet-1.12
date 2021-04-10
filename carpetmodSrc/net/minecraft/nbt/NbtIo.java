package net.minecraft.nbt;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class NbtIo {
    public static void writeCompressed(NBTTagCompound tag, File file) throws IOException {
        OutputStream outputStream = new FileOutputStream(file);
        Throwable var3 = null;

        try {
            writeCompressed(tag, outputStream);
        } catch (Throwable var12) {
            var3 = var12;
            throw var12;
        } finally {
            if (outputStream != null) {
                if (var3 != null) {
                    try {
                        outputStream.close();
                    } catch (Throwable var11) {
                        var3.addSuppressed(var11);
                    }
                } else {
                    outputStream.close();
                }
            }

        }

    }

    public static void writeCompressed(NBTTagCompound tag, OutputStream stream) throws IOException {
        DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(stream)));
        Throwable var3 = null;

        try {
            write((NBTTagCompound)tag, (DataOutput)dataOutputStream);
        } catch (Throwable var12) {
            var3 = var12;
            throw var12;
        } finally {
            if (dataOutputStream != null) {
                if (var3 != null) {
                    try {
                        dataOutputStream.close();
                    } catch (Throwable var11) {
                        var3.addSuppressed(var11);
                    }
                } else {
                    dataOutputStream.close();
                }
            }

        }

    }

    public static void write(NBTTagCompound tag, DataOutput output) throws IOException {
        write((NBTBase)tag, (DataOutput)output);
    }

    private static void write(NBTBase tag, DataOutput output) throws IOException {
        output.writeByte(tag.getId());
        if (tag.getId() != 0) {
            output.writeUTF("");
            tag.write(output);
        }
    }

}
