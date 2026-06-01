package dev.nova.fishing.challenge;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.database.DatabaseManager;
import dev.nova.fishing.reward.RewardTier;
import dev.nova.fishing.util.TextUtil;
import java.io.File;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class ChallengeManager {
   private final NovaFishing plugin;
   private final Map<String, Challenge> challenges = new LinkedHashMap<>();
   private FileConfiguration cfg;

   public ChallengeManager(NovaFishing plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      this.challenges.clear();
      File f = new File(this.plugin.getDataFolder(), "challenges.yml");
      if (!f.exists()) {
         this.plugin.saveResource("challenges.yml", false);
      }

      this.cfg = YamlConfiguration.loadConfiguration(f);
      ConfigurationSection root = this.cfg.getConfigurationSection("challenges");
      if (root != null) {
         for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s != null) {
               try {
                  Challenge.Period period = Challenge.Period.valueOf(s.getString("type", "daily").toUpperCase());
                  Challenge.Goal goal = Challenge.Goal.valueOf(s.getString("goal", "CATCH_ANY"));
                  RewardTier tier = RewardTier.safe(s.getString("tier", null));
                  long target = s.getLong("target", 1L);
                  String displayName = s.getString("display-name", id);
                  String desc = s.getString("description", "");
                  ConfigurationSection rs = s.getConfigurationSection("reward");
                  Challenge.Reward reward;
                  if (rs == null) {
                     reward = new Challenge.Reward(Challenge.Reward.Type.TOKEN, 0L, List.of());
                  } else {
                     Challenge.Reward.Type rType = Challenge.Reward.Type.valueOf(rs.getString("type", "TOKEN"));
                     long amt = rs.getLong("amount", 0L);
                     List<String> cmds = new ArrayList<>(rs.getStringList("commands"));
                     reward = new Challenge.Reward(rType, amt, cmds);
                  }

                  this.challenges.put(id, new Challenge(id, period, goal, tier, target, displayName, desc, reward));
               } catch (Exception var19) {
                  this.plugin.getLogger().warning("challenges.yml: bad entry '" + id + "': " + var19.getMessage());
               }
            }
         }
      }
   }

   public Map<String, Challenge> getAll() {
      return new LinkedHashMap<>(this.challenges);
   }

   public void onCatch(Player p, RewardTier tier) {
      for (Challenge c : this.challenges.values()) {
         if (c.goal() == Challenge.Goal.CATCH_ANY || c.goal() == Challenge.Goal.CATCH_TIER && c.targetTier() == tier) {
            this.bump(p, c, 1L);
         }
      }
   }

   public void onPrestige(Player p) {
      for (Challenge c : this.challenges.values()) {
         if (c.goal() == Challenge.Goal.PRESTIGE_ANY) {
            this.bump(p, c, 1L);
         }
      }
   }

   private void bump(Player p, Challenge c, long delta) {
      DatabaseManager.ChallengeProgress cp = this.plugin.db().getChallengeProgress(p.getUniqueId(), c.id());
      long resetAt = currentPeriodStart(c.period());
      if (cp.lastReset() < resetAt) {
         cp = new DatabaseManager.ChallengeProgress(0L, false, resetAt);
      }

      if (!cp.completed()) {
         long newProgress = cp.progress() + delta;
         boolean completed = newProgress >= c.target();
         UUID chUid = p.getUniqueId();
         String chId = c.id();
         long chProgress = Math.min(newProgress, c.target());
         this.plugin.db().runAsync(() -> this.plugin.db().setChallengeProgress(chUid, chId, chProgress, completed, resetAt));
         if (completed) {
            this.payout(p, c);
            Map<String, String> ph = new HashMap<>();
            ph.put("name", c.displayName());
            p.sendMessage(TextUtil.mm(this.plugin.configs().message("challenge.completed"), ph));
         }
      }
   }

   private void payout(Player p, Challenge c) {
      switch (c.reward().type()) {
         case TOKEN:
            this.plugin.tokens().give(p.getUniqueId(), c.reward().amount(), true);
            break;
         case COMMAND:
            for (String cmd : c.reward().commands()) {
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("<player>", p.getName()));
            }
      }
   }

   public static long currentPeriodStart(Challenge.Period period) {
      ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
      if (period == Challenge.Period.DAILY) {
         return now.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
      } else {
         ZonedDateTime monday = now.minusDays((long)((now.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue() + 7) % 7));
         return monday.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
      }
   }

   public DatabaseManager.ChallengeProgress progressOf(UUID uuid, Challenge c) {
      DatabaseManager.ChallengeProgress cp = this.plugin.db().getChallengeProgress(uuid, c.id());
      long resetAt = currentPeriodStart(c.period());
      return cp.lastReset() < resetAt ? new DatabaseManager.ChallengeProgress(0L, false, resetAt) : cp;
   }
}
