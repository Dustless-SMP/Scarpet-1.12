package carpet.script.utils;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.List;
import java.util.stream.Collectors;

public class WorldTools
{

    public static boolean canHasChunk(WorldServer world, ChunkPos chpos) {
        return world.getChunk(chpos.x, chpos.z) != null;
    }


    public static void forceChunkUpdate(BlockPos pos, WorldServer world)
    {
        Chunk worldChunk = world.getChunkProvider().getLoadedChunk(pos.getX()>>4, pos.getZ()>>4);
        if (worldChunk != null)
        {
            int vd = world.getMinecraftServer().getPlayerList().getViewDistance() * 16;
            int vvd = vd * vd;
            List<EntityPlayer> nearbyPlayers = world.getPlayers().stream().filter(p -> pos.distanceSq(p.posX, pos.getY(), p.posZ) < vvd).collect(Collectors.toList());
            //todo send chunk update packet
            //if (!nearbyPlayers.isEmpty())
            //{
            //    ChunkDataS2CPacket packet = new ChunkDataS2CPacket(worldChunk, 65535);
            //    ChunkPos chpos = new ChunkPos(pos);
            //    nearbyPlayers.forEach(p -> p.networkHandler.sendPacket(packet));
            //}
        }
    }
}
