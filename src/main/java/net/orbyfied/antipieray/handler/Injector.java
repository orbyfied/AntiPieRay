package net.orbyfied.antipieray.handler;

import net.minecraft.server.level.ServerPlayer;
import net.orbyfied.antipieray.AntiPieRay;
import net.orbyfied.antipieray.util.NmsHelper;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Injector {

    // the plugin
    final AntiPieRay plugin;

    public Injector(AntiPieRay plugin) {
        this.plugin = plugin;
    }

    // the packet handlers by player
    final Map<UUID, PlayerBlockEntityHandler> handlerMap = new HashMap<>();

    /**
     * Instantiates, injects and enables the
     * handler for the given player.
     *
     * @param player The player.
     */
    public void inject(Player player) {
        // get NMS player
        ServerPlayer nmsPlayer = NmsHelper.getPlayerHandle(player);

        // create handler
        PlayerBlockEntityHandler packetHandler = new PlayerBlockEntityHandler(this, nmsPlayer);
        handlerMap.put(player.getUniqueId(), packetHandler);

        // inject handler
        nmsPlayer.connection.connection.channel.pipeline().addLast("AntiPieRay_packet_handler",
                packetHandler);
    }

    /**
     * Removes and disables the handler for
     * the given player.
     *
     * @param player The player.
     */
    public void uninject(Player player) {
        // remove handler
        handlerMap.remove(player.getUniqueId());
    }

    public PlayerBlockEntityHandler getHandler(Player player) {
        return handlerMap.get(player.getUniqueId());
    }

}
