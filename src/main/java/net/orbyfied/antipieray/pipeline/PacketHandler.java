package net.orbyfied.antipieray.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.orbyfied.antipieray.AntiPieRay;
import net.orbyfied.antipieray.AntiPieRayConfig;
import net.orbyfied.antipieray.math.FastRayCast;
import net.orbyfied.antipieray.reflect.UnsafeField;
import net.orbyfied.antipieray.reflect.UnsafeReflector;

import java.util.Iterator;
import java.util.List;

@SuppressWarnings({ "rawtypes" })
public class PacketHandler extends ChannelDuplexHandler {

    private static final UnsafeField FIELD_BlockUpdatePacket_states =
            UnsafeReflector.get().getField(ClientboundSectionBlocksUpdatePacket.class,
                    "d");
    private static final UnsafeField FIELD_BlockUpdatePacket_positions =
            UnsafeReflector.get().getField(ClientboundSectionBlocksUpdatePacket.class,
                    "c");
    private static final UnsafeField FIELD_BlockUpdatePacket_sectionPos =
            UnsafeReflector.get().getField(ClientboundSectionBlocksUpdatePacket.class,
                    "b");

    private static final UnsafeField FIELD_ChunkDataPacket_blockEntitiesData =
            UnsafeReflector.get().getField(ClientboundLevelChunkPacketData.class,
                    "blockEntitiesData");
    private static final UnsafeField FIELD_ChunkDataPacket_BEI_packedXZ =
            UnsafeReflector.get().getField(ClientboundLevelChunkPacketData.class.getName() + "$a",
                    "packedXZ");
    private static final UnsafeField FIELD_ChunkDataPacket_BEI_y =
            UnsafeReflector.get().getField(ClientboundLevelChunkPacketData.class.getName() + "$a",
                    "y");
    private static final UnsafeField FIELD_ChunkDataPacket_BEI_type =
            UnsafeReflector.get().getField(ClientboundLevelChunkPacketData.class.getName() + "$a",
                    "type");

    //////////////////////////////////////////

    public PacketHandler(Injector injector,
                         ServerPlayer player) {
        this.injector = injector;
        this.player   = player;

        this.plugin = injector.plugin;
        this.config = plugin.config();
    }

    // the injector
    private final Injector injector;

    // the player object
    private final ServerPlayer player;

    // the config
    private final AntiPieRay plugin;
    private final AntiPieRayConfig config;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (allowPacket(msg)) {
            super.write(ctx, msg, promise);
        }
    }

    /**
     * Checks if the given packet should be sent.
     *
     * @param objectPacket The packet.
     * @return True/false. If false it will be dropped.
     */
    public boolean allowPacket(Object objectPacket) {
        // check packet type
        // and get data
        if (objectPacket instanceof ClientboundBlockEntityDataPacket packet) {
            // check if it should be checked
            if (!config.checkedBlockEntities.contains(packet.getType())) {
                return true;
            }

            return checkBlock(packet.getPos().getCenter());
        } else if (objectPacket instanceof ClientboundLevelChunkWithLightPacket withLightPacket) {
            // get packet
            ClientboundLevelChunkPacketData packet = withLightPacket.getChunkData();

            // get chunk position
            int cx = withLightPacket.getX();
            int cz = withLightPacket.getZ();

            // get list of block entities
            List blockEntities = (List) FIELD_ChunkDataPacket_blockEntitiesData.get(packet);

            // for each state
            Iterator iterator = blockEntities.iterator();
            while (iterator.hasNext()) {
                // advance item
                Object data = iterator.next();

                // get data from item
                int packedXZ = (int) FIELD_ChunkDataPacket_BEI_packedXZ.get(data);
                int y = (int) FIELD_ChunkDataPacket_BEI_y.get(data);
                BlockEntityType<?> type = (BlockEntityType<?>) FIELD_ChunkDataPacket_BEI_type.get(data);

                // check type
                if (!config.checkedBlockEntities.contains(type)) {
                    continue;
                }

                // get position
                long x = cx + SectionPos.sectionRelative(packedXZ >> 4);
                long z = cz + SectionPos.sectionRelative(packedXZ);

                // check block
                if (!checkBlock(new BlockPos(x, y, z).getCenter())) {
                    iterator.remove();
                }
            }

            return true;
        } else if (objectPacket instanceof ClientboundSectionBlocksUpdatePacket packet) {
            // get list of block states
            final BlockState[] states = (BlockState[]) FIELD_BlockUpdatePacket_states.get(packet);
            final short[] positions = (short[]) FIELD_BlockUpdatePacket_positions.get(packet);
            final SectionPos sectionPos = (SectionPos) FIELD_BlockUpdatePacket_sectionPos.get(packet);

            // for each state
            final int l = states.length;
            for (int i = 0; i < l; i++) {
                final BlockState state = states[i];
                final Block block = state.getBlock();

                // check block
                if (!config.checkedBlockTypes.contains(block))
                    continue;

                // remove state of blocked
                if (!checkBlock(sectionPos.relativeToBlockPos(positions[i]).getCenter())) {
                    states[i] = Blocks.AIR.defaultBlockState();
                }
            }

            return true;
        } else if (objectPacket instanceof ClientboundBlockUpdatePacket packet) {
            // check if it should be checked
            if (!config.checkedBlockTypes.contains(packet.getBlockState().getBlock())) {
                return true;
            }

            return checkBlock(packet.getPos().getCenter());
        } else {
            return true;
        }
    }

    public boolean checkBlock(Vec3 bPos) {
        Vec3 pPos = player.position();

        // simple distance check
        if (pPos.distanceToSqr(bPos) < config.alwaysViewDistSqr) {
            return true;
        }

        // ray cast
        if (!FastRayCast.blockRayCastNonSolid(bPos, pPos,
                // todo: cache block accesses
                //  because this will make a lot of instances
                //  and is probably slow as fuck lol
                FastRayCast.blockAccessOf(player.getLevel())
        )) {
            return false;
        }

        // return permitted
        return true;
    }

}
