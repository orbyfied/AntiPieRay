package net.orbyfied.antipieray.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.orbyfied.j8.util.reflect.Reflector;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NmsHelper {


    /**
     * The reflector it uses.
     */
    private static final Reflector REFLECTOR = new Reflector("NmsHelper");

    /**
     * The Minecraft/CraftBukkit server version.
     */
    private static final String VERSION;

    /**
     * The CraftBukkit root package with the
     * correct version inlined. Used for
     * reflection.
     */
    private static final String CRAFT_BUKKIT_PACKAGE;

    static {

        VERSION = Bukkit.getServer().getClass().getName().split("\\.")[3];
        CRAFT_BUKKIT_PACKAGE = "org.bukkit.craftbukkit." + VERSION + ".";

    }

    /**
     * Get the CraftBukkit server version.
     *
     * @return The server version.
     */
    public static String getServerVersion() {
        return VERSION;
    }

    /**
     * Get a CraftBukkit by the name relative to
     * the {@code org.bukkit.craftbukkit.version} package.
     *
     * @throws RuntimeException If an error occurs.
     * @param relativeName The name relative to the
     *                     {@code org.bukkit.craftbukkit.version} package.
     * @return The class.
     */
    public static Class<?> getCraftBukkitClass(String relativeName) {
        try {
            return Class.forName(CRAFT_BUKKIT_PACKAGE + relativeName);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /* QOL Methods */

    public static Method getCraftBukkitMethod(String relativeClassName, String methodName, Class<?>... args) {
        try {
            Method m = getCraftBukkitClass(relativeClassName).getDeclaredMethod(methodName, args);
            m.setAccessible(true);
            return m;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Field getCraftBukkitField(String relativeClassName, String fieldName) {
        try {
            Field m = getCraftBukkitClass(relativeClassName).getDeclaredField(fieldName);
            m.setAccessible(true);
            return m;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    ////////////////////////////////////////////////////////////

    private static final Class<?> CLASS_CraftEntity = getCraftBukkitClass("entity.CraftEntity");
    private static final Field FIELD_CraftEntity_entity = REFLECTOR.reflectDeclaredFieldAccessible(CLASS_CraftEntity, "entity");

    public static ServerPlayer getPlayerHandle(Player player) {
        return REFLECTOR.reflectGetField(FIELD_CraftEntity_entity, player);
    }

    public static Entity getEntityHandle(org.bukkit.entity.Entity player) {
        return REFLECTOR.reflectGetField(FIELD_CraftEntity_entity, player);
    }

    private static final Class<?> CLASS_CraftInventory = getCraftBukkitClass("inventory.CraftInventory");
    private static final Field FIELD_CraftInventory_inventory = REFLECTOR.reflectDeclaredFieldAccessible(CLASS_CraftInventory, "inventory");

    public static Container getInventoryHandle(Inventory inventory) {
        return REFLECTOR.reflectGetField(FIELD_CraftInventory_inventory, inventory);
    }

    private static final Class<?> CLASS_CraftItemStack = getCraftBukkitClass("inventory.CraftItemStack");
    private static final Field FIELD_CraftItemStack_handle = REFLECTOR.reflectDeclaredFieldAccessible(CLASS_CraftItemStack, "handle");
    private static final Method METHOD_CraftItemStack_asNMSCopy = REFLECTOR.reflectMethod(CLASS_CraftItemStack, "asNMSCopy", new Class<?>[] { org.bukkit.inventory.ItemStack.class });
    private static final Method METHOD_CraftItemStack_asCraftMirror = REFLECTOR.reflectMethod(CLASS_CraftItemStack, "asCraftMirror", new Class<?>[] { ItemStack.class });

    public static ItemStack getItemHandle(org.bukkit.inventory.ItemStack stack) {
        return REFLECTOR.reflectGetField(FIELD_CraftItemStack_handle, stack);
    }

    public static ItemStack getNMSItemCopy(org.bukkit.inventory.ItemStack stack) {
        return REFLECTOR.reflectInvoke(METHOD_CraftItemStack_asNMSCopy, null, stack);
    }

    public static org.bukkit.inventory.ItemStack getBukkitItemMirror(ItemStack stack) {
        return REFLECTOR.reflectInvoke(METHOD_CraftItemStack_asCraftMirror, null, stack);
    }

    private static final Class<?> CLASS_CraftMagicNumbers = getCraftBukkitClass("util.CraftMagicNumbers");
    private static final Method METHOD_CraftMagicNumbers_getItem = REFLECTOR.reflectMethod(CLASS_CraftMagicNumbers, "getItem", new Class<?>[] { Material.class });

    public static Item getNMSMaterial(Material material) {
        return REFLECTOR.reflectInvoke(METHOD_CraftMagicNumbers_getItem, null, material);
    }

    private static final Class<?> CLASS_CraftChatMessage = getCraftBukkitClass("util.CraftChatMessage");
    private static final Method METHOD_CraftChatMessage_fromString = REFLECTOR.reflectMethod(CLASS_CraftChatMessage, "fromStringOrNull", new Class[] { String.class });

    public static Component getComponentFromString(String str) {
        return REFLECTOR.reflectInvoke(METHOD_CraftChatMessage_fromString, null, str);
    }

    private static final Class<?> CLASS_CraftWorld = getCraftBukkitClass("CraftWorld");
    private static final Field CLASS_CraftWorld_world = REFLECTOR.reflectDeclaredFieldAccessible(CLASS_CraftWorld, "world");

    public static ServerLevel getWorldHandle(World world) {
        return REFLECTOR.reflectGetField(CLASS_CraftWorld_world, world);
    }

    private static final Method CLASS_CraftScoreboard_getHandle =
            getCraftBukkitMethod("scoreboard.CraftScoreboard", "getHandle");

    public static Scoreboard getScoreboardHandle(org.bukkit.scoreboard.Scoreboard scoreboard) {
        return REFLECTOR.reflectInvoke(CLASS_CraftScoreboard_getHandle, scoreboard);
    }

    private static final Field CLASS_CraftTeam_team =
            getCraftBukkitField("scoreboard.CraftTeam", "team");

    public static Team getScoreboardTeamHandle(org.bukkit.scoreboard.Team team) {
        return REFLECTOR.reflectGetField(CLASS_CraftTeam_team, team);
    }

}
