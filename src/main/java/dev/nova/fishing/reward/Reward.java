package dev.nova.fishing.reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;

public final class Reward {
   public final Reward.Type type;
   public final int weight;
   public final int min;
   public final int max;
   public final Material material;
   public final String displayName;
   public final List<String> displayLore;
   public final Material displayMaterial;
   public final List<String> commands;
   public final String permission;
   public final String event;
   public final Map<Enchantment, Integer> enchantments;
   public final String nexoId;

   public Reward(
      Reward.Type type,
      int weight,
      int min,
      int max,
      Material mat,
      String displayName,
      List<String> displayLore,
      Material displayMaterial,
      List<String> commands,
      String permission
   ) {
      this(type, weight, min, max, mat, displayName, displayLore, displayMaterial, commands, permission, null, Collections.emptyMap());
   }

   public Reward(
      Reward.Type type,
      int weight,
      int min,
      int max,
      Material mat,
      String displayName,
      List<String> displayLore,
      Material displayMaterial,
      List<String> commands,
      String permission,
      String event
   ) {
      this(type, weight, min, max, mat, displayName, displayLore, displayMaterial, commands, permission, event, Collections.emptyMap());
   }

   public Reward(
      Reward.Type type,
      int weight,
      int min,
      int max,
      Material mat,
      String displayName,
      List<String> displayLore,
      Material displayMaterial,
      List<String> commands,
      String permission,
      String event,
      Map<Enchantment, Integer> enchantments
   ) {
      this(type, weight, min, max, mat, displayName, displayLore, displayMaterial, commands, permission, event, enchantments, null);
   }

   public Reward(
      Reward.Type type,
      int weight,
      int min,
      int max,
      Material mat,
      String displayName,
      List<String> displayLore,
      Material displayMaterial,
      List<String> commands,
      String permission,
      String event,
      Map<Enchantment, Integer> enchantments,
      String nexoId
   ) {
      this.type = type;
      this.weight = weight;
      this.min = min;
      this.max = max;
      this.material = mat;
      this.displayName = displayName;
      this.displayLore = displayLore;
      this.displayMaterial = displayMaterial;
      this.commands = commands;
      this.permission = permission;
      this.event = event;
      this.enchantments = enchantments == null ? Collections.emptyMap() : enchantments;
      this.nexoId = nexoId;
   }

   public int rollAmount(Random rng) {
      return this.max <= this.min ? Math.max(1, this.min) : this.min + rng.nextInt(this.max - this.min + 1);
   }

   public static Reward fromConfig(ConfigurationSection s) {
      if (s == null) {
         return null;
      } else {
         Reward.Type type;
         try {
            type = Reward.Type.valueOf(s.getString("type", "ITEM").toUpperCase());
         } catch (IllegalArgumentException var19) {
            return null;
         }

         int weight = Math.max(1, s.getInt("weight", 10));
         int min = s.getInt("min-amount", 1);
         int max = s.getInt("max-amount", min);
         String permission = s.getString("permission", null);
         String event = s.getString("event", null);
         String nexoId = s.getString("nexo", null);
         Material mat = null;
         if (s.isString("material")) {
            mat = Material.matchMaterial(s.getString("material", ""));
         }

         String name = null;
         List<String> lore = Collections.emptyList();
         Material displayMat = null;
         ConfigurationSection d = s.getConfigurationSection("display");
         if (d != null) {
            name = d.getString("name");
            lore = new ArrayList<>(d.getStringList("lore"));
            String dm = d.getString("material");
            if (dm != null) {
               displayMat = Material.matchMaterial(dm);
            }
         }

         List<String> commands = new ArrayList<>(s.getStringList("commands"));
         Map<Enchantment, Integer> enchantments = new LinkedHashMap<>();
         ConfigurationSection ench = s.getConfigurationSection("enchantments");
         if (ench != null) {
            for (String key : ench.getKeys(false)) {
               Enchantment e = resolveEnchant(key);
               int lvl = ench.getInt(key, 0);
               if (e != null && lvl > 0) {
                  enchantments.put(e, lvl);
               }
            }
         }

         if (type == Reward.Type.ITEM && mat == null) {
            mat = Material.COD;
         }

         return new Reward(type, weight, min, max, mat, name, lore, displayMat, commands, permission, event, enchantments, nexoId);
      }
   }

   private static Enchantment resolveEnchant(String key) {
      if (key == null) {
         return null;
      } else {
         String k = key.toLowerCase(Locale.ROOT).trim();
         NamespacedKey nk = k.contains(":") ? NamespacedKey.fromString(k) : NamespacedKey.minecraft(k);
         return nk == null ? null : (Enchantment)Registry.ENCHANTMENT.get(nk);
      }
   }

   public Map<String, Object> toMap() {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("type", this.type.name());
      map.put("weight", this.weight);
      if (this.min != 1 || this.max != this.min) {
         map.put("min-amount", this.min);
         map.put("max-amount", this.max);
      }

      if (this.material != null) {
         map.put("material", this.material.name());
      }

      if (this.nexoId != null) {
         map.put("nexo", this.nexoId);
      }

      if (!this.commands.isEmpty()) {
         map.put("commands", new ArrayList<>(this.commands));
      }

      if (this.permission != null) {
         map.put("permission", this.permission);
      }

      if (this.event != null) {
         map.put("event", this.event);
      }

      if (this.enchantments != null && !this.enchantments.isEmpty()) {
         Map<String, Object> em = new LinkedHashMap<>();

         for (Entry<Enchantment, Integer> e : this.enchantments.entrySet()) {
            em.put(e.getKey().getKey().getKey(), e.getValue());
         }

         map.put("enchantments", em);
      }

      if (this.displayName != null || this.displayMaterial != null || this.displayLore != null && !this.displayLore.isEmpty()) {
         Map<String, Object> d = new HashMap<>();
         if (this.displayName != null) {
            d.put("name", this.displayName);
         }

         if (this.displayMaterial != null) {
            d.put("material", this.displayMaterial.name());
         }

         if (this.displayLore != null && !this.displayLore.isEmpty()) {
            d.put("lore", new ArrayList<>(this.displayLore));
         }

         map.put("display", d);
      }

      return map;
   }

   public static enum Type {
      ITEM,
      COMMAND,
      MONEY,
      TOKEN,
      XP;
   }
}
