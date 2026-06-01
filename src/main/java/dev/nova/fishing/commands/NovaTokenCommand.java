package dev.nova.fishing.commands;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.database.DatabaseManager;
import dev.nova.fishing.token.PhysicalTokenItem;
import dev.nova.fishing.token.ShopCategory;
import dev.nova.fishing.token.ShopItem;
import dev.nova.fishing.util.TextUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class NovaTokenCommand implements CommandExecutor, TabCompleter {
   private final NovaFishing plugin;

   public NovaTokenCommand(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         if (sender instanceof Player p && p.hasPermission("novatoken.use")) {
            long bal = this.plugin.tokens().get(p.getUniqueId());
            Map<String, String> ph = new HashMap<>();
            ph.put("amount", String.valueOf(bal));
            p.sendMessage(TextUtil.mm(this.plugin.configs().message("token.balance"), ph));
            return true;
         }

         this.sendHelp(sender, label);
         return true;
      } else {
         String sub = args[0].toLowerCase();
         switch (sub) {
            case "help":
               this.sendHelp(sender, label);
               break;
            case "balance":
            case "bal": {
               if (args.length < 2) {
                  if (!(sender instanceof Player p)) {
                     sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
                     return true;
                  }

                  long var29 = this.plugin.tokens().get(p.getUniqueId());
                  Map<String, String> ph = new HashMap<>();
                  ph.put("amount", String.valueOf(var29));
                  p.sendMessage(TextUtil.mm(this.plugin.configs().message("token.balance"), ph));
               } else {
                  OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
                  long bal = this.plugin.tokens().get(t.getUniqueId());
                  Map<String, String> ph = new HashMap<>();
                  ph.put("player", t.getName() == null ? args[1] : t.getName());
                  ph.put("amount", String.valueOf(bal));
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("token.balance-other"), ph));
               }
               break;
            }
            case "give": {
               if (!sender.hasPermission("novatoken.admin")) {
                  this.deny(sender);
                  return true;
               }

               if (args.length < 3) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " give <player> <amount>"));
                  return true;
               }

               long amount = this.parseLong(args[2], 0L);
               OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
               this.plugin.tokens().give(t.getUniqueId(), amount, true);
               Map<String, String> ph = new HashMap<>();
               ph.put("amount", String.valueOf(amount));
               ph.put("player", t.getName() == null ? args[1] : t.getName());
               sender.sendMessage(TextUtil.mm(this.plugin.configs().message("token.give"), ph));
               break;
            }
            case "take": {
               if (!sender.hasPermission("novatoken.admin")) {
                  this.deny(sender);
                  return true;
               }

               if (args.length < 3) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " take <player> <amount>"));
                  return true;
               }

               long amount = this.parseLong(args[2], 0L);
               OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
               this.plugin.tokens().take(t.getUniqueId(), amount);
               Map<String, String> ph = new HashMap<>();
               ph.put("amount", String.valueOf(amount));
               ph.put("player", t.getName() == null ? args[1] : t.getName());
               sender.sendMessage(TextUtil.mm(this.plugin.configs().message("token.take"), ph));
               break;
            }
            case "set": {
               if (!sender.hasPermission("novatoken.admin")) {
                  this.deny(sender);
                  return true;
               }

               if (args.length < 3) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " set <player> <amount>"));
                  return true;
               }

               long amount = this.parseLong(args[2], 0L);
               OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
               this.plugin.tokens().set(t.getUniqueId(), amount);
               Map<String, String> ph = new HashMap<>();
               ph.put("amount", String.valueOf(amount));
               ph.put("player", t.getName() == null ? args[1] : t.getName());
               sender.sendMessage(TextUtil.mm(this.plugin.configs().message("token.set"), ph));
               break;
            }
            case "physical":
            case "phys": {
               if (!sender.hasPermission("novatoken.admin")) {
                  this.deny(sender);
                  return true;
               }

               if (args.length < 2) {
                  sender.sendMessage(TextUtil.mm("<red>/" + label + " physical <amount> [player] [stack]"));
                  return true;
               }

               long amount = this.parseLong(args[1], 0L);
               if (amount <= 0L) {
                  sender.sendMessage(TextUtil.mm("<red>Amount must be positive."));
                  return true;
               }

               int stack = 1;
               Player target;
               if (args.length >= 3) {
                  target = Bukkit.getPlayerExact(args[2]);
                  if (target == null) {
                     Map<String, String> nph = new HashMap<>();
                     nph.put("player", args[2]);
                     sender.sendMessage(TextUtil.mm(this.plugin.configs().message("player-not-found"), nph));
                     return true;
                  }

                  if (args.length >= 4) {
                     stack = (int)Math.max(1L, Math.min(64L, this.parseLong(args[3], 1L)));
                  }
               } else {
                  if (!(sender instanceof Player p)) {
                     sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
                     return true;
                  }

                  target = p;
               }

               ItemStack item = PhysicalTokenItem.build(this.plugin, amount, stack);
               HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(new ItemStack[]{item});
               if (!leftover.isEmpty()) {
                  for (ItemStack drop : leftover.values()) {
                     target.getWorld().dropItemNaturally(target.getLocation(), drop);
                  }
               }

               Map<String, String> ph = new HashMap<>();
               ph.put("amount", String.valueOf(amount));
               ph.put("player", target.getName());
               ph.put("stack", String.valueOf(stack));
               sender.sendMessage(TextUtil.mm(this.plugin.configs().message("token.physical-given"), ph));
               break;
            }
            case "shop": {
               if (!(sender instanceof Player p)) {
                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
                  return true;
               }

               if (!p.hasPermission("novatoken.shop")) {
                  this.deny(sender);
                  return true;
               }

               this.plugin.gui().openTokenShop(p);
               break;
            }
            case "setstorelink":
            case "storelink": {
               if (!sender.hasPermission("novatoken.admin")) {
                  this.deny(sender);
                  return true;
               }

               if (args.length < 2) {
                  String current = this.plugin.configs().shop().getString("store-link", "https://novamc.me");
                  sender.sendMessage(TextUtil.mm("<yellow>Current store link: <aqua>" + current));
                  sender.sendMessage(TextUtil.mm("<gray>Usage: <yellow>/" + label + " setstorelink <url>"));
                  return true;
               }

               String url = args[1].trim();
               if (!url.startsWith("http://") && !url.startsWith("https://")) {
                  sender.sendMessage(TextUtil.mm("<red>URL must start with <yellow>http://<red> or <yellow>https://<red>."));
                  return true;
               }

               this.plugin.configs().shop().set("store-link", url);
               this.plugin.configs().saveShopAsync();
               sender.sendMessage(TextUtil.mm("<green>Store link updated to <aqua>" + url));
               break;
            }
            case "top":
            case "baltop":
               this.cmdTop(sender, args);
               break;
            case "earned":
               if (!sender.hasPermission("novatoken.admin")) {
                  this.deny(sender);
                  return true;
               }
               this.cmdEarned(sender, label, args);
               break;
            case "rate":
               if (!sender.hasPermission("novatoken.admin")) {
                  this.deny(sender);
                  return true;
               }
               this.cmdRate(sender, label, args);
               break;
            case "editshop": {
               if (!sender.hasPermission("novatoken.admin")) {
                  this.deny(sender);
                  return true;
               }

               if (args.length == 1) {
                  if (sender instanceof Player p) {
                     this.plugin.gui().openShopEditor(p);
                     return true;
                  }

                  sender.sendMessage(TextUtil.mm(this.plugin.configs().message("players-only")));
                  return true;
               }

               String op = args[1].toLowerCase();
               if (op.equals("newcat")) {
                  if (args.length < 5) {
                     sender.sendMessage(TextUtil.mm("<red>/" + label + " editshop newcat <id> <icon> <slot>"));
                     return true;
                  }

                  Material icon = Material.matchMaterial(args[3]);
                  int slot = (int)this.parseLong(args[4], 10L);
                  this.plugin.shop().createCategory(args[2], "<yellow>" + args[2], icon == null ? Material.PAPER : icon, slot);
                  sender.sendMessage(TextUtil.mm("<green>Created category <yellow>" + args[2]));
               } else if (op.equals("addcmd")) {
                  if (args.length < 5) {
                     sender.sendMessage(TextUtil.mm("<red>/" + label + " editshop addcmd <category> <cost> <cmd…>"));
                     return true;
                  }

                  ShopCategory cat = this.plugin.shop().getCategory(args[2]);
                  if (cat == null) {
                     sender.sendMessage(TextUtil.mm("<red>Unknown category."));
                     return true;
                  }

                  long cost = this.parseLong(args[3], 100L);
                  StringBuilder cmd = new StringBuilder();

                  for (int i = 4; i < args.length; i++) {
                     if (cmd.length() > 0) {
                        cmd.append(' ');
                     }

                     cmd.append(args[i]);
                  }

                  ShopItem item = new ShopItem(
                     "cmd_" + System.currentTimeMillis(),
                     ShopItem.Type.COMMAND,
                     cost,
                     null,
                     null,
                     new ArrayList<>(List.of(cmd.toString())),
                     "<yellow>" + cmd,
                     Material.COMMAND_BLOCK,
                     new ArrayList<>(),
                     null
                  );
                  this.plugin.shop().addItem(cat, item);
                  sender.sendMessage(TextUtil.mm("<green>Added command reward to <yellow>" + cat.id));
               }
               break;
            }
            default:
               sender.sendMessage(TextUtil.mm(this.plugin.configs().message("unknown-command").replace("<label>", label)));
         }

         return true;
      }
   }

   private long parseLong(String s, long d) {
      try {
         return Long.parseLong(s);
      } catch (Exception var5) {
         return d;
      }
   }

   private Set<String> excludedNames() {
      List<String> raw = this.plugin.getConfig().getStringList("leaderboard.exclude-players");
      if (raw.isEmpty()) {
         return Set.of();
      }
      Set<String> out = new HashSet<>(raw.size());
      for (String n : raw) {
         if (n != null && !n.isBlank()) {
            out.add(n.trim().toLowerCase(Locale.ROOT));
         }
      }
      return out;
   }

   private void cmdTop(CommandSender sender, String[] args) {
      int perPage = 10;
      int page = args.length >= 2 ? Math.max(1, (int) this.parseLong(args[1], 1L)) : 1;
      int limit = perPage * page;
      List<DatabaseManager.TopTokensEntry> entries = this.plugin.db().topByTokens(limit, this.excludedNames());
      sender.sendMessage(TextUtil.mm("<gradient:#FFD700:#FFAA00><bold>Top Token Balances</bold></gradient>"));
      int start = (page - 1) * perPage;
      if (start >= entries.size()) {
         sender.sendMessage(TextUtil.mm("<gray>No data on page <yellow>" + page + "<gray>."));
         return;
      }
      int rank = start + 1;
      for (int i = start; i < entries.size(); i++) {
         DatabaseManager.TopTokensEntry e = entries.get(i);
         sender.sendMessage(
            TextUtil.mm(" <gray>" + rank + ". <yellow>" + (e.name() == null ? "?" : e.name()) + " <dark_gray>- <gold>" + e.tokens() + " tokens")
         );
         rank++;
      }
      if (entries.size() >= limit) {
         sender.sendMessage(TextUtil.mm("<dark_gray>Next page: <gray>/novatoken top " + (page + 1)));
      }
   }

   private void cmdEarned(CommandSender sender, String label, String[] args) {
      if (args.length < 2) {
         sender.sendMessage(TextUtil.mm("<red>/" + label + " earned <player>"));
         return;
      }
      OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
      long lifetime = this.plugin.db().getFishingTokensEarned(t.getUniqueId());
      long bal = this.plugin.tokens().get(t.getUniqueId());
      long day = this.plugin.db().getEarningsSince(t.getUniqueId(), System.currentTimeMillis() - 86400000L);
      long week = this.plugin.db().getEarningsSince(t.getUniqueId(), System.currentTimeMillis() - 7L * 86400000L);
      String name = t.getName() == null ? args[1] : t.getName();
      sender.sendMessage(TextUtil.mm("<gradient:#FFD700:#FFAA00><bold>Fishing tokens — " + name + "</bold></gradient>"));
      sender.sendMessage(TextUtil.mm("<gray>Current balance: <gold>" + bal));
      sender.sendMessage(TextUtil.mm("<gray>Earned (24h): <gold>" + day));
      sender.sendMessage(TextUtil.mm("<gray>Earned (7d): <gold>" + week));
      sender.sendMessage(TextUtil.mm("<gray>Earned (lifetime, since tracking added): <gold>" + lifetime));
   }

   private void cmdRate(CommandSender sender, String label, String[] args) {
      long hours = args.length >= 2 ? Math.max(1L, this.parseLong(args[1], 24L)) : 24L;
      long sinceTs = System.currentTimeMillis() - hours * 3600000L;
      List<DatabaseManager.TopEarningsEntry> entries = this.plugin.db().topByEarningsSince(sinceTs, 10, this.excludedNames());
      sender.sendMessage(
         TextUtil.mm("<gradient:#FFD700:#FFAA00><bold>Token earning rate — last " + hours + "h</bold></gradient>")
      );
      if (entries.isEmpty()) {
         sender.sendMessage(TextUtil.mm("<gray>No tokens earned via fishing in this window."));
         return;
      }
      long total = 0L;
      int i = 1;
      for (DatabaseManager.TopEarningsEntry e : entries) {
         double perHour = (double) e.earned() / (double) hours;
         sender.sendMessage(
            TextUtil.mm(
               " <gray>" + i + ". <yellow>" + (e.name() == null ? "?" : e.name())
                  + " <dark_gray>- <gold>" + e.earned() + " <gray>(" + String.format("%.1f", perHour) + "/h)"
            )
         );
         total += e.earned();
         i++;
      }
      double totalPerHour = (double) total / (double) hours;
      sender.sendMessage(
         TextUtil.mm("<dark_gray>Top 10 total: <gold>" + total + " <gray>(" + String.format("%.1f", totalPerHour) + "/h combined)")
      );
   }

   private boolean parseToggle(String s, boolean fallback) {
      if (s == null) {
         return fallback;
      } else {
         String var3 = s.toLowerCase();

         return switch (var3) {
            case "on", "true", "yes", "enable", "enabled" -> true;
            case "off", "false", "no", "disable", "disabled" -> false;
            default -> fallback;
         };
      }
   }

   private void deny(CommandSender s) {
      s.sendMessage(TextUtil.mm(this.plugin.configs().message("no-permission")));
   }

   private void sendHelp(CommandSender s, String label) {
      s.sendMessage(TextUtil.mm("<gradient:#FFD700:#FFAA00><bold>Nova Tokens</bold></gradient>"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " balance [player]"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " top [page]"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " give <player> <amount>"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " take <player> <amount>"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " set <player> <amount>"));
      s.sendMessage(TextUtil.mm("<gray>/" + label + " shop"));
      if (s.hasPermission("novatoken.admin")) {
         s.sendMessage(TextUtil.mm("<gray>/" + label + " earned <player>"));
         s.sendMessage(TextUtil.mm("<gray>/" + label + " rate [hours]"));
         s.sendMessage(TextUtil.mm("<gray>/" + label + " editshop [newcat|addcmd]"));
         s.sendMessage(TextUtil.mm("<gray>/" + label + " setstorelink <url>"));
         s.sendMessage(TextUtil.mm("<gray>/" + label + " physical <amount> [player] [stack]"));
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 1) {
         return List.of("balance", "top", "give", "take", "set", "shop", "earned", "rate", "editshop", "setstorelink", "physical", "help")
            .stream()
            .filter(s -> s.startsWith(args[0].toLowerCase()))
            .collect(Collectors.toList());
      } else if (args.length == 2 && args[0].equalsIgnoreCase("earned")) {
         return Bukkit.getOnlinePlayers()
            .stream()
            .<String>map(Player::getName)
            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
      } else if (args.length == 3 && args[0].equalsIgnoreCase("physical")) {
         return Bukkit.getOnlinePlayers()
            .stream()
            .<String>map(Player::getName)
            .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
            .collect(Collectors.toList());
      } else if (args.length == 2 && args[0].equalsIgnoreCase("setstorelink")) {
         String current = this.plugin.configs().shop().getString("store-link", "https://novamc.me");
         return List.of(current).stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).collect(Collectors.toList());
      } else if (args.length != 2
         || !args[0].equalsIgnoreCase("give") && !args[0].equalsIgnoreCase("take") && !args[0].equalsIgnoreCase("set") && !args[0].equalsIgnoreCase("balance")) {
         return args.length == 2 && args[0].equalsIgnoreCase("editshop")
            ? List.of("newcat", "addcmd").stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList())
            : Collections.emptyList();
      } else {
         return Bukkit.getOnlinePlayers()
            .stream()
            .<String>map(Player::getName)
            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
            .collect(Collectors.toList());
      }
   }
}
