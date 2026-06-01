package dev.nova.fishing.integration;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.database.DatabaseManager;
import dev.nova.fishing.rod.RodDef;
import dev.nova.fishing.rod.RodInstance;
import dev.nova.fishing.util.TextUtil;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class PAPIHook extends PlaceholderExpansion {
   private static final int LEADERBOARD_SIZE = 10;
   private static final long REFRESH_TICKS = 100L;
   private final NovaFishing plugin;
   private volatile List<DatabaseManager.TopTokensEntry> cachedTokens = List.of();
   private volatile List<DatabaseManager.TopRodEntry> cachedRods = List.of();
   private BukkitTask refreshTask;

   private PAPIHook(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public static PAPIHook attempt(NovaFishing plugin) {
      if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
         return null;
      } else {
         try {
            PAPIHook hook = new PAPIHook(plugin);
            hook.register();
            hook.startRefresher();
            plugin.getLogger().info("PlaceholderAPI hooked.");
            return hook;
         } catch (Throwable var2) {
            plugin.getLogger().warning("PAPI hook failed: " + var2.getMessage());
            return null;
         }
      }
   }

   private void startRefresher() {
      this.refreshTask = this.plugin.getServer().getScheduler().runTaskTimerAsynchronously(this.plugin, () -> {
         try {
            Set<String> excluded = this.excludedNames();
            this.cachedTokens = this.plugin.db().topByTokens(10, excluded);
            this.cachedRods = this.plugin.db().topByMaxRod(10, this.plugin.rods()::tierIndex, excluded);
         } catch (Throwable var2) {
            this.plugin.getLogger().warning("PAPI leaderboard refresh: " + var2.getMessage());
         }
      }, 20L, 100L);
   }

   public boolean unregisterSafe() {
      if (this.refreshTask != null) {
         try {
            this.refreshTask.cancel();
         } catch (Throwable var3) {
         }
      }

      try {
         return super.unregister();
      } catch (Throwable var2) {
         return false;
      }
   }

   public String getIdentifier() {
      return "novafishing";
   }

   public String getAuthor() {
      return "Nova";
   }

   public String getVersion() {
      return this.plugin.getDescription().getVersion();
   }

   public boolean persist() {
      return true;
   }

   public String onRequest(OfflinePlayer p, String params) {
      if (params == null) {
         return null;
      } else {
         String key = params.toLowerCase();
         if (key.startsWith("top_tokens_") || key.startsWith("top_rod_")) {
            return this.resolveLeaderboard(key);
         } else if (p == null) {
            return "";
         } else {
            switch (key) {
               case "tokens":
                  return String.valueOf(this.plugin.tokens().get(p.getUniqueId()));
               case "rod_level":
               case "rod_max":
               case "rod_id":
                  if (p instanceof Player online) {
                     ItemStack stack = online.getInventory().getItemInMainHand();
                     RodInstance r = this.plugin.rods().read(stack);
                     if (r == null) {
                        return "";
                     }
                     return switch (key) {
                        case "rod_level" -> String.valueOf(r.level());
                        case "rod_max" -> String.valueOf(r.def().maxLevel());
                        case "rod_id" -> r.def().id();
                        default -> "";
                     };
                  }

                  return "";
               default:
                  return null;
            }
         }
      }
   }

   private String resolveLeaderboard(String key) {
      int lastUnderscore = key.lastIndexOf(95);
      if (lastUnderscore >= 0 && lastUnderscore != key.length() - 1) {
         int rank;
         try {
            rank = Integer.parseInt(key.substring(lastUnderscore + 1));
         } catch (NumberFormatException var11) {
            return "";
         }

         if (rank < 1) {
            return "";
         } else {
            String prefix = key.substring(0, lastUnderscore);
            if (prefix.startsWith("top_tokens_")) {
               String field = prefix.substring("top_tokens_".length());
               List<DatabaseManager.TopTokensEntry> rows = this.cachedTokens;
               if (rank > rows.size()) {
                  return "";
               } else {
                  DatabaseManager.TopTokensEntry e = rows.get(rank - 1);

                  return switch (field) {
                     case "name" -> e.name() == null ? "" : e.name();
                     case "amount" -> String.valueOf(e.tokens());
                     default -> "";
                  };
               }
            } else if (prefix.startsWith("top_rod_")) {
               String field = prefix.substring("top_rod_".length());
               List<DatabaseManager.TopRodEntry> rows = this.cachedRods;
               if (rank > rows.size()) {
                  return "";
               } else {
                  DatabaseManager.TopRodEntry e = rows.get(rank - 1);

                  return switch (field) {
                     case "name" -> e.name() == null ? "" : e.name();
                     case "id" -> e.rodId() == null ? "" : e.rodId();
                     case "level" -> e.currentLevel() <= 0 ? "" : String.valueOf(e.currentLevel());
                     case "tier" -> {
                        int idx = e.rodId() == null ? -1 : this.plugin.rods().tierIndex(e.rodId());
                        yield idx < 0 ? "" : String.valueOf(idx + 1);
                     }
                     case "display" -> {
                        RodDef def = e.rodId() == null ? null : this.plugin.rods().getRod(e.rodId());
                        yield def == null ? (e.rodId() == null ? "" : e.rodId()) : TextUtil.stripTags(def.displayName());
                     }
                     default -> "";
                  };
               }
            } else {
               return "";
            }
         }
      } else {
         return "";
      }
   }

   private Set<String> excludedNames() {
      List<String> raw = this.plugin.getConfig().getStringList("leaderboard.exclude-players");
      if (raw.isEmpty()) {
         return Set.of();
      } else {
         Set<String> out = new HashSet<>(raw.size());

         for (String n : raw) {
            if (n != null && !n.isBlank()) {
               out.add(n.trim().toLowerCase(Locale.ROOT));
            }
         }

         return out;
      }
   }
}
