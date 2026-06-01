package dev.nova.fishing.fishing;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.rod.RodInstance;
import dev.nova.fishing.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerFishEvent.State;
import org.bukkit.inventory.ItemStack;

public final class FishingListener implements Listener {
   private final NovaFishing plugin;

   public FishingListener(NovaFishing plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onFish(PlayerFishEvent e) {
      Player p = e.getPlayer();
      if (p.hasPermission("novafishing.use")) {
         ItemStack inHand = p.getInventory().getItemInMainHand();
         if (inHand == null || inHand.getType() == Material.AIR) {
            inHand = p.getInventory().getItemInOffHand();
         }

         RodInstance rod = this.plugin.rods().read(inHand);
         if (rod != null) {
            if (!this.plugin.rods().canUse(p, rod.def())) {
               p.sendActionBar(TextUtil.mm("<red>You don't have permission to use this rod."));
               e.setCancelled(true);
            } else if (e.getState() == State.FISHING && this.plugin.wordCheck() != null && !this.plugin.wordCheck().canFish(p)) {
               this.plugin.wordCheck().warnBlocked(p);
               e.setCancelled(true);
            } else {
               FishHook hook = e.getHook();
               switch (e.getState()) {
                  case FISHING:
                     this.handleCast(p, hook, rod);
                     break;
                  case BITE: {
                     FishingSession sxx = this.plugin.fishing().getSession(p);
                     if (sxx == null) {
                        return;
                     }

                     e.setCancelled(true);
                     this.plugin.fishing().enterBitePhase(p);
                     break;
                  }
                  case CAUGHT_FISH: {
                     FishingSession sx = this.plugin.fishing().getSession(p);
                     if (sx == null) {
                        return;
                     }

                     if (e.getCaught() != null) {
                        e.getCaught().remove();
                     }

                     e.setExpToDrop(0);
                     e.setCancelled(true);
                     this.plugin.fishing().forceCatch(p);
                     break;
                  }
                  case REEL_IN: {
                     FishingSession sx = this.plugin.fishing().getSession(p);
                     if (sx == null) {
                        return;
                     }

                     e.setCancelled(true);
                     this.plugin.fishing().tryReel(p);
                     break;
                  }
                  case IN_GROUND:
                  case FAILED_ATTEMPT:
                  case CAUGHT_ENTITY: {
                     FishingSession s = this.plugin.fishing().getSession(p);
                     if (s == null) {
                        return;
                     }

                     if (e.getCaught() != null) {
                        e.getCaught().remove();
                     }

                     e.setExpToDrop(0);
                     e.setCancelled(true);
                  }
               }
            }
         }
      }
   }

   private void handleCast(Player p, FishHook hook, RodInstance rod) {
      this.plugin.fishing().beginCast(p, hook, rod);
   }
}
