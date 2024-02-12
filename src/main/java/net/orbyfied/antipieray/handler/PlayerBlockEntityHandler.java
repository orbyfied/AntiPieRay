package net.orbyfied.antipieray.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.orbyfied.antipieray.AntiPieRay;
import net.orbyfied.antipieray.AntiPieRayConfig;
import net.orbyfied.antipieray.math.FastRayCast;
import net.orbyfied.antipieray.reflect.UnsafeField;
import net.orbyfied.antipieray.reflect.UnsafeReflector;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@SuppressWarnings({ "rawtypes" })
public class PlayerBlockEntityHandler extends ChannelDuplexHandler implements Listener {

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

    private static final Object OBJECT = new Object();

    // the amount of chunks of distance
    // to account for when doing ranged
    // checks and queries
    private static final int TILE_ENTITY_DISTANCE = 2;

    private static final double MOVEMENT_UPDATE_THRESHOLD_SQR = 4.0;

    //////////////////////////////////////////

    public PlayerBlockEntityHandler(Injector injector,
                                    ServerPlayer player) {
        this.injector = injector;
        this.player   = player;

        this.plugin = injector.plugin;
        this.config = plugin.config();

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // the injector
    protected final Injector injector;

    // the player object
    protected final ServerPlayer player;

    // the config
    protected final AntiPieRay plugin;
    protected final AntiPieRayConfig config;

    Vec3 lastUpdatedPosition;

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        HandlerList.unregisterAll(this);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ClientboundPlayerPositionPacket packet) {
            handleSetPosition(packet);
        }

        if (allowPacket(msg)) {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) throws Exception {
        if (msg instanceof ServerboundMovePlayerPacket packet) {
            handleMove(packet);
        }
    }

