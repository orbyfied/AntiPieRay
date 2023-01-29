package net.orbyfied.antipieray;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.orbyfied.antipieray.config.Configuration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class AntiPieRayConfig {

    AntiPieRayConfig(AntiPieRay plugin) {
        this.plugin = plugin;

        // create config
        config = new Configuration(
                plugin.getDataFolder().toPath().resolve("config.yml"),
                plugin::getResource
        ).reloadOrDefaultThrowing("defaults/config.yml");
    }

    // the plugin
    final AntiPieRay plugin;

    // the file configuration instance
    public final Configuration config;

    /* values for fast access */
    public volatile Set<BlockEntityType<?>> checkedBlockEntities = new HashSet<>();
    public volatile Set<Block> checkedBlockTypes = new HashSet<>();
    public volatile double alwaysViewDist;
    public volatile double alwaysViewDistSqr;

    /**
     * Reload the configuration.
     *
     * @return If it succeeded.
     */
    public boolean reload() {
        synchronized (this) {
            try {
                // reload from file
                config.reloadOrDefaultThrowing("defaults/config.yml");

                /* cache values for fast access */
                {
                    checkedBlockEntities.clear();
                    checkedBlockTypes.clear();
                    config.getOrSupply("checked-block-entities", (Supplier<ArrayList<String>>) ArrayList::new)
                            .forEach(s -> {
                                final ResourceLocation loc = ResourceLocation.of(s, ':');

                                checkedBlockEntities.add(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(loc));
                                checkedBlockTypes.add(BuiltInRegistries.BLOCK.get(loc));
                            });
                }
                {
                    alwaysViewDist = config.get("always-view-distance");
                    alwaysViewDistSqr = alwaysViewDist * alwaysViewDist;
                }

                // return success
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reload configuration, uncaught error");
                e.printStackTrace();
                return false;
            }
        }
    }

}
