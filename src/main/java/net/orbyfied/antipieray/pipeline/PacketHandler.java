package net.orbyfied.antipieray.pipeline;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.orbyfied.antipieray.AntiPieRay;
import net.orbyfied.antipieray.AntiPieRayConfig;
import net.orbyfied.antipieray.math.FastRayCast;

public class PacketHandler extends ChannelDuplexHandler {

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
        // check packet type
        if (!(msg instanceof ClientboundBlockEntityDataPacket packet)) return;

        // perform check
        if (!allowPacket(packet)) {
            promise.cancel(true);
        }
    }

    /**
     * Checks if the given packet should be sent.
     *
     * @param packet The packet.
     * @return True/false. If false it will be dropped.
     */
    public boolean allowPacket(ClientboundBlockEntityDataPacket packet) {
        // check if it should be checked
        if (!config.checkedBlockEntities.contains(packet.getType())) {
            return true;
        }

        // ray cast
        if (!FastRayCast.blockRayCastNonSolid(packet.getPos().getCenter(), player.position(),
                // todo: cache block accesses
                //  because this will make a lot of instances
                //  and is probably slow as fuck lol
                FastRayCast.blockAccessOf(player.getLevel())
        )) {
            return false;
        }

        // return false
        return true;
    }

}
