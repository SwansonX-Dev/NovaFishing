package dev.nova.fishing.gui;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.token.ShopCategory;
import dev.nova.fishing.token.ShopItem;
import dev.nova.fishing.util.ItemBuilder;
import dev.nova.fishing.util.TextUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class ShopEditorGUI {
   private ShopEditorGUI() {
   }

   public static void open(NovaFishing plugin, Player p) {
      ShopEditorGUI.Browser h = new ShopEditorGUI.Browser(plugin, p.getUniqueId());
      Inventory inv = Bukkit.createInventory(h, 54, TextUtil.mm("<dark_gray>Shop Editor"));
      h.inv = inv;
      int i = 0;

      for (ShopCategory c : plugin.shop().getCategories()) {
         if (i >= 45) {
            break;
         }

         ItemBuilder b = new ItemBuilder(c.icon).name(c.displayName);
         List<String> lore = new ArrayList<>();
         lore.add("<gray>Slot: <white>" + c.slot);
         lore.add("<gray>Items: <white>" + c.items.size());
         lore.add("");
         lore.add("<yellow>Left-click <gray>to edit items.");
         lore.add("<yellow>Shift right-click <gray>to delete.");
         b.lore(lore);
         inv.setItem(i++, b.build());
      }

      inv.setItem(
         49,
         new ItemBuilder(Material.WRITABLE_BOOK)
            .name("<gold>Create Category")
            .lore(
               List.of(
                  "<gray>Pick an item up on your cursor first,",
                  "<gray>then click here. The cursor item becomes",
                  "<gray>the category icon and chat will ask you",
                  "<gray>for the display name."
               )
            )
            .build()
      );
      p.openInventory(inv);
   }

   public static void openCategory(NovaFishing plugin, Player p, String catId) {
      ShopCategory cat = plugin.shop().getCategory(catId);
      if (cat == null) {
         open(plugin, p);
      } else {
         ShopEditorGUI.CatEditor h = new ShopEditorGUI.CatEditor(plugin, p.getUniqueId(), cat);
         Inventory inv = Bukkit.createInventory(h, 54, TextUtil.mm("<dark_gray>Edit: " + TextUtil.stripTags(cat.displayName)));
         h.inv = inv;

         for (int i = 0; i < cat.items.size() && i < 45; i++) {
            ShopItem it = cat.items.get(i);
            ItemStack icon = it.iconStack();
            ItemBuilder b = icon != null ? new ItemBuilder(icon) : new ItemBuilder(it.iconMaterial());
            b.name(it.displayName != null ? it.displayName : "<white>" + it.id);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Type: <yellow>" + it.type.name());
            lore.add("<gray>Cost: <yellow>" + it.cost + " Tokens");
            if (it.rodId != null) {
               lore.add("<gray>Rod: <white>" + it.rodId);
            }

            if (!it.commands.isEmpty()) {
               lore.add("<gray>Cmd: <white>" + it.commands.get(0));
            }

            lore.add("");
            lore.add("<gray>Left-click: <yellow>cost +50");
            lore.add("<gray>Shift-left: <yellow>cost +500");
            lore.add("<gray>Right-click: <red>delete");
            b.lore(lore);
            inv.setItem(i, b.build());
            h.byIndex.put(i, i);
         }

         inv.setItem(45, new ItemBuilder(Material.ARROW).name("<gray>Back").build());
         inv.setItem(46, new ItemBuilder(Material.NAME_TAG).name("<gold>Rename Category").lore(List.of("<gray>Chat prompt for a new display name.")).build());
         inv.setItem(
            47,
            new ItemBuilder(Material.EMERALD)
               .name("<green>Add Held Item")
               .lore(
                  List.of(
                     "<gray>Adds the item in your main hand",
                     "<gray>as a new ITEM purchase for <yellow>100 Tokens</yellow>.",
                     "<gray>You won't lose the held item."
                  )
               )
               .build()
         );
         inv.setItem(
            49,
            new ItemBuilder(Material.LIME_DYE)
               .name("<green>Add Cursor Item")
               .lore(List.of("<gray>Pick an item up onto your cursor,", "<gray>then click here."))
               .build()
         );
         inv.setItem(
            51, new ItemBuilder(Material.FISHING_ROD).name("<gold>Add Rod Purchase").lore(List.of("<gray>Chat-prompts for the rod id + cost.")).build()
         );
         inv.setItem(
            53, new ItemBuilder(Material.COMMAND_BLOCK).name("<gold>Add Command Reward").lore(List.of("<gray>Chat-prompts for the command + cost.")).build()
         );
         p.openInventory(inv);
      }
   }

   private static void createCategory(NovaFishing plugin, Player p, ItemStack cursor) {
      if (cursor != null && cursor.getType() != Material.AIR) {
         Material icon = cursor.getType();
         p.closeInventory();
         plugin.prompts()
            .ask(p, "<gold>Type the display name for the new category <gray>(MiniMessage allowed, e.g. <yellow><gold>Donor Perks</yellow>):", name -> {
               if (name != null) {
                  String id = sanitize(name);
                  int slot = nextFreeSlot(plugin);
                  plugin.shop().createCategory(id, name, icon, slot);
                  p.sendMessage(TextUtil.mm("<green>Created category <yellow>" + id + " <gray>at slot <yellow>" + slot));
                  open(plugin, p);
               }
            });
      } else {
         p.sendMessage(TextUtil.mm("<red>Pick an item up onto your cursor first to use as the icon."));
      }
   }

   private static void renameCategory(NovaFishing plugin, Player p, ShopCategory cat) {
      p.closeInventory();
      plugin.prompts().ask(p, "<gold>Type the new display name for <yellow>" + cat.id + "</yellow>:", name -> {
         if (name != null) {
            cat.displayName = name;
            plugin.shop().save();
            p.sendMessage(TextUtil.mm("<green>Renamed."));
            openCategory(plugin, p, cat.id);
         }
      });
   }

   private static void addHeldItem(NovaFishing plugin, Player p, ShopCategory cat) {
      ItemStack hand = p.getInventory().getItemInMainHand();
      if (hand == null || hand.getType() == Material.AIR) {
         p.sendMessage(TextUtil.mm("<red>Hold an item in your main hand first."));
      } else if (plugin.rods().isNovaRod(hand)) {
         p.sendMessage(TextUtil.mm("<red>Use \"Add Rod Purchase\" for Nova rods."));
      } else {
         ItemStack saved = hand.clone();
         saved.setAmount(1);
         ShopItem item = new ShopItem(
            hand.getType().name().toLowerCase() + "_" + System.currentTimeMillis(),
            ShopItem.Type.ITEM,
            100L,
            hand.getType(),
            saved,
            null,
            new ArrayList<>(),
            null,
            null,
            new ArrayList<>(),
            null
         );
         plugin.shop().addItem(cat, item);
         p.sendMessage(TextUtil.mm("<green>Added <yellow>" + hand.getType() + "</yellow> for <yellow>100 Tokens</yellow> (left-click in editor to bump cost)."));
         openCategory(plugin, p, cat.id);
      }
   }

   private static void addCursorItem(NovaFishing plugin, Player p, ShopCategory cat, ItemStack cursor) {
      if (cursor != null && cursor.getType() != Material.AIR) {
         ItemStack saved = cursor.clone();
         saved.setAmount(1);
         ShopItem item = new ShopItem(
            cursor.getType().name().toLowerCase() + "_" + System.currentTimeMillis(),
            ShopItem.Type.ITEM,
            100L,
            cursor.getType(),
            saved,
            null,
            new ArrayList<>(),
            null,
            null,
            new ArrayList<>(),
            null
         );
         plugin.shop().addItem(cat, item);
         openCategory(plugin, p, cat.id);
      } else {
         p.sendMessage(TextUtil.mm("<red>Pick an item up onto your cursor first."));
      }
   }

   private static void addRodPurchase(NovaFishing plugin, Player p, ShopCategory cat) {
      p.closeInventory();
      plugin.prompts()
         .ask(
            p,
            "<gold>Type the rod id to list in the shop <gray>(one of: <yellow>" + String.join(", ", plugin.rods().getRodIds()) + "<gray>):",
            rodId -> {
               if (rodId != null) {
                  if (plugin.rods().getRod(rodId) == null) {
                     p.sendMessage(TextUtil.mm("<red>Unknown rod id."));
                     openCategory(plugin, p, cat.id);
                  } else {
                     plugin.prompts()
                        .ask(
                           p,
                           "<gold>Type the cost in Nova Tokens:",
                           costInput -> {
                              long cost = 1000L;
                              if (costInput != null) {
                                 try {
                                    cost = Math.max(1L, Long.parseLong(costInput));
                                 } catch (Exception var8) {
                                 }
                              }

                              ShopItem item = new ShopItem(
                                 "rod_" + rodId + "_" + System.currentTimeMillis(),
                                 ShopItem.Type.ROD,
                                 cost,
                                 Material.FISHING_ROD,
                                 rodId,
                                 new ArrayList<>(),
                                 "<gold>" + rodId,
                                 Material.FISHING_ROD,
                                 new ArrayList<>(),
                                 null
                              );
                              plugin.shop().addItem(cat, item);
                              p.sendMessage(TextUtil.mm("<green>Added rod <yellow>" + rodId + "</yellow> for <yellow>" + cost + " Tokens"));
                              openCategory(plugin, p, cat.id);
                           }
                        );
                  }
               }
            }
         );
   }

   private static void addCommandPurchase(NovaFishing plugin, Player p, ShopCategory cat) {
      p.closeInventory();
      plugin.prompts()
         .ask(
            p,
            "<gold>Type the command <gray>(use <yellow><player></yellow> for the buyer's name):",
            cmd -> {
               if (cmd != null) {
                  plugin.prompts()
                     .ask(
                        p,
                        "<gold>Type the cost in Nova Tokens:",
                        costInput -> {
                           long cost = 100L;
                           if (costInput != null) {
                              try {
                                 cost = Math.max(1L, Long.parseLong(costInput));
                              } catch (Exception var8) {
                              }
                           }

                           ShopItem item = new ShopItem(
                              "cmd_" + System.currentTimeMillis(),
                              ShopItem.Type.COMMAND,
                              cost,
                              null,
                              null,
                              new ArrayList<>(List.of(cmd)),
                              "<yellow>" + cmd,
                              Material.COMMAND_BLOCK,
                              new ArrayList<>(),
                              null
                           );
                           plugin.shop().addItem(cat, item);
                           p.sendMessage(TextUtil.mm("<green>Added command reward for <yellow>" + cost + " Tokens"));
                           openCategory(plugin, p, cat.id);
                        }
                     );
               }
            }
         );
   }

   private static String sanitize(String name) {
      String plain = TextUtil.stripTags(name).toLowerCase().replaceAll("[^a-z0-9]+", "_");
      if (plain.isBlank()) {
         plain = "category";
      }

      if (plain.length() > 24) {
         plain = plain.substring(0, 24);
      }

      return plain + "_" + Integer.toHexString((int)(System.currentTimeMillis() & 65535L));
   }

   private static int nextFreeSlot(NovaFishing plugin) {
      Set<Integer> used = new HashSet<>();

      for (ShopCategory c : plugin.shop().getCategories()) {
         used.add(c.slot);
      }

      int rows = Math.max(1, Math.min(6, plugin.configs().shop().getInt("ui.rows", 3)));

      for (int i = 0; i < rows * 9; i++) {
         if (!used.contains(i)) {
            return i;
         }
      }

      return 0;
   }

   static final class Browser implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      Inventory inv;

      Browser(NovaFishing plugin, UUID v) {
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
         if (slot == 49) {
            ShopEditorGUI.createCategory(this.plugin, p, e.getCursor());
         } else {
            int i = 0;

            for (ShopCategory c : this.plugin.shop().getCategories()) {
               if (i == slot) {
                  if (e.getClick() == ClickType.SHIFT_RIGHT) {
                     this.plugin.shop().deleteCategory(c.id);
                     p.sendMessage(TextUtil.mm("<green>Deleted category <yellow>" + c.id));
                     ShopEditorGUI.open(this.plugin, p);
                  } else {
                     ShopEditorGUI.openCategory(this.plugin, p, c.id);
                  }

                  return;
               }

               i++;
            }
         }
      }
   }

   static final class CatEditor implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      final ShopCategory cat;
      final Map<Integer, Integer> byIndex = new HashMap<>();
      Inventory inv;

      CatEditor(NovaFishing plugin, UUID v, ShopCategory c) {
         this.plugin = plugin;
         this.viewer = v;
         this.cat = c;
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
            ShopEditorGUI.open(this.plugin, p);
         } else if (slot == 46) {
            ShopEditorGUI.renameCategory(this.plugin, p, this.cat);
         } else if (slot == 47) {
            ShopEditorGUI.addHeldItem(this.plugin, p, this.cat);
         } else if (slot == 49) {
            ShopEditorGUI.addCursorItem(this.plugin, p, this.cat, e.getCursor());
         } else if (slot == 51) {
            ShopEditorGUI.addRodPurchase(this.plugin, p, this.cat);
         } else if (slot == 53) {
            ShopEditorGUI.addCommandPurchase(this.plugin, p, this.cat);
         } else {
            Integer idx = this.byIndex.get(slot);
            if (idx != null && idx >= 0 && idx < this.cat.items.size()) {
               ShopItem r = this.cat.items.get(idx);
               if (e.isRightClick()) {
                  this.plugin.shop().removeItem(this.cat, idx);
                  ShopEditorGUI.openCategory(this.plugin, p, this.cat.id);
               } else {
                  long delta = e.isShiftClick() ? 500L : 50L;
                  ShopItem updated = new ShopItem(
                     r.id, r.type, r.cost + delta, r.material, r.stack, r.rodId, r.commands, r.displayName, r.displayMaterial, r.displayLore, r.permission
                  );
                  this.cat.items.set(idx, updated);
                  this.plugin.shop().save();
                  ShopEditorGUI.openCategory(this.plugin, p, this.cat.id);
               }
            }
         }
      }
   }
}
