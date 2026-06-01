package dev.nova.fishing.listeners;

import dev.nova.fishing.NovaFishing;
import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinListener implements Listener {
   private final NovaFishing plugin;

   public JoinListener(NovaFishing plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent e) {
      UUID uid = e.getPlayer().getUniqueId();
      String name = e.getPlayer().getName();
      this.plugin.db().runAsync(() -> this.plugin.db().touchPlayer(uid, name));
      this.plugin.rods().polishExistingRods(e.getPlayer());
      String starter = this.plugin.getConfig().getString("starter-rod", "");
      if (starter != null && !starter.isBlank() && !e.getPlayer().hasPlayedBefore()) {
         this.plugin.rods().giveRod(e.getPlayer(), starter, 1, 0L);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      UUID uid = e.getPlayer().getUniqueId();
      String name = e.getPlayer().getName();
      this.plugin.db().runAsync(() -> this.plugin.db().touchPlayer(uid, name));
   }
}
