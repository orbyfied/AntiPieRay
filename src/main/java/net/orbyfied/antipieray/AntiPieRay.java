package net.orbyfied.antipieray;

import net.orbyfied.antipieray.command.AntiPieRayCommand;
import net.orbyfied.antipieray.listeners.PlayerListener;
import net.orbyfied.antipieray.pipeline.Injector;
import net.orbyfied.antipieray.util.TextUtil;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * AntiPieRay main class.
 */
public final class AntiPieRay extends JavaPlugin {

    /* prefix */
    public static final String PREFIX = TextUtil.translate(
            "&b&lAntiPieRay &8»&r "
    );

    // the config
    protected final AntiPieRayConfig config;

    // the injection manager
    public final Injector injector = new Injector(this);

    {
        // load configuration
        config = new AntiPieRayConfig(this);
    }

    // get the config
    public AntiPieRayConfig config() {
        return config;
    }

    ///////////////////////////////

    @Override
    public void onEnable() {
        // enable commands
        {
            AntiPieRayCommand exec = new AntiPieRayCommand(this);
            getCommand("antipieray").setExecutor(exec);
            getCommand("antipieray").setTabCompleter(exec);
        }

        // register listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
    }

    @Override
    public void onDisable() {

    }

}
