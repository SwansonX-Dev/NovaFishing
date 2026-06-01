package dev.nova.fishing.commands;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.fishing.FishingSession;
import dev.nova.fishing.rod.RodInstance;
import dev.nova.fishing.util.TextUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class FishCommand implements CommandExecutor {
   private final NovaFishing plugin;

   public FishCommand(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player p) {
         if (!p.hasPermission("novafishing.use")) {
            p.sendMessage(TextUtil.mm(this.plugin.configs().message("no-permission")));
            return true;
         } else {
            FishingSession session = this.plugin.fishing().getSession(p);
            if (session != null) {
               this.plugin.fishing().tryReel(p);
               return true;
            } else {
               boolean hadDanglingHook = false;

               for (FishHook h : p.getWorld().getEntitiesByClass(FishHook.class)) {
                  if (p.equals(h.getShooter())) {
                     h.remove();
                     hadDanglingHook = true;
                  }
               }

               if (hadDanglingHook) {
                  p.sendActionBar(TextUtil.mm("<gray>Line retracted."));
                  return true;
               } else {
                  ItemStack inHand = p.getInventory().getItemInMainHand();
                  RodInstance rod = this.plugin.rods().read(inHand);
                  if (rod == null) {
                     ItemStack off = p.getInventory().getItemInOffHand();
                     RodInstance offRod = this.plugin.rods().read(off);
                     if (offRod != null) {
                        rod = offRod;
                        inHand = off;
                     }
                  }

                  if (rod == null) {
                     p.sendActionBar(TextUtil.mm("<red>Hold a Nova rod to use this command."));
                     return true;
                  } else if (!this.plugin.rods().canUse(p, rod.def())) {
                     p.sendActionBar(TextUtil.mm("<red>You don't have permission to use this rod."));
                     return true;
                  } else if (this.plugin.wordCheck() != null && !this.plugin.wordCheck().canFish(p)) {
                     this.plugin.wordCheck().warnBlocked(p);
                     return true;
                  } else if (!p.isDead() && p.isOnline()) {
                     FishHook hook;
                     try {
                        hook = (FishHook)p.launchProjectile(FishHook.class);
                     } catch (Throwable var12) {
                        this.plugin.getLogger().warning("Failed to launch fish hook for " + p.getName() + ": " + var12.getMessage());
                        p.sendActionBar(TextUtil.mm("<red>Couldn't cast — try right-clicking the rod instead."));
                        return true;
                     }

                     if (hook == null) {
                        p.sendActionBar(TextUtil.mm("<red>Couldn't cast — try right-clicking the rod instead."));
                        return true;
                     } else {
                        this.plugin.fishing().beginCast(p, hook, rod);
                        if (inHand.getType() == Material.FISHING_ROD) {
                           p.swingMainHand();
                        }

                        return true;
                     }
                  } else {
                     return true;
                  }
               }
            }
         }
      } else {
         sender.sendMessage(TextUtil.mm("<red>Only players can fish."));
         return true;
      }
   }
}
