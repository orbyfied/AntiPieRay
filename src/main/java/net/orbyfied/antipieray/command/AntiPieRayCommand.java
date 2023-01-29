package net.orbyfied.antipieray.command;

import net.orbyfied.antipieray.AntiPieRay;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static net.orbyfied.antipieray.AntiPieRay.PREFIX;

public class AntiPieRayCommand implements TabExecutor {

    public AntiPieRayCommand(AntiPieRay plugin) {
        this.plugin = plugin;
    }

    // the plugin
    final AntiPieRay plugin;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(PREFIX + "Running " + ChatColor.AQUA + "AntiPieRay " + ChatColor.DARK_GRAY +
                    "v" + ChatColor.DARK_AQUA + plugin.getDescription().getVersion() +
                    ChatColor.WHITE + " by " + ChatColor.AQUA + "Orbyfied");
            return true;
        }

        if (!sender.hasPermission("antipieray.admin")) {
            return false;
        }

        switch (args[0]) {
            case "reload" -> {
                // reload configuration
                if (plugin.config().reload()) {
                    sender.sendMessage(PREFIX + ChatColor.GREEN + "Successfully reloaded configuration");
                } else {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Failed to reload configuration");
                    sender.sendMessage(PREFIX + ChatColor.RED + "A full error may be in console");
                }
            }

            default -> {
                sender.sendMessage(PREFIX + ChatColor.RED + "Unknown subcommand " + ChatColor.WHITE + args[0]);
                return false;
            }
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("antipieray.admin"))
            return List.of();

        return List.of(
                "reload"
        );
    }

}
