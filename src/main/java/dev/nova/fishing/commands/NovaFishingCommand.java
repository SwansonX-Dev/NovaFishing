package dev.nova.fishing.commands;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.config.ConfigManager;
import dev.nova.fishing.database.DatabaseManager;
import dev.nova.fishing.gui.ChallengeGUI;
import dev.nova.fishing.hologram.Hologram;
import dev.nova.fishing.reward.RewardTier;
import dev.nova.fishing.rod.RodDef;
import dev.nova.fishing.rod.RodInstance;
import dev.nova.fishing.rod.RodManager;
import dev.nova.fishing.tournament.TournamentManager;
import dev.nova.fishing.util.TextUtil;
import dev.nova.fishing.voidfish.VoidRegion;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class NovaFishingCommand implements CommandExecutor, TabCompleter {
   private final NovaFishing plugin;

   public NovaFishingCommand(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         this.sendHelp(sender, label);
         return true;
      } else {
         String sub = args[0].toLowerCase();
         switch (sub) {
            case "help":
               this.sendHelp(sender, label);
               break;
            case "reload":
               if (!sender.hasPermission("novafishing.admin")) {
                  this.deny(sender);
                  return true;
               }

               this.plugin.reloadAll();
               sender.sendMessage(TextUtil.mm(this.plugin.configs().message("reload-success")));
               break;
            case "give":
               this.cmdGive(sender, label, args);
               break;
            case "setpos1":
               this.cmdSetPos(sender, label, args, true);
               break;
            case "setpos2":
               this.cmdSetPos(sender, label, args, false);
               break;
            case "clearpos":
               this.cmdClearPos(sender, label, args);
               break;
            case "rewards":
               this.cmdRewards(sender);
               break;
            case "info":
               this.cmdInfo(sender);
               break;
            case "top":
               this.cmdTop(sender);
               break;
            case "togglelava":
               this.cmdToggleLava(sender);
               break;
            case "voidlist":
               this.cmdVoidList(sender, label);
               break;
            case "prestige":
               this.cmdPrestige(sender);
               break;
            case "upgrade":
               this.cmdUpgrade(sender);
               break;
            case "resetconfig":
               this.cmdResetConfig(sender, label, args);
               break;
            case "skin":
               this.cmdSkin(sender, label, args);
               break;
            case "challenges":
               this.cmdChallenges(sender);
               break;
            case "stats":
               this.cmdStats(sender, label, args);
               break;
            case "wordcheck":
               this.cmdWordCheck(sender, label, args);
               break;
            case "tournament":
               this.cmdTournament(sender, label, args);
               break;
            case "lbexclude":
               this.cmdLbExclude(sender, label, args);
               break;
            case "holo":
            case "hologram":
               this.cmdHolo(sender, label, args);
               break;
            case "lbhologram":
               this.cmdLbHologram(sender, label, args);
               break;
            default:
               sender.sendMessage(TextUtil.mm(this.plugin.configs().message("unknown-command").replace("<label>", label)));
         }

         return true;
      }
   }

   private void cmdGive(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else if (args.length < 3) {
         sender.sendMessage(TextUtil.mm("<red>/" + label + " give <player> <rodId> [level] [xp]"));
      } else {
         Player target = Bukkit.getPlayerExact(args[1]);
         if (target == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("player", args[1]);
            sender.sendMessage(TextUtil.mm(this.plugin.configs().message("player-not-found"), ph));
         } else {
            String rodId = args[2];
            if (this.plugin.rods().getRod(rodId) == null) {
               sender.sendMessage(TextUtil.mm("<red>Unknown rod id: <yellow>" + rodId));
            } else {
               int level = 1;
               long xp = 0L;
               if (args.length > 3) {
                  try {
                     level = Math.max(1, Integer.parseInt(args[3]));
                  } catch (Exception var11) {
                  }
               }

               if (args.length > 4) {
                  try {
                     xp = Math.max(0L, Long.parseLong(args[4]));
                  } catch (Exception var10) {
                  }
               }

               this.plugin.rods().giveRod(target, rodId, level, xp);
               Map<String, String> ph = new HashMap<>();
               ph.put("rod", rodId);
               ph.put("player", target.getName());
               sender.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.given"), ph));
            }
         }
      }
   }

   private void cmdSetPos(CommandSender sender, String label, String[] args, boolean first) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else if (sender instanceof Player p) {
         if (args.length < 2) {
            sender.sendMessage(TextUtil.mm("<red>/" + label + " setpos" + (first ? "1" : "2") + " <name>"));
         } else {
            if (first) {
               this.plugin.voids().setPos1(p, args[1]);
            } else {
               this.plugin.voids().setPos2(p, args[1]);
            }

            Map<String, String> ph = new HashMap<>();
            ph.put("name", args[1]);
            p.sendMessage(TextUtil.mm(this.plugin.configs().message("void.pos" + (first ? "1" : "2") + "-set"), ph));
         }
      } else {
         sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
      }
   }

   private void cmdClearPos(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else if (args.length < 2) {
         sender.sendMessage(TextUtil.mm("<red>/" + label + " clearpos <name>"));
      } else {
         boolean ok = this.plugin.voids().delete(args[1]);
         Map<String, String> ph = new HashMap<>();
         ph.put("name", args[1]);
         sender.sendMessage(TextUtil.mm(ok ? this.plugin.configs().message("void.cleared") : "<red>No such region: <yellow><name>", ph));
      }
   }

   private void cmdRewards(CommandSender sender) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else if (sender instanceof Player p) {
         this.plugin.gui().openStaffRewards(p);
      } else {
         sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
      }
   }

   private void cmdInfo(CommandSender sender) {
      sender.sendMessage(TextUtil.mm("<gold><bold>NovaFishing</bold></gold>"));
      sender.sendMessage(TextUtil.mm("<gray>Rods loaded:"));

      for (RodDef r : this.plugin.rods().getRods()) {
         sender.sendMessage(TextUtil.mm(" <yellow>" + r.id() + " <dark_gray>- <gray>max " + r.maxLevel() + ", <gray>max prestige " + r.maxPrestige()));
      }

      sender.sendMessage(TextUtil.mm("<gray>Void regions: <yellow>" + this.plugin.voids().regions().size()));
      sender.sendMessage(TextUtil.mm("<gray>Challenges loaded: <yellow>" + this.plugin.challenges().getAll().size()));
      sender.sendMessage(TextUtil.mm("<gray>Tournament active: <yellow>" + this.plugin.tournament().isActive()));
   }

   private void cmdTop(CommandSender sender) {
      List<DatabaseManager.TopEntry> entries = this.plugin.db().topByXp(10);
      sender.sendMessage(TextUtil.mm("<gold><bold>Top Anglers</bold>"));
      int i = 1;

      for (DatabaseManager.TopEntry e : entries) {
         sender.sendMessage(
            TextUtil.mm(
               " <gray>"
                  + i
                  + ". <yellow>"
                  + (e.name() == null ? "?" : e.name())
                  + " <dark_gray>- <aqua>"
                  + e.totalXp()
                  + " XP <gray>("
                  + e.totalCatches()
                  + " catches)"
            )
         );
         i++;
      }

      if (entries.isEmpty()) {
         sender.sendMessage(TextUtil.mm("<gray>No data yet."));
      }
   }

   private void cmdToggleLava(CommandSender sender) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else {
         boolean v = !this.plugin.getConfig().getBoolean("settings.allow-lava-anywhere", true);
         this.plugin.getConfig().set("settings.allow-lava-anywhere", v);
         this.plugin.saveConfig();
         sender.sendMessage(TextUtil.mm("<green>allow-lava-anywhere set to <yellow>" + v));
      }
   }

   private void cmdVoidList(CommandSender sender, String label) {
      sender.sendMessage(TextUtil.mm("<gold><bold>Void Regions</bold>"));

      for (VoidRegion r : this.plugin.voids().regions()) {
         sender.sendMessage(
            TextUtil.mm(
               " <yellow>"
                  + r.name
                  + " <dark_gray>- <gray>"
                  + r.world
                  + " <dark_gray>(<gray>"
                  + r.minX
                  + ","
                  + r.minY
                  + ","
                  + r.minZ
                  + " -> "
                  + r.maxX
                  + ","
                  + r.maxY
                  + ","
                  + r.maxZ
                  + "<dark_gray>)"
            )
         );
      }

      if (this.plugin.voids().regions().isEmpty()) {
         sender.sendMessage(TextUtil.mm("<gray>None set. Use /" + label + " setpos1/setpos2 <name>"));
      }
   }

   private void cmdPrestige(CommandSender sender) {
      if (sender instanceof Player p) {
         if (!p.hasPermission("novafishing.prestige")) {
            this.deny(sender);
         } else {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            RodInstance r = this.plugin.rods().read(inHand);
            if (r == null) {
               p.sendMessage(TextUtil.mm("<red>Hold a Nova rod first."));
            } else if (!r.isMaxLevel()) {
               p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.prestige-fail")));
            } else if (!r.canPrestige()) {
               p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.prestige-cap")));
            } else {
               if (this.plugin.rods().prestige(p, inHand) && this.plugin.challenges() != null) {
                  this.plugin.challenges().onPrestige(p);
               }
            }
         }
      } else {
         sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
      }
   }

   private void cmdResetConfig(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else if (args.length < 2) {
         sender.sendMessage(TextUtil.mm("<gold>/" + label + " resetconfig <file>"));
         sender.sendMessage(TextUtil.mm("<gray>  Backs up the file to <yellow><name>.bak-<timestamp></yellow> and"));
         sender.sendMessage(TextUtil.mm("<gray>  re-extracts the bundled version from the jar."));
         sender.sendMessage(TextUtil.mm("<gray>Available: <yellow>" + String.join(", ", ConfigManager.resettableNames()) + "<gray> or <yellow>all"));
      } else {
         String target = args[1].toLowerCase();
         if (!target.endsWith(".yml") && !target.equals("all")) {
            target = target + ".yml";
         }

         List<String> files;
         if (target.equals("all")) {
            files = ConfigManager.resettableNames();
         } else {
            if (!ConfigManager.resettableNames().contains(target)) {
               sender.sendMessage(TextUtil.mm("<red>Unknown config file: <yellow>" + target));
               return;
            }

            files = List.of(target);
         }

         for (String name : files) {
            File bak = this.plugin.configs().resetToDefault(name);
            if (bak != null) {
               sender.sendMessage(TextUtil.mm("<green>Reset <yellow>" + name + " <gray>(backup: <yellow>" + bak.getName() + "<gray>)"));
            } else {
               sender.sendMessage(TextUtil.mm("<green>Extracted fresh <yellow>" + name));
            }
         }

         this.plugin.reloadAll();
         sender.sendMessage(TextUtil.mm("<green>Reloaded."));
      }
   }

   private void cmdUpgrade(CommandSender sender) {
      if (sender instanceof Player p) {
         if (!p.hasPermission("novafishing.upgrade")) {
            this.deny(sender);
         } else {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            RodInstance r = this.plugin.rods().read(inHand);
            if (r == null) {
               p.sendMessage(TextUtil.mm("<red>Hold a Nova rod first."));
            } else {
               RodManager.UpgradeResult result = this.plugin.rods().upgrade(p, inHand);
               switch (result) {
                  case OK:
                  default:
                     break;
                  case NOT_MAX_LEVEL:
                     p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.upgrade-fail-level")));
                     break;
                  case NO_UPGRADE_PATH:
                  case UNKNOWN_TARGET:
                     p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.upgrade-fail-none")));
                     break;
                  case NO_PERMISSION:
                     p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.upgrade-fail-perm")));
                     break;
                  case INSUFFICIENT_TOKENS:
                     Map<String, String> ph = new HashMap<>();
                     ph.put("amount", String.valueOf(r.def().upgradeCost()));
                     p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.upgrade-fail-cost"), ph));
               }
            }
         }
      } else {
         sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
      }
   }

   private void cmdSkin(CommandSender sender, String label, String[] args) {
      if (!(sender instanceof Player p)) {
         sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
      } else if (!p.hasPermission("novafishing.skin")) {
         this.deny(sender);
      } else if (args.length >= 2) {
         ItemStack inHand = p.getInventory().getItemInMainHand();
         RodInstance r = this.plugin.rods().read(inHand);
         if (r == null) {
            p.sendMessage(TextUtil.mm("<red>Hold a Nova rod first."));
         } else {
            String skinId = args[1];
            if (!skinId.equalsIgnoreCase("clear") && !skinId.equalsIgnoreCase("none")) {
               if (this.plugin.rods().setSkin(p, inHand, skinId)) {
                  Map<String, String> ph = new HashMap<>();
                  ph.put("skin", skinId);
                  p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.skin-applied"), ph));
               } else {
                  p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.skin-bad")));
               }
            } else {
               this.plugin.rods().setSkin(p, inHand, null);
               p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.skin-cleared")));
            }
         }
      } else {
         ItemStack inHand = p.getInventory().getItemInMainHand();
         RodInstance r = this.plugin.rods().read(inHand);
         if (r == null) {
            p.sendMessage(TextUtil.mm("<red>Hold a Nova rod first."));
         } else {
            p.sendMessage(TextUtil.mm("<gold>Available skins for <yellow>" + r.def().id() + "<gold>:"));

            for (RodDef.RodSkin sk : this.plugin.rods().availableSkins(r.def()).values()) {
               p.sendMessage(TextUtil.mm(" <yellow>" + sk.id() + " <gray>- " + sk.displayName()));
            }

            p.sendMessage(TextUtil.mm("<gray>Apply with <yellow>/" + label + " skin <id></yellow> or <yellow>clear"));
         }
      }
   }

   private void cmdChallenges(CommandSender sender) {
      if (sender instanceof Player p) {
         if (!p.hasPermission("novafishing.challenges")) {
            this.deny(sender);
         } else {
            ChallengeGUI.open(this.plugin, p);
         }
      } else {
         sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
      }
   }

   private void cmdStats(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.stats") && !sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else {
         OfflinePlayer target;
         if (args.length >= 2) {
            target = Bukkit.getOfflinePlayer(args[1]);
         } else {
            if (!(sender instanceof Player ps)) {
               sender.sendMessage(TextUtil.mm("<red>/" + label + " stats <player>"));
               return;
            }

            target = ps;
         }

         DatabaseManager.PlayerStats st = this.plugin.db().getStats(target.getUniqueId());
         Map<String, String> ph = new HashMap<>();
         ph.put("player", target.getName() == null ? "?" : target.getName());
         sender.sendMessage(TextUtil.mm(this.plugin.configs().rawMessage("stats.header"), ph));
         sender.sendMessage(TextUtil.mm(this.line("Tokens", String.valueOf(st.tokens()))));
         sender.sendMessage(TextUtil.mm(this.line("Total XP", String.valueOf(st.totalXp()))));
         sender.sendMessage(TextUtil.mm(this.line("Total catches", String.valueOf(st.totalCatches()))));
         sender.sendMessage(TextUtil.mm(this.line("Maxed rod", st.highestMaxRod() == null ? "—" : st.highestMaxRod())));
         sender.sendMessage(TextUtil.mm("<gold>Catches by tier:"));

         for (RewardTier t : RewardTier.values()) {
            long n = st.tierCatches().getOrDefault(t, Long.valueOf(0L));
            sender.sendMessage(TextUtil.mm("  " + this.plugin.rewards().tierLabel(t) + " <dark_gray>- <yellow>" + n));
         }

         if (!st.prestige().isEmpty()) {
            sender.sendMessage(TextUtil.mm("<gold>Highest prestige:"));

            for (Entry<String, Integer> e : st.prestige().entrySet()) {
               sender.sendMessage(TextUtil.mm("  <yellow>" + e.getKey() + " <gray>- <gold>" + e.getValue() + "★"));
            }
         }
      }
   }

   private String line(String label, String value) {
      return this.plugin.configs().rawMessage("stats.line").replace("<label>", label).replace("<value>", value);
   }

   private void cmdWordCheck(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else if (args.length < 2) {
         sender.sendMessage(TextUtil.mm("<gold>Word-check (anti-AFK):"));
         sender.sendMessage(
            TextUtil.mm(
               "<gray> enabled: <yellow>"
                  + this.plugin.wordCheck().enabled()
                  + " <gray>interval: <yellow>"
                  + this.plugin.wordCheck().intervalMinutes()
                  + "m <gray>current: <yellow>"
                  + (this.plugin.wordCheck().currentWord() == null ? "—" : this.plugin.wordCheck().currentWord())
            )
         );
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " wordcheck interval <minutes>"));
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " wordcheck enable|disable"));
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " wordcheck now <gray>(fire a check immediately)"));
      } else {
         String var4 = args[1].toLowerCase();
         switch (var4) {
            case "interval":
               if (args.length < 3) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " wordcheck interval <minutes>"));
                  return;
               }

               long minutes;
               try {
                  minutes = Long.parseLong(args[2]);
               } catch (NumberFormatException var9) {
                  sender.sendMessage(TextUtil.mm("<red>Minutes must be a positive integer."));
                  return;
               }

               if (minutes < 1L) {
                  sender.sendMessage(TextUtil.mm("<red>Minutes must be at least 1."));
                  return;
               }

               this.plugin.wordCheck().setIntervalMinutes(minutes);
               sender.sendMessage(TextUtil.mm("<green>word-check interval = <yellow>" + minutes + "m"));
               break;
            case "enable":
            case "on":
               this.plugin.wordCheck().setEnabled(true);
               sender.sendMessage(TextUtil.mm("<green>word-check <yellow>enabled<green>."));
               break;
            case "disable":
            case "off":
               this.plugin.wordCheck().setEnabled(false);
               sender.sendMessage(TextUtil.mm("<green>word-check <yellow>disabled<green>."));
               break;
            case "now":
               this.plugin.wordCheck().broadcast();
               sender.sendMessage(
                  TextUtil.mm(
                     "<green>Broadcast a fresh challenge. Current word: <yellow>"
                        + (this.plugin.wordCheck().currentWord() == null ? "—" : this.plugin.wordCheck().currentWord())
                  )
               );
               break;
            default:
               sender.sendMessage(
                  TextUtil.mm("<red>Unknown action. Use <yellow>interval<red>, <yellow>enable<red>, <yellow>disable<red>, or <yellow>now<red>.")
               );
         }
      }
   }

   private void cmdTournament(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else if (args.length < 2) {
         sender.sendMessage(TextUtil.mm("<gold>Tournament:"));
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " tournament start <minutes> [ANY_CATCH|TIER_WEIGHTED]"));
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " tournament stop"));
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " tournament status"));
      } else {
         String var4 = args[1].toLowerCase();
         switch (var4) {
            case "start":
               if (this.plugin.tournament().isActive()) {
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("tournament.already-running")));
                  return;
               }

               long minutes = 30L;
               if (args.length >= 3) {
                  try {
                     minutes = Math.max(1L, Long.parseLong(args[2]));
                  } catch (Exception var11) {
                  }
               }

               TournamentManager.Type type = TournamentManager.Type.TIER_WEIGHTED;
               if (args.length >= 4) {
                  try {
                     type = TournamentManager.Type.valueOf(args[3].toUpperCase());
                  } catch (Exception var10) {
                  }
               }

               this.plugin.tournament().start(minutes, type);
               break;
            case "stop":
               if (!this.plugin.tournament().isActive()) {
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("tournament.not-running")));
                  return;
               }

               this.plugin.tournament().stop();
               break;
            case "status":
               if (!this.plugin.tournament().isActive()) {
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("tournament.not-running")));
                  return;
               }

               long sec = this.plugin.tournament().timeRemainingMs() / 1000L;
               sender.sendMessage(
                  TextUtil.mm(
                     "<gold>Tournament: <yellow>"
                        + this.plugin.tournament().type()
                        + " <gold>| <yellow>"
                        + sec / 60L
                        + "m "
                        + sec % 60L
                        + "s left <gold>| <yellow>"
                        + this.plugin.tournament().scores().size()
                        + " participants"
                  )
               );
         }
      }
   }

   private void cmdLbExclude(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.admin")) {
         this.deny(sender);
      } else {
         String path = "leaderboard.exclude-players";
         List<String> current = new ArrayList<>(this.plugin.getConfig().getStringList("leaderboard.exclude-players"));
         if (args.length < 2) {
            sender.sendMessage(TextUtil.mm("<gold>Leaderboard exclusions:"));
            sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " lbexclude list"));
            sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " lbexclude add <player>"));
            sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " lbexclude remove <player>"));
            sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " lbexclude clear"));
         } else {
            String var6 = args[1].toLowerCase();
            switch (var6) {
               case "list":
                  if (current.isEmpty()) {
                     sender.sendMessage(TextUtil.mm("<gray>No leaderboard exclusions."));
                     return;
                  }

                  sender.sendMessage(TextUtil.mm("<gold>Excluded from leaderboards (<yellow>" + current.size() + "<gold>):"));

                  for (String n : current) {
                     sender.sendMessage(TextUtil.mm(" <gray>- <yellow>" + n));
                  }
                  break;
               case "add":
                  if (args.length < 3) {
                     sender.sendMessage(TextUtil.mm("<red>/" + label + " lbexclude add <player>"));
                     return;
                  }

                  String name = args[2];
                  if (current.stream().anyMatch(s -> s.equalsIgnoreCase(name))) {
                     sender.sendMessage(TextUtil.mm("<yellow>" + name + " <gray>is already excluded."));
                     return;
                  }

                  current.add(name);
                  this.plugin.getConfig().set("leaderboard.exclude-players", current);
                  this.plugin.saveConfig();
                  sender.sendMessage(TextUtil.mm("<green>Excluded <yellow>" + name + " <green>from leaderboards."));
                  break;
               case "remove":
               case "del":
                  if (args.length < 3) {
                     sender.sendMessage(TextUtil.mm("<red>/" + label + " lbexclude remove <player>"));
                     return;
                  }

                  String name = args[2];
                  if (!current.removeIf(s -> s.equalsIgnoreCase(name))) {
                     sender.sendMessage(TextUtil.mm("<yellow>" + name + " <gray>is not on the exclusion list."));
                     return;
                  }

                  this.plugin.getConfig().set("leaderboard.exclude-players", current);
                  this.plugin.saveConfig();
                  sender.sendMessage(TextUtil.mm("<green>Removed <yellow>" + name + " <green>from leaderboard exclusions."));
                  break;
               case "clear":
                  if (current.isEmpty()) {
                     sender.sendMessage(TextUtil.mm("<gray>No leaderboard exclusions to clear."));
                     return;
                  }

                  int n = current.size();
                  this.plugin.getConfig().set("leaderboard.exclude-players", new ArrayList());
                  this.plugin.saveConfig();
                  sender.sendMessage(TextUtil.mm("<green>Cleared leaderboard exclusions (<yellow>" + n + "<green> removed)."));
                  break;
               default:
                  sender.sendMessage(TextUtil.mm("<red>Unknown action. Use <yellow>list<red>, <yellow>add<red>, <yellow>remove<red>, or <yellow>clear<red>."));
            }
         }
      }
   }

   private void cmdHolo(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.hologram")) {
         this.deny(sender);
      } else if (args.length < 2) {
         this.sendHoloHelp(sender, label);
      } else {
         String action = args[1].toLowerCase();
         switch (action) {
            case "create":
               if (!(sender instanceof Player p)) {
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
                  return;
               }

               if (args.length < 3) {
                  p.sendMessage(TextUtil.mm("<red>/" + label + " holo create <id>"));
                  return;
               }

               String id = args[2];
               Hologram h = this.plugin
                  .holograms()
                  .create(id, p.getLocation(), List.of("<aqua><bold>" + id, "<gray>(use <yellow>/" + label + " holo setline " + id + " 1 ...<gray> to edit)"));
               if (h == null) {
                  p.sendMessage(TextUtil.mm("<red>A hologram with that id already exists."));
                  return;
               }

               p.sendMessage(
                  TextUtil.mm(
                     "<green>Created hologram <yellow>" + id + "<green>. Edit lines with <yellow>/" + label + " holo setline " + id + " <i> <text></yellow>."
                  )
               );
               break;
            case "move":
               if (!(sender instanceof Player p)) {
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
                  return;
               }

               if (args.length < 3) {
                  p.sendMessage(TextUtil.mm("<red>/" + label + " holo move <id>"));
                  return;
               }

               if (!this.plugin.holograms().move(args[2], p.getLocation())) {
                  p.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + args[2]));
                  return;
               }

               p.sendMessage(TextUtil.mm("<green>Moved <yellow>" + args[2] + "<green> here."));
               break;
            case "delete":
            case "remove":
               if (args.length < 3) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " holo delete <id>"));
                  return;
               }

               if (!this.plugin.holograms().delete(args[2])) {
                  sender.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + args[2]));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<green>Deleted hologram <yellow>" + args[2]));
               break;
            case "list":
               Collection<Hologram> all = this.plugin.holograms().all();
               if (all.isEmpty()) {
                  sender.sendMessage(TextUtil.mm("<gray>No holograms."));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<gold>Holograms (<yellow>" + all.size() + "<gold>):"));

               for (Hologram h : all) {
                  sender.sendMessage(
                     TextUtil.mm(
                        " <yellow>"
                           + h.id()
                           + " <dark_gray>- <gray>"
                           + h.worldName()
                           + " <dark_gray>(<gray>"
                           + (int)h.x()
                           + ","
                           + (int)h.y()
                           + ","
                           + (int)h.z()
                           + "<dark_gray>) <gray>lines=<yellow>"
                           + h.lines().size()
                     )
                  );
               }
               break;
            case "info":
               if (args.length < 3) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " holo info <id>"));
                  return;
               }

               Hologram h = this.plugin.holograms().get(args[2]);
               if (h == null) {
                  sender.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + args[2]));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<gold>Hologram <yellow>" + h.id()));
               sender.sendMessage(
                  TextUtil.mm("<gray>world=<yellow>" + h.worldName() + " <gray>pos=<yellow>" + String.format("%.2f, %.2f, %.2f", h.x(), h.y(), h.z()))
               );
               sender.sendMessage(TextUtil.mm("<gray>line-height=<yellow>" + h.lineHeight() + " <gray>interval=<yellow>" + h.updateIntervalTicks() + "t"));

               for (int i = 0; i < h.lines().size(); i++) {
                  sender.sendMessage(TextUtil.mm("<gray>" + i + ": <white>" + h.lines().get(i)));
               }
               break;
            case "setline":
               if (args.length < 5) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " holo setline <id> <index> <text...>"));
                  return;
               }

               int idxx;
               try {
                  idxx = Integer.parseInt(args[3]);
               } catch (NumberFormatException var13) {
                  sender.sendMessage(TextUtil.mm("<red>Index must be a number."));
                  return;
               }

               String text = joinFrom(args, 4);
               if (!this.plugin.holograms().setLine(args[2], idxx, text)) {
                  sender.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + args[2]));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<green>Set line <yellow>" + idxx + "<green> on <yellow>" + args[2]));
               break;
            case "addline":
               if (args.length < 4) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " holo addline <id> <text...>"));
                  return;
               }

               String text = joinFrom(args, 3);
               if (!this.plugin.holograms().addLine(args[2], text)) {
                  sender.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + args[2]));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<green>Added a line to <yellow>" + args[2]));
               break;
            case "removeline":
            case "delline":
               if (args.length < 4) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " holo removeline <id> <index>"));
                  return;
               }

               int idx;
               try {
                  idx = Integer.parseInt(args[3]);
               } catch (NumberFormatException var12) {
                  sender.sendMessage(TextUtil.mm("<red>Index must be a number."));
                  return;
               }

               if (!this.plugin.holograms().removeLine(args[2], idx)) {
                  sender.sendMessage(TextUtil.mm("<red>Bad id or index."));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<green>Removed line <yellow>" + idx + "<green> from <yellow>" + args[2]));
               break;
            case "lineheight":
               if (args.length < 4) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " holo lineheight <id> <value>"));
                  return;
               }

               double v;
               try {
                  v = Double.parseDouble(args[3]);
               } catch (NumberFormatException var11) {
                  sender.sendMessage(TextUtil.mm("<red>Value must be a number."));
                  return;
               }

               if (!this.plugin.holograms().setLineHeight(args[2], v)) {
                  sender.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + args[2]));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<green>line-height = <yellow>" + v));
               break;
            case "interval":
               if (args.length < 4) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " holo interval <id> <ticks>"));
                  return;
               }

               int t;
               try {
                  t = Integer.parseInt(args[3]);
               } catch (NumberFormatException var10) {
                  sender.sendMessage(TextUtil.mm("<red>Ticks must be an integer."));
                  return;
               }

               if (!this.plugin.holograms().setInterval(args[2], t)) {
                  sender.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + args[2]));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<green>update-interval-ticks = <yellow>" + Math.max(5, t)));
               break;
            case "reload":
               if (args.length < 3) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " holo reload <id>"));
                  return;
               }

               if (!this.plugin.holograms().reload(args[2])) {
                  sender.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + args[2]));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<green>Respawned <yellow>" + args[2]));
               break;
            default:
               this.sendHoloHelp(sender, label);
         }
      }
   }

   private void cmdLbHologram(CommandSender sender, String label, String[] args) {
      if (!sender.hasPermission("novafishing.hologram")) {
         this.deny(sender);
      } else if (args.length < 2) {
         sender.sendMessage(TextUtil.mm("<gold>Leaderboard hologram:"));
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " lbhologram create [id]"));
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " lbhologram move [id]"));
         sender.sendMessage(TextUtil.mm(" <yellow>/" + label + " lbhologram delete [id]"));
         sender.sendMessage(TextUtil.mm("<gray>Defaults pulled from <yellow>config.yml<gray>'s <yellow>holograms.leaderboard<gray> section."));
      } else {
         String action = args[1].toLowerCase();
         String id = args.length >= 3 ? args[2] : this.plugin.holograms().defaultLeaderboardId();
         switch (action) {
            case "create":
               if (!(sender instanceof Player p)) {
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
                  return;
               }

               Hologram h = this.plugin.holograms().create(id, p.getLocation(), this.plugin.holograms().buildLeaderboardLines());
               if (h == null) {
                  p.sendMessage(
                     TextUtil.mm(
                        "<red>A hologram with id <yellow>" + id + "<red> already exists. Try <yellow>/" + label + " holo delete " + id + "</yellow> first."
                     )
                  );
                  return;
               }

               p.sendMessage(TextUtil.mm("<green>Created leaderboard hologram <yellow>" + id + "<green>."));
               p.sendMessage(
                  TextUtil.mm(
                     "<gray>Edit lines with <yellow>/"
                        + label
                        + " holo setline "
                        + id
                        + " <i> <text></yellow> or restyle via <yellow>config.yml</yellow> + recreate."
                  )
               );
               break;
            case "move":
               if (!(sender instanceof Player p)) {
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
                  return;
               }

               if (!this.plugin.holograms().move(id, p.getLocation())) {
                  p.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + id));
                  return;
               }

               p.sendMessage(TextUtil.mm("<green>Moved <yellow>" + id + "<green> here."));
               break;
            case "delete":
            case "remove":
               if (!this.plugin.holograms().delete(id)) {
                  sender.sendMessage(TextUtil.mm("<red>No hologram with id <yellow>" + id));
                  return;
               }

               sender.sendMessage(TextUtil.mm("<green>Deleted <yellow>" + id));
               break;
            default:
               sender.sendMessage(TextUtil.mm("<red>Unknown action. Use <yellow>create<red>, <yellow>move<red>, or <yellow>delete<red>."));
         }
      }
   }

   private void sendHoloHelp(CommandSender s, String label) {
      s.sendMessage(TextUtil.mm("<gold>Holograms:"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo create <id>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo move <id>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo delete <id>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo list"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo info <id>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo setline <id> <i> <text...>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo addline <id> <text...>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo removeline <id> <i>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo lineheight <id> <value>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo interval <id> <ticks>"));
      s.sendMessage(TextUtil.mm(" <yellow>/" + label + " holo reload <id>"));
      s.sendMessage(TextUtil.mm("<gray>One-shot rod leaderboard: <yellow>/" + label + " lbhologram create"));
   }

   private static String joinFrom(String[] args, int from) {
      StringBuilder sb = new StringBuilder();

      for (int i = from; i < args.length; i++) {
         if (i > from) {
            sb.append(' ');
         }

         sb.append(args[i]);
      }

      return sb.toString();
   }

   private void deny(CommandSender s) {
      s.sendMessage(TextUtil.mm(this.plugin.configs().message("no-permission")));
   }

   private void sendHelp(CommandSender s, String label) {
      s.sendMessage(TextUtil.mm("<gradient:#FF6A00:#FFD200><bold>NovaFishing</bold></gradient>"));
      s.sendMessage(TextUtil.mm("<aqua>/fish <gray>or <aqua>/cast <gray>— mobile-friendly cast/reel button"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " give <player> <rodId> [lvl] [xp]"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " rewards"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " prestige <gray>(while holding a maxed rod)"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " upgrade <gray>(advance a maxed rod to the next tier)"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " skin [skin|clear]"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " challenges"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " stats [player]"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " wordcheck <interval|enable|disable|now>"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " tournament <start|stop|status>"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " lbexclude <list|add|remove|clear> [player]"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " holo <create|move|delete|list|info|setline|...>"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " lbhologram <create|move|delete> [id]"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " setpos1 <name> | setpos2 <name> | clearpos <name>"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " voidlist | info | top | reload | togglelava"));
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 1) {
         List<String> opts = new ArrayList<>(
            List.of(
               "help",
               "reload",
               "give",
               "setpos1",
               "setpos2",
               "clearpos",
               "rewards",
               "info",
               "top",
               "togglelava",
               "voidlist",
               "prestige",
               "upgrade",
               "skin",
               "challenges",
               "stats",
               "wordcheck",
               "tournament",
               "resetconfig",
               "lbexclude",
               "holo",
               "lbhologram"
            )
         );
         return opts.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
      } else if (args.length == 2 && args[0].equalsIgnoreCase("resetconfig")) {
         List<String> out = new ArrayList<>(ConfigManager.resettableNames());
         out.add("all");
         return out.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
      } else {
         if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("stats")) {
               return Bukkit.getOnlinePlayers()
                  .stream()
                  .<String>map(Player::getName)
                  .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("clearpos")) {
               return this.plugin
                  .voids()
                  .regions()
                  .stream()
                  .map(rx -> rx.name)
                  .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("tournament")) {
               return List.of("start", "stop", "status");
            }

            if (args[0].equalsIgnoreCase("wordcheck")) {
               return Stream.of("interval", "enable", "disable", "now").filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("holo") || args[0].equalsIgnoreCase("hologram")) {
               return Stream.of("create", "move", "delete", "list", "info", "setline", "addline", "removeline", "lineheight", "interval", "reload")
                  .filter(s -> s.startsWith(args[1].toLowerCase()))
                  .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("lbhologram")) {
               return Stream.of("create", "move", "delete").filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("lbexclude")) {
               return Stream.of("list", "add", "remove", "clear").filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("skin") && sender instanceof Player ps) {
               RodInstance r = this.plugin.rods().read(ps.getInventory().getItemInMainHand());
               if (r != null) {
                  List<String> out = new ArrayList<>(r.def().skins().keySet());
                  out.add("clear");
                  return out.stream().filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
               }
            }
         }

         if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return this.plugin.rods().getRodIds().stream().filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase())).collect(Collectors.toList());
         } else if (args.length == 3 && (args[0].equalsIgnoreCase("holo") || args[0].equalsIgnoreCase("hologram"))) {
            String action = args[1].toLowerCase();
            return action.equals("create")
               ? Collections.emptyList()
               : this.plugin.holograms().all().stream().map(h -> h.id()).filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
         } else if (args.length == 3 && args[0].equalsIgnoreCase("lbhologram")) {
            return this.plugin.holograms().all().stream().map(h -> h.id()).filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
         } else if (args.length == 4 && args[0].equalsIgnoreCase("tournament") && args[1].equalsIgnoreCase("start")) {
            return List.of("ANY_CATCH", "TIER_WEIGHTED");
         } else {
            if (args.length == 3 && args[0].equalsIgnoreCase("lbexclude")) {
               String action = args[1].toLowerCase();
               if (action.equals("add")) {
                  return Bukkit.getOnlinePlayers()
                     .stream()
                     .<String>map(Player::getName)
                     .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                     .collect(Collectors.toList());
               }

               if (action.equals("remove") || action.equals("del")) {
                  return this.plugin
                     .getConfig()
                     .getStringList("leaderboard.exclude-players")
                     .stream()
                     .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                     .collect(Collectors.toList());
               }
            }

            return Collections.emptyList();
         }
      }
   }
}
