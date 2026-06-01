package dev.nova.fishing.anticheat;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.fishing.FishingSession;
import dev.nova.fishing.util.TextUtil;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

public final class AntiAutofishManager implements Listener {
   private static final int SAMPLE_WINDOW = 12;
   private static final int MIN_SAMPLES = 5;
   private final NovaFishing plugin;
   private final Map<UUID, AntiAutofishManager.PlayerData> data = new HashMap<>();

   public AntiAutofishManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public void onCastStart(Player p) {
      if (this.enabled() && !this.isExempt(p)) {
         AntiAutofishManager.PlayerData d = this.data.computeIfAbsent(p.getUniqueId(), id -> new AntiAutofishManager.PlayerData());
         long now = System.currentTimeMillis();
         d.castTimestamps.addLast(now);

         while (d.castTimestamps.size() > 12) {
            d.castTimestamps.pollFirst();
         }
      }
   }

   public boolean allowCatch(Player p, FishingSession s) {
      if (this.enabled() && !this.isExempt(p)) {
         AntiAutofishManager.PlayerData d = this.data.computeIfAbsent(p.getUniqueId(), id -> new AntiAutofishManager.PlayerData());
         long now = System.currentTimeMillis();
         if (this.lookConeEnabled() && s.hook != null && !s.hook.isDead()) {
            double angleDeg = angleToHookDegrees(p, s.hook);
            if (angleDeg > this.lookConeMaxDegrees()) {
               return this.trip(p, d, "look", "aimed " + Math.round(angleDeg) + "deg off bobber (max " + Math.round(this.lookConeMaxDegrees()) + "deg)");
            }
         }

         long reactionMs = Math.max(0L, now - s.biteAtMs);
         d.reactionMs.addLast(reactionMs);

         while (d.reactionMs.size() > 12) {
            d.reactionMs.pollFirst();
         }

         if (this.jitterEnabled() && d.castTimestamps.size() >= 5) {
            double sd = stddevIntervals(d.castTimestamps);
            double mean = meanIntervals(d.castTimestamps);
            if (sd < this.jitterMaxStddevMs() && mean > 1000.0) {
               return this.trip(p, d, "jitter", String.format("cast interval %.0fms+-%.0fms (min stddev %.0fms)", mean, sd, this.jitterMaxStddevMs()));
            }
         }

         if (this.reactionEnabled() && d.reactionMs.size() >= 5) {
            double sd = stddev(d.reactionMs);
            if (sd > this.reactionMaxStddevMs()) {
               return this.trip(p, d, "reaction", String.format("reaction stddev %.0fms (max %.0fms)", sd, this.reactionMaxStddevMs()));
            }
         }

         if (this.activityEnabled() && d.lastLookChangeMs > 0L) {
            long sinceLook = now - d.lastLookChangeMs;
            long max = this.activityMaxIdleMs();
            if (sinceLook > max) {
               return this.trip(p, d, "activity", "no look change for " + sinceLook / 1000L + "s (max " + max / 1000L + "s)");
            }
         }

         if (d.strikes > 0) {
            d.strikes--;
         }

         return true;
      } else {
         return true;
      }
   }

   @EventHandler
   public void onMove(PlayerMoveEvent e) {
      if (e.getTo() != null) {
         if (e.getFrom().getYaw() != e.getTo().getYaw() || e.getFrom().getPitch() != e.getTo().getPitch()) {
            AntiAutofishManager.PlayerData d = this.data.computeIfAbsent(e.getPlayer().getUniqueId(), id -> new AntiAutofishManager.PlayerData());
            d.lastLookChangeMs = System.currentTimeMillis();
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      this.data.remove(e.getPlayer().getUniqueId());
   }

   private boolean trip(Player p, AntiAutofishManager.PlayerData d, String detector, String detail) {
      d.strikes++;
      long now = System.currentTimeMillis();
      String msg = this.plugin.configs().rawMessage("antifish.look-at-bobber");
      if (msg == null || msg.isEmpty()) {
         msg = "<red>Look at your bobber when reeling.";
      }

      p.sendActionBar(TextUtil.mm(msg));
      if (now - d.lastStaffAlertMs > this.staffAlertCooldownMs()) {
         d.lastStaffAlertMs = now;
         Component alert = TextUtil.mm(
            "<dark_red>[Anti-AutoFish]</dark_red> <yellow>"
               + p.getName()
               + "</yellow> <gray>tripped <white>"
               + detector
               + "</white> ("
               + detail
               + ", strikes: "
               + d.strikes
               + ")"
         );

         for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("novafishing.alert.autofish")) {
               staff.sendMessage(alert);
            }
         }

         Bukkit.getConsoleSender().sendMessage(alert);
      }

      return false;
   }

