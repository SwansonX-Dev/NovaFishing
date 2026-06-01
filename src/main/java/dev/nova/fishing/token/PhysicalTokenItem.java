package dev.nova.fishing.token;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.util.ItemBuilder;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class PhysicalTokenItem {
   private PhysicalTokenItem() {
   }

   public static ItemStack build(NovaFishing plugin, long amount, int stackSize) {
      ConfigurationSection cfg = plugin.configs().main().getConfigurationSection("physical-token");
      String matName = cfg == null ? "SUNFLOWER" : cfg.getString("material", "SUNFLOWER");
      Material mat = Material.matchMaterial(matName);
      if (mat == null) {
         mat = Material.SUNFLOWER;
      }

      String name = cfg == null
         ? "<gradient:#FFD700:#FFAA00><bold>Nova Token</bold></gradient> <gray>(<yellow><amount></yellow>)"
         : cfg.getString("name", "<gradient:#FFD700:#FFAA00><bold>Nova Token</bold></gradient> <gray>(<yellow><amount></yellow>)");
      List<String> lore = cfg == null ? defaultLore() : cfg.getStringList("lore");
      if (lore.isEmpty()) {
         lore = defaultLore();
      }

      boolean glow = cfg == null || cfg.getBoolean("glow", true);
      Map<String, String> ph = new HashMap<>();
      ph.put("amount", NumberFormat.getNumberInstance(Locale.US).format(amount));
      ItemBuilder ib = new ItemBuilder(mat).name(replace(name, ph)).lore(lore, ph).amount(Math.max(1, stackSize));
      if (glow) {
         ib.glow();
      }

      ItemStack stack = ib.build();
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         PersistentDataContainer pdc = meta.getPersistentDataContainer();
         pdc.set(plugin.tokenItemKey, PersistentDataType.LONG, Math.max(0L, amount));
         stack.setItemMeta(meta);
      }

      return stack;
   }

   public static long valueOf(NovaFishing plugin, ItemStack stack) {
      if (stack != null && stack.hasItemMeta()) {
         ItemMeta meta = stack.getItemMeta();
         PersistentDataContainer pdc = meta.getPersistentDataContainer();
         Long v = (Long)pdc.get(plugin.tokenItemKey, PersistentDataType.LONG);
         return v == null ? -1L : v;
      } else {
         return -1L;
      }
   }

   public static boolean is(NovaFishing plugin, ItemStack stack) {
      return valueOf(plugin, stack) >= 0L;
   }

   private static List<String> defaultLore() {
      List<String> l = new ArrayList<>();
      l.add("<gray>Worth <yellow><amount></yellow> Nova Tokens.");
      l.add("");
      l.add("<gold>Right-click <gray>to redeem.");
      return l;
   }

   private static String replace(String s, Map<String, String> ph) {
      String r = s;

      for (Entry<String, String> e : ph.entrySet()) {
         r = r.replace("<" + e.getKey() + ">", e.getValue());
      }

      return r;
   }
}
