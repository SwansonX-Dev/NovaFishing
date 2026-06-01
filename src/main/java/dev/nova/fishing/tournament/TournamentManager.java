package dev.nova.fishing.tournament;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.reward.RewardTier;
import dev.nova.fishing.util.TextUtil;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class TournamentManager {
   private final NovaFishing plugin;
   private FileConfiguration cfg;
   private TournamentManager.Type type;
   private long endAt;
   private final Map<UUID, Long> scores = new HashMap<>();
   private final Map<UUID, String> names = new HashMap<>();
   private BukkitTask announceTask;
   private BukkitTask endTask;
   private BukkitTask scheduleTask;
   private final Set<String> firedTodayMinutes = new HashSet<>();
   private int lastCheckedDayOfYear = -1;

   public TournamentManager(NovaFishing plugin) {
      this.plugin = plugin;
      this.reload();
      this.startScheduler();
   }

   public void reload() {
      File f = new File(this.plugin.getDataFolder(), "tournaments.yml");
      if (!f.exists()) {
         this.plugin.saveResource("tournaments.yml", false);
      }

      this.cfg = YamlConfiguration.loadConfiguration(f);
      if (this.scheduleTask != null) {
         this.startScheduler();
      }
   }

   private void startScheduler() {
      if (this.scheduleTask != null) {
         this.scheduleTask.cancel();
         this.scheduleTask = null;
      }

      if (this.cfg.getBoolean("schedule.enabled", true)) {
         this.scheduleTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickScheduler, 600L, 600L);
      }
   }

   private void tickScheduler() {
      LocalTime now = LocalTime.now();
      int doy = LocalDate.now().getDayOfYear();
      if (doy != this.lastCheckedDayOfYear) {
         this.firedTodayMinutes.clear();
         this.lastCheckedDayOfYear = doy;
      }

      String slot = String.format("%02d:%02d", now.getHour(), now.getMinute());

      for (String raw : this.cfg.getStringList("schedule.times")) {
         String normalized = normalizeHHmm(raw);
         if (normalized != null && normalized.equals(slot) && !this.firedTodayMinutes.contains(normalized)) {
            this.firedTodayMinutes.add(normalized);
            if (this.isActive()) {
               this.plugin.getLogger().info("Scheduled tournament " + normalized + " skipped — another is already running.");
               return;
            }

            long mins = this.cfg.getLong("schedule.duration-minutes", 30L);
            String typeStr = this.cfg.getString("schedule.type", "TIER_WEIGHTED");

            TournamentManager.Type t;
            try {
               t = TournamentManager.Type.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException var13) {
               this.plugin.getLogger().warning("tournaments.yml schedule.type '" + typeStr + "' invalid; using TIER_WEIGHTED.");
               t = TournamentManager.Type.TIER_WEIGHTED;
            }

            this.start(mins, t);
            return;
         }
      }
   }

   private static String normalizeHHmm(String raw) {
      if (raw == null) {
         return null;
      } else {
         String s = raw.trim();
         int colon = s.indexOf(58);
         if (colon > 0 && colon < s.length() - 1) {
            try {
               int h = Integer.parseInt(s.substring(0, colon).trim());
               int m = Integer.parseInt(s.substring(colon + 1).trim());
               if (h == 24 && m == 0) {
                  h = 0;
               }

               return h >= 0 && h <= 23 && m >= 0 && m <= 59 ? String.format("%02d:%02d", h, m) : null;
            } catch (NumberFormatException var5) {
               return null;
            }
         } else {
            return null;
         }
      }
   }

   public boolean isActive() {
      return this.endAt > System.currentTimeMillis();
   }

   public TournamentManager.Type type() {
      return this.type;
   }

   public long timeRemainingMs() {
      return Math.max(0L, this.endAt - System.currentTimeMillis());
   }

   public Map<UUID, Long> scores() {
      return new HashMap<>(this.scores);
   }

   public boolean start(long durationMinutes, TournamentManager.Type type) {
      if (this.isActive()) {
         return false;
      } else {
         this.type = type;
         this.endAt = System.currentTimeMillis() + durationMinutes * 60000L;
         this.scores.clear();
         this.names.clear();
         Map<String, String> ph = new HashMap<>();
         ph.put("minutes", String.valueOf(durationMinutes));

         String friendlyType = switch (type) {
            case ANY_CATCH -> "Any Catch (+1 pt each)";
            case TIER_WEIGHTED -> "Points per Tier";
         };
         ph.put("type", friendlyType);
         Bukkit.broadcast(TextUtil.mm(this.plugin.configs().rawMessage("tournament.start"), ph));
         this.playGlobalSound(this.cfg.getString("settings.start-sound", "UI_TOAST_CHALLENGE_COMPLETE"), 1.0F, 1.0F);
         long announceInterval = this.cfg.getLong("settings.announce-interval-seconds", 300L);
         if (this.announceTask != null) {
            this.announceTask.cancel();
         }

         this.announceTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::announceStandings, announceInterval * 20L, announceInterval * 20L);
         if (this.endTask != null) {
            this.endTask.cancel();
         }

         this.endTask = Bukkit.getScheduler().runTaskLater(this.plugin, this::end, durationMinutes * 60L * 20L);
         return true;
      }
   }

   public void stop() {
      if (this.isActive()) {
         this.endAt = 0L;
         if (this.announceTask != null) {
            this.announceTask.cancel();
            this.announceTask = null;
         }

         if (this.endTask != null) {
            this.endTask.cancel();
            this.endTask = null;
         }

         Bukkit.broadcast(TextUtil.mm(this.plugin.configs().rawMessage("tournament.cancelled")));
      }
   }

   public void onCatch(Player p, RewardTier tier) {
      if (this.isActive()) {
         long delta = switch (this.type) {
            case ANY_CATCH -> 1L;
            case TIER_WEIGHTED -> {
               switch (tier) {
                  case JUNK:
                     yield 0L;
                  case COMMON:
                     yield 1L;
                  case UNCOMMON:
                     yield 2L;
                  case RARE:
                     yield 5L;
                  case EPIC:
                     yield 10L;
                  case LEGENDARY:
                     yield 25L;
                  case MYTHIC:
                     yield 100L;
                  default:
                     throw new MatchException(null, null);
               }
            }
         };
         if (delta > 0L) {
            this.scores.merge(p.getUniqueId(), delta, Long::sum);
            this.names.put(p.getUniqueId(), p.getName());
         }
      }
   }

   private void announceStandings() {
      if (this.isActive()) {
         int top = this.cfg.getInt("settings.show-top-n", 3);
         List<Entry<UUID, Long>> ranked = this.sortedScores();
         long remaining = this.timeRemainingMs() / 1000L;
         Bukkit.broadcast(
            TextUtil.mm(
               "<gradient:#FF6A00:#FFD200><bold>Tournament</bold></gradient> <gray>standings — <yellow>" + remaining / 60L + "m " + remaining % 60L + "s left:"
            )
         );
         int rank = 1;

         for (Entry<UUID, Long> e : ranked) {
            if (rank > top) {
               break;
            }

            Bukkit.broadcast(TextUtil.mm(" <gold>" + rank + ". <yellow>" + this.names.getOrDefault(e.getKey(), "?") + " <dark_gray>- <aqua>" + e.getValue()));
            rank++;
         }

         if (ranked.isEmpty()) {
            Bukkit.broadcast(TextUtil.mm("<gray>No catches yet."));
         }
      }
   }

   private void end() {
      this.endAt = 0L;
      if (this.announceTask != null) {
         this.announceTask.cancel();
         this.announceTask = null;
      }

      if (this.endTask != null) {
         this.endTask.cancel();
         this.endTask = null;
      }

      List<Entry<UUID, Long>> ranked = this.sortedScores();
      Bukkit.broadcast(TextUtil.mm(this.plugin.configs().rawMessage("tournament.ended")));
      this.playGlobalSound(this.cfg.getString("settings.end-sound", "ENTITY_PLAYER_LEVELUP"), 1.0F, 1.2F);
      String[] places = new String[]{"first", "second", "third"};

      for (int i = 0; i < Math.min(places.length, ranked.size()); i++) {
         Entry<UUID, Long> e = ranked.get(i);
         String name = this.names.getOrDefault(e.getKey(), "?");
         Bukkit.broadcast(TextUtil.mm("<gold>#" + (i + 1) + " <yellow>" + name + " <dark_gray>- <aqua>" + e.getValue()));

         for (String cmd : this.cfg.getStringList("prizes." + places[i])) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("<player>", name));
         }
      }

      this.scores.clear();
      this.names.clear();
   }

   private List<Entry<UUID, Long>> sortedScores() {
      List<Entry<UUID, Long>> list = new ArrayList<>(this.scores.entrySet());
      list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
      return list;
   }

   private void playGlobalSound(String name, float volume, float pitch) {
      if (name != null && !name.isBlank()) {
         Sound sound;
         try {
            sound = Sound.valueOf(name.toUpperCase());
         } catch (IllegalArgumentException var7) {
            this.plugin.getLogger().warning("tournaments.yml: unknown Sound '" + name + "'");
            return;
         }

         for (Player pl : Bukkit.getOnlinePlayers()) {
            pl.playSound(pl.getLocation(), sound, volume, pitch);
         }
      }
   }

   public static enum Type {
      ANY_CATCH,
      TIER_WEIGHTED;
   }
}
