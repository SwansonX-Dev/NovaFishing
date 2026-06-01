package dev.nova.fishing.integration;

import dev.nova.fishing.NovaFishing;
import org.bukkit.Location;

public final class WorldGuardHook {
   private final boolean enabled;

   private WorldGuardHook(boolean enabled) {
      this.enabled = enabled;
   }

   public static WorldGuardHook attempt(NovaFishing plugin) {
      boolean has = plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
      if (has) {
         plugin.getLogger().info("WorldGuard detected (region awareness available).");
      }

      return new WorldGuardHook(has);
   }

   public boolean enabled() {
      return this.enabled;
   }

   public boolean canFishHere(Location l) {
      return true;
   }
}
