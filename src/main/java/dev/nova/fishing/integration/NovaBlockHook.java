package dev.nova.fishing.integration;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.reward.RewardTier;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Soft hook into NovaBlock's skill system. A NovaFishing catch cancels the vanilla
 * PlayerFishEvent (and intercepts the bite), so NovaBlock's Fishing skill would never
 * gain XP while a Nova rod is in use. This bridge feeds each catch back to NovaBlock
 * reflectively — there's no compile-time dependency, and NovaFishing runs fine when
 * NovaBlock is absent.
 */
public final class NovaBlockHook {
   private final Plugin novaBlock;
   private final Method bridge;

   private NovaBlockHook(Plugin novaBlock, Method bridge) {
      this.novaBlock = novaBlock;
      this.bridge = bridge;
   }

   public static NovaBlockHook attempt(NovaFishing plugin) {
      try {
         Plugin nb = plugin.getServer().getPluginManager().getPlugin("NovaBlock");
         if (nb == null) {
            return null;
         } else {
            Method m = nb.getClass().getMethod("onExternalFishingCatch", Player.class, double.class);
            plugin.getLogger().info("NovaBlock hooked: Fishing catches now grant NovaBlock Fishing-skill XP.");
            return new NovaBlockHook(nb, m);
         }
      } catch (NoSuchMethodException var3) {
         plugin.getLogger()
            .warning("NovaBlock found but its fishing-skill bridge is missing — update NovaBlock to grant Fishing-skill XP from Nova rods.");
         return null;
      } catch (Throwable var4) {
         plugin.getLogger().warning("NovaBlock hook failed: " + var4.getMessage());
         return null;
      }
   }

   /** Award NovaBlock Fishing-skill XP for a catch, weighted by tier rarity. */
   public void onCatch(Player player, RewardTier tier) {
      if (this.novaBlock != null && this.novaBlock.isEnabled()) {
         try {
            this.bridge.invoke(this.novaBlock, player, weightFor(tier));
         } catch (Throwable var4) {
            // A fishing catch must never fail because the skill bridge hiccuped — swallow.
         }
      }
   }

   private static double weightFor(RewardTier tier) {
      return switch (tier) {
         case JUNK, COMMON -> 1.0;
         case UNCOMMON -> 1.5;
         case RARE -> 2.0;
         case EPIC -> 3.0;
         case LEGENDARY -> 5.0;
         case MYTHIC -> 8.0;
      };
   }
}
