package dev.nova.fishing.listeners;

import dev.nova.fishing.NovaFishing;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public final class RodProtectionListener implements Listener {
   private final NovaFishing plugin;
   private final Map<UUID, List<ItemStack>> deathStash = new HashMap<>();

   public RodProtectionListener(NovaFishing plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onItemDamage(PlayerItemDamageEvent e) {
      if (this.plugin.rods().isNovaRod(e.getItem())) {
         e.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBlockPlace(BlockPlaceEvent e) {
      Player placer = e.getPlayer();
      if (this.plugin.fishing().isBlockOccupiedByOtherHook(e.getBlockPlaced(), placer.getUniqueId())) {
         e.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDrop(PlayerDropItemEvent e) {
      if (this.plugin.rods().isNovaRod(e.getItemDrop().getItemStack())) {
         e.setCancelled(true);
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent e) {
      List<ItemStack> stash = new ArrayList<>();
      Iterator<ItemStack> it = e.getDrops().iterator();

      while (it.hasNext()) {
         ItemStack stack = it.next();
         if (this.plugin.rods().isNovaRod(stack)) {
            stash.add(stack.clone());
            it.remove();
         }
      }

      if (!stash.isEmpty()) {
         this.deathStash.put(e.getEntity().getUniqueId(), stash);
      }
   }

   @EventHandler
   public void onRespawn(PlayerRespawnEvent e) {
      List<ItemStack> stash = this.deathStash.remove(e.getPlayer().getUniqueId());
      if (stash != null) {
         for (ItemStack stack : stash) {
            Map<Integer, ItemStack> leftover = e.getPlayer().getInventory().addItem(new ItemStack[]{stack});
            leftover.values().forEach(i -> e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), i));
         }
      }
   }
}
