package dev.nova.fishing.integration;

import dev.nova.fishing.NovaFishing;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class HDBHook {
   private final HeadDatabaseAPI api;

   private HDBHook(HeadDatabaseAPI api) {
      this.api = api;
   }

   public static HDBHook attempt(NovaFishing plugin) {
      try {
         if (plugin.getServer().getPluginManager().getPlugin("HeadDatabase") == null) {
            return null;
         } else {
            HeadDatabaseAPI api = new HeadDatabaseAPI();
            plugin.getLogger().info("HeadDatabase API hooked.");
            return new HDBHook(api);
         }
      } catch (Throwable var2) {
         plugin.getLogger().warning("HDB hook failed: " + var2.getMessage());
         return null;
      }
   }

   public ItemStack getHead(String id) {
      try {
         if (this.api == null) {
            return new ItemStack(Material.PLAYER_HEAD);
         } else {
            ItemStack s = this.api.getItemHead(id);
            return s == null ? new ItemStack(Material.PLAYER_HEAD) : s;
         }
      } catch (Throwable var3) {
         return new ItemStack(Material.PLAYER_HEAD);
      }
   }
}