    public Long2ObjectOpenHashMap<ChunkData> getChunkDataMap() {
        return chunkDataMap;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    // local chunk data for a chunk
    // stores data like the amount of
    // hidden entities, used by
    // the packet handler
    public static class ChunkData {
        public long pos;
        public IntOpenHashSet hiddenEntities = new IntOpenHashSet();

        // mark an entity as hidden
        // by adding it to the set
        public void addHidden(int packed) {
            hiddenEntities.add(packed);
        }

        // mark an entity as shown
        // by removing it from the set
        public void removeHidden(int packed) {
            hiddenEntities.remove(packed);
        }
    }

    // packs chunk x and z into a long
    static long packChunkPos(int cx, int cz) {
        return (long)cx | (long)cz >> 32;
    }

    // packs position x, y and z into a long
    // packed format: (1char1byte) x-yy-z
    static int packBlockPos(int x, int y, int z) {
        return x | y >> 8 | z >> (8 + 16);
    }

    // unpacks position x, y and z from a long
    // to a vec3i packed format: (1char1byte) x-yy-z
    static Vec3i unpackBlockPos(int packed) {
        int x = packed & 0xFF;   packed <<= 8;
        int y = packed & 0xFFFF; packed <<= 16;
        int z = packed & 0xFF;
        return new Vec3i(x, y, z);
    }

    // unpacks position x, y and z from a long
    // and calculates that over into a block pos
    // using the provided chunkX*16 and chunkZ*16 parameters
    // to a vec3i packed format: (1char1byte) x-yy-z
    static BlockPos unpackAndCalcBlockPos(long cuX16, long cuZ16, int packed) {
        long x = (long)(packed & 0xFF)  * cuX16; packed <<= 8;
        long y =        packed & 0xFFFF        ; packed <<= 16;
        long z = (long)(packed & 0xFF)  * cuZ16;
        return new BlockPos(x, y, z);
    }

    // the current world access
    FastRayCast.BlockAccess blockAccess;
    ServerLevel currentBlockAccessWorld;

    // the chunks (by packed position) that
    // have hidden entities and data
    Long2ObjectOpenHashMap<ChunkData> chunkDataMap = new Long2ObjectOpenHashMap();

    /**
     * Update a range of chunks around the given
     * chunk including itself for the player.
     *
     * This does things like re-check hidden
     * block entities and show them.
     *
     * @param cx The chunk X.
     * @param cz The chunk Z.
     */
    public void updateChunkView(int cx, int cz) {
        // the blocks to be shown
        List<BlockPos> toShow = new ArrayList<>();

        // for every chunk in range
        int sx = cx - TILE_ENTITY_DISTANCE;
        int sz = cz - TILE_ENTITY_DISTANCE;
        int ex = cx + TILE_ENTITY_DISTANCE;
        int ez = cz + TILE_ENTITY_DISTANCE;
        for (int cuX = sx; cuX <= ex; cuX++) {
            long cuX16 = cuX * 16L;
            for (int cuZ = sz; cuZ <= ez; cuZ++) {
                long cuZ16 = cuZ * 16L;

                System.out.println("updateChunkView Chunk: cuX(" + cuX + ") cuZ(" + cuZ + ")");

                // pack position
                long packedChunkPos = packChunkPos(cuX, cuZ);

                // get chunk data
                ChunkData chunkData = chunkDataMap.get(packedChunkPos);
                if (chunkData == null)
                    continue;

                findDisplayedTileEntitiesInChunk(chunkData, toShow);
            }
        }

        showTileEntities(toShow);
    }

    /**
     * Find all tile entities to show in the given chunk and add them
     * to the buffer
     */
    public void findDisplayedTileEntitiesInChunk(ChunkData chunkData, List<BlockPos> toShow) {
        long packedChunkPos = chunkData.pos;

        int cz = (int) (packedChunkPos);
        int cx = (int) (packedChunkPos << 32);

        int cuX16 = cx * 16;
        int cuZ16 = cz * 16;

        // iterate over hidden entities
        IntIterator iterator = chunkData.hiddenEntities.iterator();
        for (int packedBlockPos = iterator.nextInt(); iterator.hasNext(); packedBlockPos = iterator.nextInt()) {
            // unpack position and calculate absolute block position
            BlockPos bPos = unpackAndCalcBlockPos(cuX16, cuZ16, packedBlockPos);
            Vec3 cbPos = bPos.getCenter();

            // re-check block
            if (checkBlock(cbPos)) {
                toShow.add(bPos);
                iterator.remove();
            }
        }
    }

    public void showTileEntities(List<BlockPos> toShow) {
        // send blocks to be shown to player
        final int l = toShow.size();
        if (l != 0) {
            final ServerLevel level = player.getLevel();
            final ServerGamePacketListenerImpl connection = player.connection;
            for (int i = 0; i < l; i++) {
                BlockPos bPos = toShow.get(i);

                // get block entity
                BlockEntity entity = level.getBlockEntity(bPos);
                if (entity == null)
                    continue;

                // send tile entity packet
                ClientboundBlockEntityDataPacket packet =
                        ClientboundBlockEntityDataPacket.create(entity);
                connection.send(packet);
            }
        }
    }

    /**
     * Get the local chunk data for the
     * given coordinates, can be null.
     *
     * @param cx The chunk X.
     * @param cz The chunk Z.
     * @return The data or null if absent.
     */
    public ChunkData getChunkData(int cx, int cz) {
        return chunkDataMap.get(packChunkPos(cx, cz));
    }

    /**
     * Get or create the local chunk data for the
     * given coordinates, can be null.
     *
     * @param cx The chunk X.
     * @param cz The chunk Z.
     * @return The data or null if absent.
     */
    public ChunkData getOrCreateChunkData(int cx, int cz) {
        long packed = packChunkPos(cx, cz);
        ChunkData data = chunkDataMap.get(packed);
        if (data == null) {
            data = new ChunkData();
            data.pos = packed;
            chunkDataMap.put(packed, data);
        }

        return data;
    }

    /**
     * Mark that a tile entity has been hidden
     * in the given chunk.
     *
     * @param cx The chunk X.
     * @param cz The chunk Y.
     * @param value The value to set.
     */
    public void markHidden(int cx, int cz, int tx, int ty, int tz, boolean value) {
        if (value) {
            getOrCreateChunkData(cx, cz).addHidden(packBlockPos(tx, ty, tz));
        } else {
            ChunkData data = getChunkData(cx, cz);
            if (data != null) {
                data.removeHidden(packBlockPos(tx, ty, tz));
            }
        }
    }

    /**
     * Checks if the given packet should be sent.
     *
     * @param objectPacket The packet.
     * @return True/false. If false it will be dropped.
     */
    public boolean allowPacket(Object objectPacket) {
        ServerLevel level = player.getLevel();
        if (currentBlockAccessWorld != level) {
            blockAccess = FastRayCast.blockAccessOf(player.getLevel());
            currentBlockAccessWorld = level;
        }

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
            int tx = (int)(bPos.x) % 16;
            int tz = (int)(bPos.z) % 16;

            markHidden(cx, cz, tx, (int)bPos.y, tz, true);
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
        System.out.println("pPos: " + pPos + ", bPos: " + bPos);
        if (pPos.distanceToSqr(bPos) < config.alwaysViewDistSqr) {
            return true;
        }

        // ray cast
        if (!FastRayCast.blockRayCastNonSolid(bPos, pPos.add(0, 0.8, 0), blockAccess)) {
            return false;
        }

        // return permitted
        return true;
    }

    private boolean checkMovementUpdate(double x, double y, double z) {
        Vec3 newPos = new Vec3(x, y, z);
        if (lastUpdatedPosition != null) {
            if (lastUpdatedPosition.distanceToSqr(newPos) < MOVEMENT_UPDATE_THRESHOLD_SQR) {
                return false;
            }
        }

        lastUpdatedPosition = newPos;
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
        if (!checkMovementUpdate(x, y, z)) return;

        updateChunkView((int)((long)x >> 4), (int)((long)z >> 4));
    }

    /**
     * Handles a move packet.
     *
     * Required for sending the tile entities
     * once they become visible.
     *
     * @param packet The packets.
     */
    public void handleMove(ServerboundMovePlayerPacket packet) {
        double x = packet.getX(player.getX());
        double y = packet.getY(player.getY());
        double z = packet.getZ(player.getZ());
        if (!checkMovementUpdate(x, y, z)) return;

        updateChunkView((int)((long)x >> 4), (int)((long)z >> 4));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onBlockBreak(BlockBreakEvent event) {
        org.bukkit.block.Block block = event.getBlock();

        BlockPos blockPos = new BlockPos(block.getX(), block.getY(), block.getZ());
        int cx = blockPos.getX() >> 4;
        int cz = blockPos.getZ() >> 4;

        // update tile entities in the affected chunk
        ChunkData chunkData = getChunkData(cx, cz);
        if (chunkData != null && !chunkData.hiddenEntities.isEmpty()) {
            List<BlockPos> toShow = new ArrayList<>();
            findDisplayedTileEntitiesInChunk(chunkData, toShow);
            showTileEntities(toShow);
        }
    }

}
