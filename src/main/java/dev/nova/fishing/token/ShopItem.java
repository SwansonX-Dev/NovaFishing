package dev.nova.fishing.token;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;

public final class ShopItem {
   public final String id;
   public final ShopItem.Type type;
   public final long cost;
   public final Material material;
   public final ItemStack stack;
   public final String rodId;
   public final List<String> commands;
   public final String displayName;
   public final Material displayMaterial;
   public final List<String> displayLore;
   public final String permission;

   public ShopItem(
      String id,
      ShopItem.Type type,
      long cost,
      Material material,
      String rodId,
      List<String> commands,
      String displayName,
      Material displayMaterial,
      List<String> displayLore,
      String permission
   ) {
      this(id, type, cost, material, null, rodId, commands, displayName, displayMaterial, displayLore, permission);
   }

   public ShopItem(
      String id,
      ShopItem.Type type,
      long cost,
      Material material,
      ItemStack stack,
      String rodId,
      List<String> commands,
      String displayName,
      Material displayMaterial,
      List<String> displayLore,
      String permission
   ) {
      this.id = id;
      this.type = type;
      this.cost = cost;
      this.material = material;
      this.stack = stack;
      this.rodId = rodId;
      this.commands = commands;
      this.displayName = displayName;
      this.displayMaterial = displayMaterial;
      this.displayLore = displayLore;
      this.permission = permission;
   }

   public Material iconMaterial() {
      if (this.displayMaterial != null) {
         return this.displayMaterial;
      } else if (this.stack != null) {
         return this.stack.getType();
      } else if (this.material != null) {
         return this.material;
      } else {
         return this.type == ShopItem.Type.ROD ? Material.FISHING_ROD : Material.PAPER;
      }
   }

   public ItemStack iconStack() {
      return this.stack == null ? null : this.stack.clone();
   }

   public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", this.id);
      m.put("type", this.type.name());
      m.put("cost", this.cost);
      if (this.rodId != null) {
         m.put("rod", this.rodId);
      }

      if (this.material != null) {
         m.put("material", this.material.name());
      }

      if (this.stack != null) {
         m.put("item", this.stack.serialize());
      }

      if (!this.commands.isEmpty()) {
         m.put("commands", new ArrayList<>(this.commands));
      }

      if (this.permission != null) {
         m.put("permission", this.permission);
      }

      Map<String, Object> d = new LinkedHashMap<>();
      if (this.displayName != null) {
         d.put("name", this.displayName);
      }

      if (this.displayMaterial != null) {
         d.put("material", this.displayMaterial.name());
      }

      if (this.displayLore != null && !this.displayLore.isEmpty()) {
         d.put("lore", new ArrayList<>(this.displayLore));
      }

      if (!d.isEmpty()) {
         m.put("display", d);
      }

      return m;
   }

   public static ShopItem fromMap(Map<?, ?> raw) {
      if (raw == null) {
         return null;
      } else {
         Map<String, Object> map = (Map<String, Object>)raw;
         String id = String.valueOf(((Map<?, String>)raw).getOrDefault("id", "item"));

         ShopItem.Type type;
         try {
            type = ShopItem.Type.valueOf(String.valueOf(map.getOrDefault("type", "ITEM")).toUpperCase());
         } catch (IllegalArgumentException var25) {
            return null;
         }

         long cost = ((Number)((Map<?, Integer>)raw).getOrDefault("cost", 0)).longValue();
         String rodId = (String)raw.get("rod");
         Material mat = null;
         if (raw.get("material") instanceof String s) {
            mat = Material.matchMaterial(s);
         }

         ItemStack stack = null;
         if (raw.get("item") instanceof Map<?, ?> im) {
            try {
               @SuppressWarnings("unchecked")
               Map<String, Object> imTyped = (Map<String, Object>) im;
               if (ConfigurationSerialization.deserializeObject(imTyped, ItemStack.class) instanceof ItemStack is) {
                  stack = is;
               }
            } catch (Throwable var24) {
            }
         }

         if (stack != null && mat == null) {
            mat = stack.getType();
         }

         List<String> commands = new ArrayList<>();
         Object cObj = raw.get("commands");
         if (cObj instanceof List) {
            for (Object x : (List)cObj) {
               commands.add(String.valueOf(x));
            }
         }

         String permission = (String)raw.get("permission");
         String dName = null;
         Material dMat = null;
         List<String> dLore = new ArrayList<>();
         if (raw.get("display") instanceof Map<?, ?> dm) {
            if (dm.get("name") != null) {
               dName = String.valueOf(dm.get("name"));
            }

            if (dm.get("material") instanceof String s) {
               dMat = Material.matchMaterial(s);
            }

            Object lObj = dm.get("lore");
            if (lObj instanceof List) {
               for (Object x : (List)lObj) {
                  dLore.add(String.valueOf(x));
               }
            }
         }

         return new ShopItem(id, type, cost, mat, stack, rodId, commands, dName, dMat, dLore, permission);
      }
   }

   public static enum Type {
      ITEM,
      ROD,
      COMMAND;
   }
}
