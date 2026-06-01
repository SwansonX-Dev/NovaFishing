package dev.nova.fishing.prompt;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ChatPromptManager implements Listener {
   private final NovaFishing plugin;
   private final Map<UUID, Consumer<String>> pending = new HashMap<>();
   private final Map<UUID, Long> recentlyHandled = new ConcurrentHashMap<>();

   public ChatPromptManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public void ask(Player p, String promptMiniMessage, Consumer<String> onResult) {
      this.pending.put(p.getUniqueId(), onResult);
      p.sendMessage(TextUtil.mm(promptMiniMessage));
      p.sendMessage(TextUtil.mm("<gray>(Type your answer in chat, or <yellow>cancel</yellow> to abort.)"));
   }

   public boolean isPending(Player p) {
      return this.pending.containsKey(p.getUniqueId());
   }

   public void cancelPending(Player p) {
      Consumer<String> cb = this.pending.remove(p.getUniqueId());
      if (cb != null) {
         Bukkit.getScheduler().runTask(this.plugin, () -> cb.accept(null));
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onChatModern(AsyncChatEvent e) {
      Player p = e.getPlayer();
      UUID id = p.getUniqueId();
      boolean isPrompted = this.pending.containsKey(id);
      boolean wasRecent = this.wasRecentlyHandled(id);
      if (isPrompted || wasRecent) {
         e.setCancelled(true);

         try {
            e.viewers().clear();
         } catch (Throwable var7) {
         }

         if (isPrompted) {
            this.recentlyHandled.put(id, System.currentTimeMillis());
            String raw = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
            this.deliver(p, raw);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onChatLegacy(AsyncPlayerChatEvent e) {
      Player p = e.getPlayer();
      UUID id = p.getUniqueId();
      boolean isPrompted = this.pending.containsKey(id);
      boolean wasRecent = this.wasRecentlyHandled(id);
      if (isPrompted || wasRecent) {
         e.setCancelled(true);

         try {
            e.getRecipients().clear();
         } catch (Throwable var7) {
         }

         if (isPrompted) {
            this.recentlyHandled.put(id, System.currentTimeMillis());
            String raw = e.getMessage() == null ? "" : e.getMessage().trim();
            this.deliver(p, raw);
         }
      }
   }

   private boolean wasRecentlyHandled(UUID id) {
      Long ts = this.recentlyHandled.get(id);
      if (ts == null) {
         return false;
      } else if (System.currentTimeMillis() - ts > 1000L) {
         this.recentlyHandled.remove(id);
         return false;
      } else {
         return true;
      }
   }

   private void deliver(Player p, String raw) {
      Consumer<String> cb = this.pending.remove(p.getUniqueId());
      if (cb != null) {
         String value = raw.equalsIgnoreCase("cancel") ? null : raw;
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            try {
               cb.accept(value);
            } catch (Throwable var5) {
               this.plugin.getLogger().warning("Chat-prompt callback failed: " + var5.getMessage());
            }

            if (value == null) {
               p.sendMessage(TextUtil.mm("<gray>Cancelled."));
            }
         });
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      this.pending.remove(e.getPlayer().getUniqueId());
      this.recentlyHandled.remove(e.getPlayer().getUniqueId());
   }
}
