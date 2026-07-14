package dev.nova.fishing.gui;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.reward.Reward;
import dev.nova.fishing.reward.RewardManager;
import dev.nova.fishing.reward.RewardTier;
import dev.nova.fishing.util.ItemBuilder;
import dev.nova.fishing.util.TextUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class StaffRewardGUI {
   private StaffRewardGUI() {
   }

   public static void open(NovaFishing plugin, Player p) {
      StaffRewardGUI.TierBrowser h = new StaffRewardGUI.TierBrowser(plugin, p.getUniqueId());
      Inventory inv = Bukkit.createInventory(h, 27, TextUtil.mm("<dark_gray>Reward Tiers"));
      h.inv = inv;
      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16};
      RewardTier[] tiers = RewardTier.values();

      for (int i = 0; i < tiers.length; i++) {
         RewardTier t = tiers[i];
         int weightSum = plugin.rewards().getRewards(t).stream().mapToInt(r -> r.weight).sum();
         ItemBuilder b = new ItemBuilder(tierIcon(t));
         b.name(plugin.rewards().tierLabel(t));
         List<String> lore = new ArrayList<>();
         lore.add("<gray>Entries: <white>" + plugin.rewards().getRewards(t).size());
         lore.add("<gray>Total weight: <white>" + weightSum);
         lore.add("");
         lore.add("<yellow>Click <gray>to edit this tier.");
         b.lore(lore);
         inv.setItem(slots[i], b.build());
      }

      inv.setItem(
         22,
         new ItemBuilder(Material.NETHER_STAR)
            .name("<gradient:#FFD700:#FF55FF><bold>Jackpots</bold></gradient>")
            .lore(List.of("<gray>Edit bonus jackpot pools that roll", "<gray>on top of normal catches.", "", "<yellow>Click <gray>to open."))
            .build()
      );
      p.openInventory(inv);
   }

   static void openTier(NovaFishing plugin, Player p, RewardTier tier) {
      StaffRewardGUI.TierEditor h = new StaffRewardGUI.TierEditor(plugin, p.getUniqueId(), tier);
      Inventory inv = Bukkit.createInventory(h, 54, TextUtil.mm("<dark_gray>Edit: " + plugin.rewards().tierLabel(tier)));
      h.inv = inv;
      List<Reward> list = plugin.rewards().getRewards(tier);

      for (int i = 0; i < list.size() && i < 45; i++) {
         Reward r = list.get(i);
         ItemBuilder b = rewardIcon(plugin, r);
         List<String> lore = new ArrayList<>();
         lore.add("<gray>Type: <yellow>" + r.type.name());
         lore.add("<gray>Weight: <yellow>" + r.weight);
         lore.add("<gray>Amount: <yellow>" + r.min + "–" + r.max);
         if (r.nexoId != null) {
            lore.add("<gray>Nexo item: <aqua>" + r.nexoId);
         }

         if (r.type == Reward.Type.COMMAND && !r.commands.isEmpty()) {
            lore.add("<gray>Cmd: <white>" + r.commands.get(0));
         }

         lore.add("");
         lore.add("<gray>Left-click: <yellow>weight +5");
         lore.add("<gray>Shift-left: <yellow>weight +25");
         lore.add("<gray>Right-click: <red>delete");
         b.lore(lore);
         inv.setItem(i, b.build());
         h.byIndex.put(i, i);
      }

      inv.setItem(
         49,
         new ItemBuilder(Material.LIME_DYE)
            .name("<green>Add Item from Cursor")
            .lore(List.of("<gray>Pick an item up onto your cursor,", "<gray>then click here to add it as a", "<gray>new ITEM reward (weight=10)."))
            .build()
      );
      inv.setItem(
         48,
         new ItemBuilder(Material.EMERALD)
            .name("<green>Add Held Item")
            .lore(List.of("<gray>Adds the item in your main hand", "<gray>as a new ITEM reward (weight=10).", "<gray>You won't lose the held item."))
            .build()
      );
      inv.setItem(
         47,
         new ItemBuilder(Material.CHEST)
            .name("<green>Add Whole Inventory")
            .lore(
               List.of(
                  "<gray>Adds every item in your inventory",
                  "<gray>as separate ITEM rewards (weight=10).",
                  "<gray>You won't lose your items.",
                  "<gray>Nova rods are skipped."
               )
            )
            .build()
      );
      inv.setItem(
         50,
         new ItemBuilder(Material.COMMAND_BLOCK)
            .name("<gold>Add Command Reward")
            .lore(List.of("<gray>Click and you'll be prompted", "<gray>in chat for the command + weight."))
            .build()
      );
      inv.setItem(45, new ItemBuilder(Material.ARROW).name("<gray>Back").build());
      p.openInventory(inv);
   }

   static void openJackpots(NovaFishing plugin, Player p) {
      StaffRewardGUI.JackpotBrowser h = new StaffRewardGUI.JackpotBrowser(plugin, p.getUniqueId());
      Inventory inv = Bukkit.createInventory(h, 27, TextUtil.mm("<dark_gray>Jackpot Pools"));
      h.inv = inv;
      List<String> names = new ArrayList<>(plugin.rewards().getJackpotNames());
      Collections.sort(names);
      int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16};

      for (int i = 0; i < names.size() && i < slots.length; i++) {
         String name = names.get(i);
         RewardManager.JackpotPool pool = plugin.rewards().getJackpot(name);
         if (pool != null) {
            int totalWeight = pool.entries.stream().mapToInt(r -> r.weight).sum();
            ItemBuilder b = new ItemBuilder(jackpotIcon(name));
            b.name(pool.broadcastName == null ? "<gold>" + name : pool.broadcastName);
            b.lore(
               List.of(
                  "<gray>Pool id: <white>" + name,
                  "<gray>Chance/catch: <yellow>" + formatPct(pool.chance),
                  "<gray>Entries: <white>" + pool.entries.size(),
                  "<gray>Total weight: <white>" + totalWeight,
                  "",
                  "<yellow>Click <gray>to edit."
               )
            );
            inv.setItem(slots[i], b.build());
            h.slotToName.put(slots[i], name);
         }
      }

      inv.setItem(22, new ItemBuilder(Material.ARROW).name("<gray>Back").build());
      p.openInventory(inv);
   }

   static void openJackpot(NovaFishing plugin, Player p, String poolName) {
      RewardManager.JackpotPool pool = plugin.rewards().getJackpot(poolName);
      if (pool == null) {
         openJackpots(plugin, p);
      } else {
         StaffRewardGUI.JackpotEditor h = new StaffRewardGUI.JackpotEditor(plugin, p.getUniqueId(), poolName);
         Inventory inv = Bukkit.createInventory(h, 54, TextUtil.mm("<dark_gray>Jackpot: " + (pool.broadcastName == null ? poolName : pool.broadcastName)));
         h.inv = inv;

         for (int i = 0; i < pool.entries.size() && i < 45; i++) {
            Reward r = pool.entries.get(i);
            ItemBuilder b = rewardIcon(plugin, r);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Type: <yellow>" + r.type.name());
            lore.add("<gray>Weight: <yellow>" + r.weight);
            lore.add("<gray>Amount: <yellow>" + r.min + "–" + r.max);
            if (r.nexoId != null) {
               lore.add("<gray>Nexo item: <aqua>" + r.nexoId);
            }

            if (r.type == Reward.Type.COMMAND && !r.commands.isEmpty()) {
               lore.add("<gray>Cmd: <white>" + r.commands.get(0));
            }

            lore.add("");
            lore.add("<gray>Left-click: <yellow>weight +5");
            lore.add("<gray>Shift-left: <yellow>weight +25");
            lore.add("<gray>Right-click: <red>delete");
            b.lore(lore);
            inv.setItem(i, b.build());
            h.byIndex.put(i, i);
         }

         inv.setItem(
            46,
            new ItemBuilder(Material.CLOCK)
               .name("<yellow>Chance: <white>" + formatPct(pool.chance))
               .lore(List.of("<gray>Per-catch probability that", "<gray>this jackpot rolls.", "", "<yellow>Click <gray>to set via chat prompt."))
               .build()
         );
         inv.setItem(
            47,
            new ItemBuilder(Material.OAK_SIGN)
               .name("<yellow>Broadcast name")
               .lore(List.of("<gray>" + (pool.broadcastName == null ? "—" : pool.broadcastName), "", "<yellow>Click <gray>to edit (MiniMessage)."))
               .build()
         );
         inv.setItem(
            48,
            new ItemBuilder(Material.EMERALD)
               .name("<green>Add Held Item")
               .lore(List.of("<gray>Adds the item in your main hand", "<gray>as a new ITEM reward (weight=10).", "<gray>You won't lose the held item."))
               .build()
         );
         inv.setItem(
            49,
            new ItemBuilder(Material.LIME_DYE)
               .name("<green>Add Item from Cursor")
               .lore(List.of("<gray>Pick an item up onto your cursor,", "<gray>then click here to add it as a", "<gray>new ITEM reward (weight=10)."))
               .build()
         );
         inv.setItem(
            50,
            new ItemBuilder(Material.COMMAND_BLOCK)
               .name("<gold>Add Command Reward")
               .lore(List.of("<gray>Click and you'll be prompted", "<gray>in chat for the command + weight."))
               .build()
         );
         inv.setItem(
            51,
            new ItemBuilder(Material.CHEST)
               .name("<green>Add Whole Inventory")
               .lore(
                  List.of(
                     "<gray>Adds every item in your inventory",
                     "<gray>as separate ITEM rewards (weight=10).",
                     "<gray>You won't lose your items.",
                     "<gray>Nova rods are skipped."
                  )
               )
               .build()
         );
         inv.setItem(45, new ItemBuilder(Material.ARROW).name("<gray>Back").build());
         p.openInventory(inv);
      }
   }

   private static Material tierIcon(RewardTier t) {
      return switch (t) {
         case JUNK -> Material.ROTTEN_FLESH;
         case COMMON -> Material.COD;
         case UNCOMMON -> Material.PUFFERFISH;
         case RARE -> Material.DIAMOND;
         case EPIC -> Material.NETHERITE_SCRAP;
         case LEGENDARY -> Material.NETHERITE_INGOT;
         case MYTHIC -> Material.NETHER_STAR;
      };
   }

   private static Material jackpotIcon(String poolName) {
      String var1 = poolName.toLowerCase(Locale.ROOT);

      return switch (var1) {
         case "lava" -> Material.BLAZE_ROD;
         case "void" -> Material.ENDER_EYE;
         case "global" -> Material.NETHER_STAR;
         default -> Material.GOLD_INGOT;
      };
   }

   private static String formatPct(double chance) {
      return String.format(Locale.ROOT, "%.2f%%", chance * 100.0);
   }

   private static Map<Enchantment, Integer> readEnchantments(ItemStack stack) {
      if (stack != null && stack.hasItemMeta()) {
         ItemMeta m = stack.getItemMeta();
         Map<Enchantment, Integer> out = new LinkedHashMap<>();
         if (m instanceof EnchantmentStorageMeta esm) {
            out.putAll(esm.getStoredEnchants());
         }

         out.putAll(m.getEnchants());
         return out;
      } else {
         return Collections.emptyMap();
      }
   }

   private static Reward rewardFromStack(NovaFishing plugin, ItemStack stack) {
      String nexoId = plugin.nexo() != null ? plugin.nexo().idOf(stack) : null;
      if (nexoId != null) {
         // Nexo rebuilds the full custom item on delivery, so don't snapshot the base
         // material's meta/enchants here — the id alone keeps the reward in sync with Nexo.
         return new Reward(
            Reward.Type.ITEM, 10, stack.getAmount(), stack.getAmount(), stack.getType(), null, null, null, new ArrayList<>(), null, null, Collections.emptyMap(), nexoId
         );
      }

      return new Reward(
         Reward.Type.ITEM, 10, stack.getAmount(), stack.getAmount(), stack.getType(), null, null, null, new ArrayList<>(), null, null, readEnchantments(stack), null
      );
   }

   private static List<Reward> inventoryRewards(NovaFishing plugin, Player p) {
      List<Reward> out = new ArrayList<>();

      for (ItemStack stack : p.getInventory().getStorageContents()) {
         if (stack != null && stack.getType() != Material.AIR && !plugin.rods().isNovaRod(stack)) {
            out.add(rewardFromStack(plugin, stack));
         }
      }

      return out;
   }

   private static ItemBuilder rewardIcon(NovaFishing plugin, Reward r) {
      if (r.nexoId != null) {
         ItemStack nx = plugin.nexo() != null ? plugin.nexo().build(r.nexoId, Math.max(1, r.min)) : null;
         ItemBuilder b = nx != null ? new ItemBuilder(nx) : new ItemBuilder(r.material != null ? r.material : Material.PAPER);
         b.name(r.displayName != null ? r.displayName : "<aqua>Nexo: <white>" + r.nexoId);
         return b;
      }

      ItemBuilder b = new ItemBuilder(r.displayMaterial != null ? r.displayMaterial : (r.material != null ? r.material : Material.PAPER));
      b.name(r.displayName != null ? r.displayName : "<white>" + r.type.name() + " <gray>(" + (r.material != null ? r.material.name() : "—") + ")");
      return b;
   }

   static final class JackpotBrowser implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      final Map<Integer, String> slotToName = new HashMap<>();
      Inventory inv;

      JackpotBrowser(NovaFishing plugin, UUID v) {
         this.plugin = plugin;
         this.viewer = v;
      }

      public Inventory getInventory() {
         return this.inv;
      }

      @Override
      public UUID viewer() {
         return this.viewer;
      }

      @Override
      public void click(Player p, InventoryClickEvent e) {
         int slot = e.getRawSlot();
         if (slot == 22) {
            StaffRewardGUI.open(this.plugin, p);
         } else {
            String name = this.slotToName.get(slot);
            if (name != null) {
               StaffRewardGUI.openJackpot(this.plugin, p, name);
            }
         }
      }
   }

   static final class JackpotEditor implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      final String poolName;
      final Map<Integer, Integer> byIndex = new HashMap<>();
      Inventory inv;

      JackpotEditor(NovaFishing plugin, UUID v, String poolName) {
         this.plugin = plugin;
         this.viewer = v;
         this.poolName = poolName;
      }

      public Inventory getInventory() {
         return this.inv;
      }

      @Override
      public UUID viewer() {
         return this.viewer;
      }

      @Override
      public void click(Player p, InventoryClickEvent e) {
         int slot = e.getRawSlot();
         if (slot == 45) {
            StaffRewardGUI.openJackpots(this.plugin, p);
         } else if (slot == 46) {
            p.closeInventory();
            this.plugin.prompts().ask(p, "<gold>Set jackpot chance per catch <gray>(0.0–1.0, e.g. <yellow>0.03</yellow> = 3%):", in -> {
               if (in == null) {
                  StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
               } else {
                  try {
                     double v = Double.parseDouble(in.trim());
                     this.plugin.rewards().setJackpotChance(this.poolName, v);
                     p.sendMessage(TextUtil.mm("<green>Chance set to <yellow>" + StaffRewardGUI.formatPct(Math.max(0.0, Math.min(1.0, v)))));
                  } catch (NumberFormatException var5x) {
                     p.sendMessage(TextUtil.mm("<red>Invalid number. Aborted."));
                  }

                  StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
               }
            });
         } else if (slot == 47) {
            p.closeInventory();
            this.plugin.prompts().ask(p, "<gold>Type the new broadcast name <gray>(MiniMessage allowed):", in -> {
               if (in == null) {
                  StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
               } else {
                  this.plugin.rewards().setJackpotBroadcastName(this.poolName, in);
                  p.sendMessage(TextUtil.mm("<green>Broadcast name updated."));
                  StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
               }
            });
         } else if (slot == 51) {
            List<Reward> toAdd = StaffRewardGUI.inventoryRewards(this.plugin, p);
            if (toAdd.isEmpty()) {
               p.sendMessage(TextUtil.mm("<red>No addable items found in your inventory."));
            } else {
               int added = this.plugin.rewards().addJackpotEntries(this.poolName, toAdd);
               p.sendMessage(
                  TextUtil.mm("<green>Added <yellow>" + added + "</yellow> item(s) from your inventory to <yellow>" + this.poolName)
               );
            }

            StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
         } else if (slot == 49) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
               this.plugin.rewards().addJackpotEntry(this.poolName, StaffRewardGUI.rewardFromStack(this.plugin, cursor));
               StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
            } else {
               p.sendMessage(TextUtil.mm("<red>Pick an item up onto your cursor first."));
            }
         } else if (slot == 48) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
               p.sendMessage(TextUtil.mm("<red>Hold an item in your main hand first."));
            } else if (this.plugin.rods().isNovaRod(hand)) {
               p.sendMessage(TextUtil.mm("<red>Refusing to add a Nova rod as a reward."));
            } else {
               this.plugin.rewards().addJackpotEntry(this.poolName, StaffRewardGUI.rewardFromStack(this.plugin, hand));
               p.sendMessage(
                  TextUtil.mm("<green>Added <yellow>" + hand.getType() + "</yellow> ×<yellow>" + hand.getAmount() + "</yellow> to <yellow>" + this.poolName)
               );
               StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
            }
         } else if (slot == 50) {
            p.closeInventory();
            this.plugin
               .prompts()
               .ask(p, "<gold>Type the command to run as a reward <gray>(use <yellow><player></yellow> for the catcher's name):", cmdInput -> {
                  if (cmdInput == null) {
                     StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
                  } else {
                     this.plugin.prompts().ask(p, "<gold>What weight should this entry have? <gray>(integer)", weightInput -> {
                        int weight = 10;
                        if (weightInput != null) {
                           try {
                              weight = Math.max(1, Integer.parseInt(weightInput));
                           } catch (Exception var7x) {
                           }
                        }

                        List<String> cmds = new ArrayList<>();
                        cmds.add(cmdInput);
                        Reward rx = new Reward(Reward.Type.COMMAND, weight, 1, 1, null, "<yellow>" + cmdInput, null, Material.COMMAND_BLOCK, cmds, null);
                        this.plugin.rewards().addJackpotEntry(this.poolName, rx);
                        p.sendMessage(TextUtil.mm("<green>Added command reward to <yellow>" + this.poolName + "</yellow> with weight <yellow>" + weight));
                        StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
                     });
                  }
               });
         } else {
            Integer idx = this.byIndex.get(slot);
            if (idx != null) {
               RewardManager.JackpotPool pool = this.plugin.rewards().getJackpot(this.poolName);
               if (pool != null) {
                  if (idx >= 0 && idx < pool.entries.size()) {
                     Reward r = pool.entries.get(idx);
                     if (e.isRightClick()) {
                        this.plugin.rewards().removeJackpotEntry(this.poolName, idx);
                        StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
                     } else {
                        int delta = e.isShiftClick() ? 25 : 5;
                        int newWeight = Math.max(1, r.weight + delta);
                        Reward updated = new Reward(
                           r.type,
                           newWeight,
                           r.min,
                           r.max,
                           r.material,
                           r.displayName,
                           r.displayLore,
                           r.displayMaterial,
                           r.commands,
                           r.permission,
                           r.event,
                           r.enchantments,
                           r.nexoId
                        );
                        this.plugin.rewards().updateJackpotEntry(this.poolName, idx, updated);
                        StaffRewardGUI.openJackpot(this.plugin, p, this.poolName);
                     }
                  }
               }
            }
         }
      }
   }

   static final class TierBrowser implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      Inventory inv;

      TierBrowser(NovaFishing plugin, UUID v) {
         this.plugin = plugin;
         this.viewer = v;
      }

      public Inventory getInventory() {
         return this.inv;
      }

      @Override
      public UUID viewer() {
         return this.viewer;
      }

      @Override
      public void click(Player p, InventoryClickEvent e) {
         int slot = e.getRawSlot();
         if (slot == 22) {
            StaffRewardGUI.openJackpots(this.plugin, p);
         } else {
            int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16};

            for (int i = 0; i < slots.length; i++) {
               if (slots[i] == slot) {
                  StaffRewardGUI.openTier(this.plugin, p, RewardTier.values()[i]);
                  return;
               }
            }
         }
      }
   }

   static final class TierEditor implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      final RewardTier tier;
      final Map<Integer, Integer> byIndex = new HashMap<>();
      Inventory inv;

      TierEditor(NovaFishing plugin, UUID v, RewardTier t) {
         this.plugin = plugin;
         this.viewer = v;
         this.tier = t;
      }

      public Inventory getInventory() {
         return this.inv;
      }

      @Override
      public UUID viewer() {
         return this.viewer;
      }

      @Override
      public void click(Player p, InventoryClickEvent e) {
         int slot = e.getRawSlot();
         if (slot == 45) {
            StaffRewardGUI.open(this.plugin, p);
         } else if (slot == 47) {
            List<Reward> toAdd = StaffRewardGUI.inventoryRewards(this.plugin, p);
            if (toAdd.isEmpty()) {
               p.sendMessage(TextUtil.mm("<red>No addable items found in your inventory."));
            } else {
               int added = this.plugin.rewards().addRewards(this.tier, toAdd);
               p.sendMessage(
                  TextUtil.mm("<green>Added <yellow>" + added + "</yellow> item(s) from your inventory to <yellow>" + this.tier.name())
               );
            }

            StaffRewardGUI.openTier(this.plugin, p, this.tier);
         } else if (slot == 49) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
               this.plugin.rewards().addReward(this.tier, StaffRewardGUI.rewardFromStack(this.plugin, cursor));
               StaffRewardGUI.openTier(this.plugin, p, this.tier);
            } else {
               p.sendMessage(TextUtil.mm("<red>Pick an item up onto your cursor first."));
            }
         } else if (slot == 48) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand == null || hand.getType() == Material.AIR) {
               p.sendMessage(TextUtil.mm("<red>Hold an item in your main hand first."));
            } else if (this.plugin.rods().isNovaRod(hand)) {
               p.sendMessage(TextUtil.mm("<red>Refusing to add a Nova rod as a reward."));
            } else {
               this.plugin.rewards().addReward(this.tier, StaffRewardGUI.rewardFromStack(this.plugin, hand));
               p.sendMessage(
                  TextUtil.mm("<green>Added <yellow>" + hand.getType() + "</yellow> ×<yellow>" + hand.getAmount() + "</yellow> to <yellow>" + this.tier.name())
               );
               StaffRewardGUI.openTier(this.plugin, p, this.tier);
            }
         } else if (slot == 50) {
            p.closeInventory();
            this.plugin
               .prompts()
               .ask(p, "<gold>Type the command to run as a reward <gray>(use <yellow><player></yellow> for the catcher's name):", cmdInput -> {
                  if (cmdInput != null) {
                     this.plugin.prompts().ask(p, "<gold>What weight should this entry have? <gray>(integer)", weightInput -> {
                        int weight = 10;
                        if (weightInput != null) {
                           try {
                              weight = Math.max(1, Integer.parseInt(weightInput));
                           } catch (Exception var7x) {
                           }
                        }

                        List<String> cmds = new ArrayList<>();
                        cmds.add(cmdInput);
                        Reward rx = new Reward(Reward.Type.COMMAND, weight, 1, 1, null, "<yellow>" + cmdInput, null, Material.COMMAND_BLOCK, cmds, null);
                        this.plugin.rewards().addReward(this.tier, rx);
                        p.sendMessage(TextUtil.mm("<green>Added command reward to <yellow>" + this.tier.name() + "</yellow> with weight <yellow>" + weight));
                        StaffRewardGUI.openTier(this.plugin, p, this.tier);
                     });
                  }
               });
         } else {
            Integer idx = this.byIndex.get(slot);
            if (idx != null) {
               List<Reward> list = this.plugin.rewards().getRewards(this.tier);
               if (idx >= 0 && idx < list.size()) {
                  Reward r = list.get(idx);
                  if (e.isRightClick()) {
                     this.plugin.rewards().removeReward(this.tier, idx);
                     StaffRewardGUI.openTier(this.plugin, p, this.tier);
                  } else {
                     int delta = e.isShiftClick() ? 25 : 5;
                     int newWeight = Math.max(1, r.weight + delta);
                     Reward updated = new Reward(
                        r.type,
                        newWeight,
                        r.min,
                        r.max,
                        r.material,
                        r.displayName,
                        r.displayLore,
                        r.displayMaterial,
                        r.commands,
                        r.permission,
                        r.event,
                        r.enchantments,
                        r.nexoId
                     );
                     list.set(idx, updated);
                     this.plugin.rewards().save();
                     StaffRewardGUI.openTier(this.plugin, p, this.tier);
                  }
               }
            }
         }
      }
   }
}
