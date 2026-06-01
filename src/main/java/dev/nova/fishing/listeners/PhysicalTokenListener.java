package dev.nova.fishing.listeners;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.token.PhysicalTokenItem;
import dev.nova.fishing.util.TextUtil;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class PhysicalTokenListener implements Listener {
   private final NovaFishing plugin;

   public PhysicalTokenListener(NovaFishing plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = false
   )
   public void onInteract(PlayerInteractEvent e) {
      if (e.getHand() == EquipmentSlot.HAND) {
         Action a = e.getAction();
         if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            ItemStack stack = e.getItem();
            long perItem = PhysicalTokenItem.valueOf(this.plugin, stack);
            if (perItem >= 0L) {
               e.setCancelled(true);
               Player p = e.getPlayer();
               if (!p.hasPermission("novatoken.use")) {
                  p.sendMessage(TextUtil.mm(this.plugin.configs().message("no-permission")));
               } else {
                  int count = stack.getAmount();
                  long total = Math.multiplyExact(perItem, (long)count);
                  stack.setAmount(0);
                  this.plugin.tokens().give(p.getUniqueId(), total, false);
                  Map<String, String> ph = new HashMap<>();
                  ph.put("amount", String.valueOf(total));
                  ph.put("each", String.valueOf(perItem));
                  ph.put("count", String.valueOf(count));
                  p.sendMessage(TextUtil.mm(this.plugin.configs().message("token.physical-redeem"), ph));
               }
            }
         }
      }
   }
}