   private boolean isExempt(Player p) {
      return p.hasPermission("novafishing.antiautofish.bypass");
   }

   private FileConfiguration cfg() {
      return this.plugin.getConfig();
   }

   private boolean enabled() {
      return this.cfg().getBoolean("settings.anti-autofish.enabled", true);
   }

   private boolean lookConeEnabled() {
      return this.cfg().getBoolean("settings.anti-autofish.look-cone.enabled", true);
   }

   private double lookConeMaxDegrees() {
      return this.cfg().getDouble("settings.anti-autofish.look-cone.max-degrees", 35.0);
   }

   private boolean jitterEnabled() {
      return this.cfg().getBoolean("settings.anti-autofish.cast-jitter.enabled", true);
   }

   private double jitterMaxStddevMs() {
      return this.cfg().getDouble("settings.anti-autofish.cast-jitter.min-stddev-ms", 350.0);
   }

   private boolean reactionEnabled() {
      return this.cfg().getBoolean("settings.anti-autofish.reaction.enabled", true);
   }

   private double reactionMaxStddevMs() {
      return this.cfg().getDouble("settings.anti-autofish.reaction.max-stddev-ms", 700.0);
   }

   private boolean activityEnabled() {
      return this.cfg().getBoolean("settings.anti-autofish.activity.enabled", true);
   }

   private long activityMaxIdleMs() {
      return this.cfg().getLong("settings.anti-autofish.activity.max-idle-seconds", 45L) * 1000L;
   }

   private long staffAlertCooldownMs() {
      return this.cfg().getLong("settings.anti-autofish.staff-alert-cooldown-seconds", 30L) * 1000L;
   }

   private static double angleToHookDegrees(Player p, FishHook hook) {
      Location eye = p.getEyeLocation();
      Vector toHook = hook.getLocation().toVector().subtract(eye.toVector());
      if (toHook.lengthSquared() < 1.0E-4) {
         return 0.0;
      } else {
         toHook.normalize();
         Vector look = eye.getDirection();
         double dot = Math.max(-1.0, Math.min(1.0, look.dot(toHook)));
         return Math.toDegrees(Math.acos(dot));
      }
   }

   private static double meanIntervals(Deque<Long> stamps) {
      if (stamps.size() < 2) {
         return 0.0;
      } else {
         Long prev = null;
         double sum = 0.0;
         int n = 0;

         for (Long t : stamps) {
            if (prev != null) {
               sum += (double)(t - prev);
               n++;
            }

            prev = t;
         }

         return n == 0 ? 0.0 : sum / (double)n;
      }
   }

   private static double stddevIntervals(Deque<Long> stamps) {
      if (stamps.size() < 2) {
         return Double.POSITIVE_INFINITY;
      } else {
         double mean = meanIntervals(stamps);
         Long prev = null;
         double sq = 0.0;
         int n = 0;

         for (Long t : stamps) {
            if (prev != null) {
               double d = (double)(t - prev) - mean;
               sq += d * d;
               n++;
            }

            prev = t;
         }

         return n == 0 ? Double.POSITIVE_INFINITY : Math.sqrt(sq / (double)n);
      }
   }

   private static double stddev(Deque<Long> values) {
      if (values.size() < 2) {
         return Double.POSITIVE_INFINITY;
      } else {
         double mean = 0.0;

         for (Long v : values) {
            mean += (double)v.longValue();
         }

         mean /= (double)values.size();
         double sq = 0.0;

         for (Long v : values) {
            double d = (double)v.longValue() - mean;
            sq += d * d;
         }

         return Math.sqrt(sq / (double)values.size());
      }
   }

   private static final class PlayerData {
      final Deque<Long> castTimestamps = new ArrayDeque<>(13);
      final Deque<Long> reactionMs = new ArrayDeque<>(13);
      long lastLookChangeMs = 0L;
      long lastStaffAlertMs = 0L;
      int strikes = 0;
   }
}
