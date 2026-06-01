package dev.nova.fishing.token;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class ShopCategory {
   public final String id;
   public String displayName;
   public Material icon;
   public int slot;
   public final List<ShopItem> items = new ArrayList<>();

   public ShopCategory(String id, String displayName, Material icon, int slot) {
      this.id = id;
      this.displayName = displayName;
      this.icon = icon == null ? Material.PAPER : icon;
      this.slot = slot;
   }

   public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("display-name", this.displayName);
      m.put("icon", this.icon.name());
      m.put("slot", this.slot);
      List<Map<String, Object>> serialized = new ArrayList<>();

      for (ShopItem i : this.items) {
         serialized.add(i.toMap());
      }

      m.put("items", serialized);
      return m;
   }

   public static ShopCategory fromConfig(String id, ConfigurationSection s) {
      if (s == null) {
         return null;
      } else {
         Material mat = Material.matchMaterial(s.getString("icon", "PAPER"));
         ShopCategory cat = new ShopCategory(id, s.getString("display-name", id), mat == null ? Material.PAPER : mat, s.getInt("slot", 10));
         List<?> rawItems = s.getList("items");
         if (rawItems != null) {
            for (Object o : rawItems) {
               if (o instanceof Map) {
                  Map<?, ?> m = (Map<?, ?>)o;
                  ShopItem it = ShopItem.fromMap(m);
                  if (it != null) {
                     cat.items.add(it);
                  }
               }
            }
         }

         return cat;
      }
   }
}
