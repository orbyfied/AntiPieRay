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
import java.util.concurrent.atomic.AtomicBoolean;

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
                    "d");
    private static final UnsafeField FIELD_ChunkDataPacket_BEI_packedXZ =
            UnsafeReflector.get().getField(ClientboundLevelChunkPacketData.class.getName() + "$a",
                    "a");
    private static final UnsafeField FIELD_ChunkDataPacket_BEI_y =
            UnsafeReflector.get().getField(ClientboundLevelChunkPacketData.class.getName() + "$a",
                    "b");
    private static final UnsafeField FIELD_ChunkDataPacket_BEI_type =
            UnsafeReflector.get().getField(ClientboundLevelChunkPacketData.class.getName() + "$a",
                    "c");

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

    // the world access
    FastRayCast.BlockAccess blockAccess;
    // if any entities have been
    // hidden for this player
    // TODO: use position instead of just checking
    //  a boolean
    AtomicBoolean hidden = new AtomicBoolean(false);

    /**
     * Get if any tile entities have been hidden
     * in range of the given chunk by position.
     *
     * @param cx The chunk X.
     * @param cz The chunk Z.
     * @return If any block entities have been hidden in range of this chunk.
     */
    public boolean anyHidden(int cx, int cz) {
        return hidden.get();
    }

    /**
     * Mark that a tile entity has been hidden
     * in the given chunk.
     *
     * @param cx The chunk X.
     * @param cz The chunk Y.
     * @param value The value to set.
     */
    public void markHidden(int cx, int cz, boolean value) {
        hidden.set(true);
    }

    /**
     * Checks if the given packet should be sent.
     *
     * @param objectPacket The packet.
     * @return True/false. If false it will be dropped.
     */
    public boolean allowPacket(Object objectPacket) {
        blockAccess = FastRayCast.blockAccessOf(player.getLevel());

        // check packet type
        // and get data
        if (objectPacket instanceof ClientboundBlockEntityDataPacket packet) {
            // check if it should be checked
            if (!config.checkedBlockEntities.contains(packet.getType())) {
                return true;
            }

            return checkBlockOrMark(packet.getPos().getCenter());
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
                if (!checkBlockOrMark(new BlockPos(x, y, z).getCenter())) {
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
                if (!checkBlockOrMark(sectionPos.relativeToBlockPos(positions[i]).getCenter())) {
                    states[i] = Blocks.AIR.defaultBlockState();
                }
            }

            return true;
        } else if (objectPacket instanceof ClientboundBlockUpdatePacket packet) {
            // check if it should be checked
            if (!config.checkedBlockTypes.contains(packet.getBlockState().getBlock())) {
                return true;
            }

            return checkBlockOrMark(packet.getPos().getCenter());
        } else {
            return true;
        }
    }

    // check given block with checkBlock(Vec3)
    // and mark as hidden if false
    public boolean checkBlockOrMark(Vec3 bPos) {
        boolean v = checkBlock(bPos);
        if (!v) {
            int cx = (int)(((long)bPos.x) >> 4);
            int cz = (int)(((long)bPos.z) >> 4);

            markHidden(cx, cz, true);
        }

        return v;
    }

    /**
     * Checks if a given block position should
     * be rendered to the player.
     *
     * @param bPos The center of the block.
     * @return True/false.
     */
    public boolean checkBlock(Vec3 bPos) {
        Vec3 pPos = player.position();

        // simple distance check
        if (pPos.distanceToSqr(bPos) < config.alwaysViewDistSqr) {
            return true;
        }

        // ray cast
        if (!FastRayCast.blockRayCastNonSolid(bPos, pPos.add(0, 0.8, 0),
                blockAccess)) {
            return false;
        }

        // return permitted
        return true;
    }

    /**
     * Handles a set position packet.
     *
     * Required for sending the tile entities
     * once they become visible.
     *
     * @param packet The packets.
     */
    public void handleSetPosition(ClientboundPlayerPositionPacket packet) {
        double x = packet.getX();
        double y = packet.getY();
        double z = packet.getZ();
        if (anyHidden((int)(((long)x) >> 4), (int)(((long)z) >> 4))) {
            // TODO: send visible tile entities
        }
    }

}
