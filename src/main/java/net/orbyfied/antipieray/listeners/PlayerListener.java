package net.orbyfied.antipieray.listeners;

import net.orbyfied.antipieray.AntiPieRay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerListener implements Listener {

    public PlayerListener(AntiPieRay plugin) {
        this.plugin = plugin;
    }

    // the plugin
    final AntiPieRay plugin;

    @EventHandler
    void onJoin(PlayerJoinEvent event) {

    }

}
