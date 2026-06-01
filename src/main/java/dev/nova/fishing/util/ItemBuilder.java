package dev.nova.fishing.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class ItemBuilder {
   private final ItemStack item;
   private final ItemMeta meta;

   public ItemBuilder(Material material) {
      this.item = new ItemStack(material);
      this.meta = this.item.getItemMeta();
   }

   public ItemBuilder(ItemStack base) {
      this.item = base;
      this.meta = this.item.getItemMeta();
   }

   public ItemBuilder name(String mm) {
      if (this.meta != null) {
         this.meta.displayName(TextUtil.mm(mm));
      }

      return this;
   }

   public ItemBuilder lore(List<String> lines) {
      if (this.meta != null && lines != null) {
         List<Component> components = new ArrayList<>();

         for (String l : lines) {
            components.add(TextUtil.mm(l));
         }

         this.meta.lore(components);
      }

      return this;
   }

   public ItemBuilder lore(List<String> lines, Map<String, String> placeholders) {
      if (this.meta != null && lines != null) {
         List<Component> components = new ArrayList<>();

         for (String l : lines) {
            components.add(TextUtil.mm(TextUtil.replace(l, placeholders)));
         }

         this.meta.lore(components);
      }

      return this;
   }

   public ItemBuilder amount(int amount) {
      this.item.setAmount(Math.max(1, amount));
      return this;
   }

   public ItemBuilder customModelData(Integer cmd) {
      if (this.meta != null && cmd != null) {
         this.meta.setCustomModelData(cmd);
      }

      return this;
   }

   public ItemBuilder glow() {
      if (this.meta != null) {
         this.meta.addEnchant(Enchantment.UNBREAKING, 1, true);
         this.meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
      }

      return this;
   }

   public ItemBuilder enchant(Enchantment ench, int level) {
      if (this.meta != null && ench != null && level > 0) {
         if (this.meta instanceof EnchantmentStorageMeta esm) {
            esm.addStoredEnchant(ench, level, true);
         } else {
            this.meta.addEnchant(ench, level, true);
         }
      }

      return this;
   }

   public ItemBuilder enchants(Map<Enchantment, Integer> enchs) {
      if (this.meta != null && enchs != null) {
         boolean book = this.meta instanceof EnchantmentStorageMeta;

         for (Entry<Enchantment, Integer> e : enchs.entrySet()) {
            if (e.getKey() != null && e.getValue() != null && e.getValue() > 0) {
               if (book) {
                  ((EnchantmentStorageMeta)this.meta).addStoredEnchant(e.getKey(), e.getValue(), true);
               } else {
                  this.meta.addEnchant(e.getKey(), e.getValue(), true);
               }
            }
         }
      }

      return this;
   }

   public ItemBuilder hideAll() {
      if (this.meta != null) {
         this.meta.addItemFlags(ItemFlag.values());
      }

      return this;
   }

   public ItemMeta meta() {
      return this.meta;
   }

   public ItemStack build() {
      if (this.meta != null) {
         this.item.setItemMeta(this.meta);
      }

      return this.item;
   }
}
