package dev.nova.fishing.gui;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.token.ShopCategory;
import dev.nova.fishing.token.ShopItem;
import dev.nova.fishing.util.ItemBuilder;
import dev.nova.fishing.util.TextUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class TokenShopGUI {
   private TokenShopGUI() {
   }

   public static void open(NovaFishing plugin, Player p) {
      int rows = Math.max(1, Math.min(6, plugin.configs().shop().getInt("ui.rows", 3)));
      String title = plugin.configs().shop().getString("ui.title", "<dark_gray>Nova Token Shop");
      TokenShopGUI.MainHolder h = new TokenShopGUI.MainHolder(plugin, p.getUniqueId());
      Inventory inv = Bukkit.createInventory(h, rows * 9, TextUtil.mm(title));
      h.inv = inv;
      Material filler = Material.matchMaterial(plugin.configs().shop().getString("ui.filler.material", "BLACK_STAINED_GLASS_PANE"));
      if (filler == null) {
         filler = Material.BLACK_STAINED_GLASS_PANE;
      }

      String fillerName = plugin.configs().shop().getString("ui.filler.name", " ");

      for (int i = 0; i < inv.getSize(); i++) {
         inv.setItem(i, new ItemBuilder(filler).name(fillerName).hideAll().build());
      }

      for (ShopCategory c : plugin.shop().getCategories()) {
         if (c.slot >= 0 && c.slot < inv.getSize()) {
            ItemBuilder b = new ItemBuilder(c.icon);
            b.name(c.displayName);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + c.items.size() + " item(s)");
            lore.add("");
            lore.add("<yellow>Click <gray>to browse.");
            b.lore(lore);
            inv.setItem(c.slot, b.build());
         }
      }

      long bal = plugin.tokens().get(p.getUniqueId());
      int balSlot = inv.getSize() - 5;
      if (balSlot >= 0) {
         inv.setItem(balSlot, new ItemBuilder(Material.SUNFLOWER).name("<yellow>Your Balance: <gold>" + bal + " Tokens").build());
      }

      int storeSlot = inv.getSize() - 1;
      List<String> storeLore = new ArrayList<>();
      storeLore.add("<gray>Need more tokens?");
      storeLore.add("<gray>Visit <aqua>" + storeLinkDisplay(plugin) + " <gray>to buy.");
      storeLore.add("");
      storeLore.add("<yellow>Click <gray>for the store link.");
      inv.setItem(storeSlot, new ItemBuilder(Material.EMERALD).name("<green>Buy More Tokens").lore(storeLore).build());
      p.openInventory(inv);
   }

   private static void sendStoreLink(NovaFishing plugin, Player p) {
      p.closeInventory();
      String url = storeLinkUrl(plugin);
      p.sendMessage(
         TextUtil.mm(
            "<gold>» <yellow>Click here to visit the Nova Token Store: <click:open_url:'" + url + "'><aqua><underlined>" + url + "</underlined></aqua></click>"
         )
      );
   }

   public static String storeLinkUrl(NovaFishing plugin) {
      String url = plugin.configs().shop().getString("store-link", "https://novamc.me");
      return url != null && !url.isBlank() ? url.trim() : "https://novamc.me";
   }

   private static String storeLinkDisplay(NovaFishing plugin) {
      String url = storeLinkUrl(plugin);
      int i = url.indexOf("://");
      return i >= 0 ? url.substring(i + 3) : url;
   }

   public static void openCategory(NovaFishing plugin, Player p, String catId) {
      ShopCategory cat = plugin.shop().getCategory(catId);
      if (cat == null) {
         open(plugin, p);
      } else {
         TokenShopGUI.CategoryHolder h = new TokenShopGUI.CategoryHolder(plugin, p.getUniqueId(), cat);
         Inventory inv = Bukkit.createInventory(h, 54, TextUtil.mm("<dark_gray>" + TextUtil.stripTags(cat.displayName)));
         h.inv = inv;
         int slot = 10;
         int placed = 0;

         for (int i = 0; i < cat.items.size() && slot < 44; i++) {
            ShopItem it = cat.items.get(i);
            ItemStack icon = it.iconStack();
            ItemBuilder b = icon != null ? new ItemBuilder(icon) : new ItemBuilder(it.iconMaterial());
            if (it.displayName != null) {
               b.name(it.displayName);
            } else if (icon == null) {
               b.name("<white>" + it.id);
            }

            List<String> lore = new ArrayList<>(it.displayLore == null ? List.of() : it.displayLore);
            lore.add("");
            lore.add("<gold>Cost: <yellow>" + it.cost + " Tokens");
            lore.add("<yellow>Click <gray>to purchase.");
            b.lore(lore);
            inv.setItem(slot, b.build());
            h.byIndex.put(slot, i);
            if ((++slot + 1) % 9 == 0) {
               slot += 2;
            }

            placed++;
         }

         if (placed == 0) {
            inv.setItem(22, new ItemBuilder(Material.BARRIER).name(plugin.configs().rawMessage("shop.category-empty")).build());
         }

         inv.setItem(49, new ItemBuilder(Material.ARROW).name("<gray>Back").build());
         p.openInventory(inv);
      }
   }

   public static void openConfirm(NovaFishing plugin, Player p, ShopCategory cat, int idx) {
      if (cat == null || idx < 0 || idx >= cat.items.size()) {
         openCategory(plugin, p, cat == null ? null : cat.id);
         return;
      }

      ShopItem it = cat.items.get(idx);
      TokenShopGUI.ConfirmHolder h = new TokenShopGUI.ConfirmHolder(plugin, p.getUniqueId(), cat, idx);
      Inventory inv = Bukkit.createInventory(h, 27, TextUtil.mm("<dark_gray>Confirm Purchase"));
      h.inv = inv;
      Material filler = Material.matchMaterial(plugin.configs().shop().getString("ui.filler.material", "BLACK_STAINED_GLASS_PANE"));
      if (filler == null) {
         filler = Material.BLACK_STAINED_GLASS_PANE;
      }

      String fillerName = plugin.configs().shop().getString("ui.filler.name", " ");

      for (int i = 0; i < inv.getSize(); i++) {
         inv.setItem(i, new ItemBuilder(filler).name(fillerName).hideAll().build());
      }

      ItemStack icon = it.iconStack();
      ItemBuilder pb = icon != null ? new ItemBuilder(icon) : new ItemBuilder(it.iconMaterial());
      if (it.displayName != null) {
         pb.name(it.displayName);
      } else if (icon == null) {
         pb.name("<white>" + it.id);
      }

      long bal = plugin.tokens().get(p.getUniqueId());
      List<String> lore = new ArrayList<>(it.displayLore == null ? List.of() : it.displayLore);
      lore.add("");
      lore.add("<gold>Cost: <yellow>" + it.cost + " Tokens");
      lore.add("<gray>Balance: <yellow>" + bal + " <gray>→ <yellow>" + Math.max(0L, bal - it.cost));
      pb.lore(lore);
      inv.setItem(13, pb.build());

      List<String> confirmLore = new ArrayList<>();
      confirmLore.add("<gray>Buy for <yellow>" + it.cost + " Tokens<gray>.");
      confirmLore.add("");
      confirmLore.add("<yellow>Click <gray>to confirm.");
      inv.setItem(11, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("<green><bold>Confirm Purchase").lore(confirmLore).build());

      List<String> cancelLore = new ArrayList<>();
      cancelLore.add("<gray>Don't buy this item.");
      cancelLore.add("");
      cancelLore.add("<yellow>Click <gray>to go back.");
      inv.setItem(15, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("<red><bold>Cancel").lore(cancelLore).build());

      p.openInventory(inv);
   }

   static final class ConfirmHolder implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      final ShopCategory cat;
      final int idx;
      Inventory inv;

      ConfirmHolder(NovaFishing plugin, UUID v, ShopCategory c, int idx) {
         this.plugin = plugin;
         this.viewer = v;
         this.cat = c;
         this.idx = idx;
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
         if (slot == 11) {
            if (this.idx >= 0 && this.idx < this.cat.items.size()) {
               this.plugin.shop().purchase(p, this.cat.items.get(this.idx));
            }

            TokenShopGUI.openCategory(this.plugin, p, this.cat.id);
         } else if (slot == 15) {
            TokenShopGUI.openCategory(this.plugin, p, this.cat.id);
         }
      }
   }

   static final class CategoryHolder implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      final ShopCategory cat;
      final Map<Integer, Integer> byIndex = new HashMap<>();
      Inventory inv;

      CategoryHolder(NovaFishing plugin, UUID v, ShopCategory c) {
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
         if (slot == 49) {
            TokenShopGUI.open(this.plugin, p);
         } else {
            Integer idx = this.byIndex.get(slot);
            if (idx != null) {
               if (idx >= 0 && idx < this.cat.items.size()) {
                  if (this.plugin.configs().shop().getBoolean("ui.confirm-purchase", true)) {
                     TokenShopGUI.openConfirm(this.plugin, p, this.cat, idx);
                  } else {
                     this.plugin.shop().purchase(p, this.cat.items.get(idx));
                     TokenShopGUI.openCategory(this.plugin, p, this.cat.id);
                  }
               }
            }
         }
      }
   }

   static final class MainHolder implements GUIManager.NovaHolder {
      private final NovaFishing plugin;
      private final UUID viewer;
      Inventory inv;

      MainHolder(NovaFishing plugin, UUID v) {
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
         if (slot == this.inv.getSize() - 1) {
            TokenShopGUI.sendStoreLink(this.plugin, p);
         } else {
            for (ShopCategory c : this.plugin.shop().getCategories()) {
               if (c.slot == slot) {
                  TokenShopGUI.openCategory(this.plugin, p, c.id);
                  return;
               }
            }
         }
      }
   }
}
