package dev.nova.fishing.token;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.util.TextUtil;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ShopManager {
   private final NovaFishing plugin;
   private final Map<String, ShopCategory> categories = new LinkedHashMap<>();

   public ShopManager(NovaFishing plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      this.categories.clear();
      ConfigurationSection root = this.plugin.configs().shop().getConfigurationSection("categories");
      if (root != null) {
         for (String id : root.getKeys(false)) {
            ShopCategory c = ShopCategory.fromConfig(id, root.getConfigurationSection(id));
            if (c != null) {
               this.categories.put(id.toLowerCase(), c);
            }
         }
      }
   }

   public int getCategoryCount() {
      return this.categories.size();
   }

   public Collection<ShopCategory> getCategories() {
      return this.categories.values();
   }

   public ShopCategory getCategory(String id) {
      return this.categories.get(id.toLowerCase());
   }

   public void save() {
      ConfigurationSection root = this.plugin.configs().shop().getConfigurationSection("categories");
      if (root != null) {
         for (String k : root.getKeys(false)) {
            root.set(k, null);
         }
      } else {
         root = this.plugin.configs().shop().createSection("categories");
      }

      for (ShopCategory c : this.categories.values()) {
         root.set(c.id, c.toMap());
      }

      this.plugin.configs().saveShopAsync();
   }

   public ShopCategory createCategory(String id, String displayName, Material icon, int slot) {
      ShopCategory c = new ShopCategory(id, displayName, icon, slot);
      this.categories.put(id.toLowerCase(), c);
      this.save();
      return c;
   }

   public boolean deleteCategory(String id) {
      if (this.categories.remove(id.toLowerCase()) == null) {
         return false;
      } else {
         this.save();
         return true;
      }
   }

   public void addItem(ShopCategory c, ShopItem item) {
      c.items.add(item);
      this.save();
   }

   public boolean removeItem(ShopCategory c, int index) {
      if (index >= 0 && index < c.items.size()) {
         c.items.remove(index);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean purchase(Player p, ShopItem item) {
      if (item.permission != null && !p.hasPermission(item.permission)) {
         p.sendMessage(TextUtil.mm("<red>You don't have permission for that item."));
         return false;
      } else if (!this.plugin.tokens().take(p.getUniqueId(), item.cost)) {
         Map<String, String> ph = new HashMap<>();
         ph.put("amount", String.valueOf(item.cost));
         p.sendMessage(TextUtil.mm(this.plugin.configs().message("token.not-enough"), ph));
         return false;
      } else {
         switch (item.type) {
            case ITEM:
               ItemStack s = item.stack != null ? item.stack.clone() : new ItemStack(item.material == null ? Material.PAPER : item.material);
               Map<Integer, ItemStack> over = p.getInventory().addItem(new ItemStack[]{s});

               for (ItemStack o : over.values()) {
                  p.getWorld().dropItemNaturally(p.getLocation(), o);
               }
               break;
            case ROD:
               this.plugin.rods().giveRod(p, item.rodId, 1, 0L);
               break;
            case COMMAND:
               for (String c : item.commands) {
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("<player>", p.getName()));
               }
         }

         Map<String, String> ph = new HashMap<>();
         ph.put("item", item.displayName != null ? item.displayName : item.id);
         ph.put("cost", String.valueOf(item.cost));
         p.sendMessage(TextUtil.mm(this.plugin.configs().message("shop.purchased"), ph));
         return true;
      }
   }
}
