package dev.nova.fishing.wordcheck;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.util.TextUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class WordCheckManager implements Listener {
   private static final String[] WORDS = new String[]{
      "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
   };
   private final NovaFishing plugin;
   private final Set<UUID> verified = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Long> recentlyAnswered = new ConcurrentHashMap<>();
   private volatile String currentWord;
   private volatile long currentWordAt = 0L;
   private BukkitTask task;

   public WordCheckManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public void start() {
      this.stop();
      if (this.enabled()) {
         long periodTicks = Math.max(600L, this.intervalMinutes() * 60L * 20L);
         this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::broadcast, periodTicks, periodTicks);
      }
   }

   public void stop() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
   }

   public void reschedule() {
      this.start();
   }

   public boolean enabled() {
      return this.plugin.getConfig().getBoolean("settings.word-check.enabled", true);
   }

   public long intervalMinutes() {
      return Math.max(1L, this.plugin.getConfig().getLong("settings.word-check.interval-minutes", 15L));
   }

   public void setIntervalMinutes(long minutes) {
      this.plugin.getConfig().set("settings.word-check.interval-minutes", Math.max(1L, minutes));
      this.plugin.saveConfig();
      this.reschedule();
   }

   public void setEnabled(boolean on) {
      this.plugin.getConfig().set("settings.word-check.enabled", on);
      this.plugin.saveConfig();
      if (on) {
         this.start();
      } else {
         this.stop();
         this.verified.clear();
         this.currentWord = null;
      }
   }

   public boolean canFish(Player p) {
      if (!this.enabled()) {
         return true;
      } else if (this.currentWord == null) {
         return true;
      } else {
         return p.hasPermission("novafishing.wordcheck.bypass") ? true : this.verified.contains(p.getUniqueId());
      }
   }

   public void warnBlocked(Player p) {
      String w = this.currentWord;
      if (w != null) {
         Map<String, String> ph = new HashMap<>();
         ph.put("word", w);
         p.sendMessage(TextUtil.mm(this.plugin.configs().message("word-check.blocked"), ph));
         this.showTitle(p, w);
      }
   }

   public void broadcast() {
      if (this.enabled()) {
         this.currentWord = WORDS[ThreadLocalRandom.current().nextInt(WORDS.length)];
         this.currentWordAt = System.currentTimeMillis();
         this.verified.clear();

         for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("novafishing.wordcheck.bypass")) {
               this.verified.add(p.getUniqueId());
            } else if (p.hasPermission("novafishing.use") && (this.isFishing(p) || this.isHoldingRod(p))) {
               this.showTitle(p, this.currentWord);
               Map<String, String> ph = new HashMap<>();
               ph.put("word", this.currentWord);
               p.sendMessage(TextUtil.mm(this.plugin.configs().message("word-check.prompt"), ph));

               try {
                  p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6F, 1.4F);
               } catch (Throwable var5) {
               }
            }
         }
      }
   }

   private boolean isFishing(Player p) {
      return this.plugin.fishing() != null && this.plugin.fishing().getSession(p) != null;
   }

   private boolean isHoldingRod(Player p) {
      return this.plugin.rods().isNovaRod(p.getInventory().getItemInMainHand()) || this.plugin.rods().isNovaRod(p.getInventory().getItemInOffHand());
   }

   private void showTitle(Player p, String word) {
      Map<String, String> ph = new HashMap<>();
      ph.put("word", word);
      String titleStr = this.plugin.configs().rawMessage("word-check.title");
      String subtitleStr = this.plugin.configs().rawMessage("word-check.subtitle");
      if (titleStr == null || titleStr.isEmpty()) {
         titleStr = "<gold><bold>Anti-AFK Check";
      }

      if (subtitleStr == null || subtitleStr.isEmpty()) {
         subtitleStr = "<yellow>Type <white><word></white> in chat";
      }

      Component title = TextUtil.mm(titleStr, ph);
      Component subtitle = TextUtil.mm(subtitleStr, ph);
      Title t = Title.title(title, subtitle, Times.times(Duration.ofMillis(300L), Duration.ofSeconds(5L), Duration.ofMillis(700L)));
      p.showTitle(t);
   }

   public String currentWord() {
      return this.currentWord;
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onChatModern(AsyncChatEvent e) {
      Player p = e.getPlayer();
      String word = this.currentWord;
      if (word != null) {
         if (!this.verified.contains(p.getUniqueId())) {
            String raw = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
            if (!raw.equalsIgnoreCase(word)) {
               if (this.wasRecentlyAnswered(p.getUniqueId())) {
                  e.setCancelled(true);

                  try {
                     e.viewers().clear();
                  } catch (Throwable var6) {
                  }
               }
            } else {
               e.setCancelled(true);

               try {
                  e.viewers().clear();
               } catch (Throwable var7) {
               }

               this.markVerified(p);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onChatLegacy(AsyncPlayerChatEvent e) {
      Player p = e.getPlayer();
      String word = this.currentWord;
      if (word != null) {
         if (!this.verified.contains(p.getUniqueId())) {
            String raw = e.getMessage() == null ? "" : e.getMessage().trim();
            if (!raw.equalsIgnoreCase(word)) {
               if (this.wasRecentlyAnswered(p.getUniqueId())) {
                  e.setCancelled(true);

                  try {
                     e.getRecipients().clear();
                  } catch (Throwable var6) {
                  }
               }
            } else {
               e.setCancelled(true);

               try {
                  e.getRecipients().clear();
               } catch (Throwable var7) {
               }

               this.markVerified(p);
            }
         }
      }
   }

   private void markVerified(Player p) {
      UUID id = p.getUniqueId();
      this.verified.add(id);
      this.recentlyAnswered.put(id, System.currentTimeMillis());
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         p.sendMessage(TextUtil.mm(this.plugin.configs().message("word-check.verified")));

         try {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.6F);
         } catch (Throwable var3) {
         }
      });
   }

   private boolean wasRecentlyAnswered(UUID id) {
      Long ts = this.recentlyAnswered.get(id);
      if (ts == null) {
         return false;
      } else if (System.currentTimeMillis() - ts > 1000L) {
         this.recentlyAnswered.remove(id);
         return false;
      } else {
         return true;
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      this.verified.remove(e.getPlayer().getUniqueId());
      this.recentlyAnswered.remove(e.getPlayer().getUniqueId());
   }
}
