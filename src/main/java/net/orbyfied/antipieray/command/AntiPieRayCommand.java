package net.orbyfied.antipieray.command;

import net.orbyfied.antipieray.AntiPieRay;
import net.orbyfied.antipieray.handler.PlayerBlockEntityHandler;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import static net.orbyfied.antipieray.AntiPieRay.PREFIX;

public class AntiPieRayCommand extends BaseCommand {

    public AntiPieRayCommand(AntiPieRay plugin) {
        super(plugin, "antipieray", "apr");
        this.plugin = plugin;

        executes(ctx -> ctx.sender().sendMessage(PREFIX + ChatColor.WHITE + "AntiPieRay by @orbyfied on GitHub"));

        then(subcommand("reload")
                .permission("antipieray.admin")
                .executes(ctx -> {
                    var sender = ctx.sender();

                    // reload configuration
                    if (plugin.config().reload()) {
                        sender.sendMessage(PREFIX + ChatColor.GREEN + "Successfully reloaded configuration");
                    } else {
                        sender.sendMessage(PREFIX + ChatColor.RED + "Failed to reload configuration");
                        sender.sendMessage(PREFIX + ChatColor.RED + "● A full stacktrace describing the error should be in console.");
                    }
                })
        );

        then(subcommand("debug")
                .permission("antipieray.admin")
                .then(subcommand("lshidden")
                      .with(Argument.onlinePlayer("player"))
                      .executes(ctx -> {
                          Player player = ctx.get("player", Player.class).get();
                          var handler = plugin.injector.getHandler(player);

                          if (handler == null) {
                              ctx.fail("No injected handler for given player");
                              return;
                          }

                          CommandSender sender = ctx.sender;
                          for (PlayerBlockEntityHandler.ChunkData chunkData : handler.getChunkDataMap().values()) {
                              // send chunk header
                              int cx = (int)(chunkData.pos & 0xFFFFFFFF00000000L);
                              int cz = (int)(chunkData.pos << 32 & 0xFFFFFFFF00000000L);
                              sender.sendMessage("Chunk(" + cx + ", " + cz + ") hidden# = " + chunkData.hiddenEntities.size());

                              // send all hidden entities
                              for (int packed : chunkData.hiddenEntities) {
                                  int x = packed & 0xFF;   packed <<= 8;
                                  int y = packed & 0xFFFF; packed <<= 16;
                                  int z = packed & 0xFF;
                                  sender.sendMessage("  hidden(x: " + x + " y: " + y + " z: " + z + ") blockType = " + player.getWorld().getBlockAt(x, y, z).getType());
                              }
                          }
                      })
              )
        );
    }

    // the plugin
    final AntiPieRay plugin;

}
