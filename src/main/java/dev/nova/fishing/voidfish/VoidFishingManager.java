package dev.nova.fishing.voidfish;

import dev.nova.fishing.NovaFishing;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class VoidFishingManager {
   private final NovaFishing plugin;
   private final Map<String, VoidRegion> regions = new HashMap<>();
   private final Map<UUID, Map<String, Location>> pos1 = new HashMap<>();
   private final Map<UUID, Map<String, Location>> pos2 = new HashMap<>();

   public VoidFishingManager(NovaFishing plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      this.regions.clear();
      ConfigurationSection sec = this.plugin.getConfig().getConfigurationSection("void-regions");
      if (sec != null) {
         for (String k : sec.getKeys(false)) {
            VoidRegion r = VoidRegion.fromConfig(k, sec.getConfigurationSection(k));
            if (r != null) {
               this.regions.put(k.toLowerCase(), r);
            }
         }
      }
   }

   public Collection<VoidRegion> regions() {
      return this.regions.values();
   }

   public boolean isInRegion(Location l) {
      for (VoidRegion r : this.regions.values()) {
         if (r.contains(l)) {
            return true;
         }
      }

      return false;
   }

   public VoidRegion regionAt(Location l) {
      for (VoidRegion r : this.regions.values()) {
         if (r.contains(l)) {
            return r;
         }
      }

      return null;
   }

   public void setPos1(Player p, String name) {
      this.pos1.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(name.toLowerCase(), p.getLocation());
      this.tryComplete(p, name);
   }

   public void setPos2(Player p, String name) {
      this.pos2.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(name.toLowerCase(), p.getLocation());
      this.tryComplete(p, name);
   }

   private void tryComplete(Player p, String name) {
      Location a = this.pos1.getOrDefault(p.getUniqueId(), new HashMap<>()).get(name.toLowerCase());
      Location b = this.pos2.getOrDefault(p.getUniqueId(), new HashMap<>()).get(name.toLowerCase());
      if (a != null && b != null) {
         if (a.getWorld() != null && b.getWorld() != null) {
            if (a.getWorld().equals(b.getWorld())) {
               VoidRegion region = new VoidRegion(
                  name, a.getWorld().getName(), a.getBlockX(), a.getBlockY(), a.getBlockZ(), b.getBlockX(), b.getBlockY(), b.getBlockZ()
               );
               this.regions.put(name.toLowerCase(), region);
               ConfigurationSection sec = this.plugin.getConfig().getConfigurationSection("void-regions");
               if (sec == null) {
                  sec = this.plugin.getConfig().createSection("void-regions");
               }

               sec.set(name, region.toMap());
               this.plugin.saveConfig();
               this.pos1.get(p.getUniqueId()).remove(name.toLowerCase());
               this.pos2.get(p.getUniqueId()).remove(name.toLowerCase());
            }
         }
      }
   }

   public boolean delete(String name) {
      if (this.regions.remove(name.toLowerCase()) == null) {
         return false;
      } else {
         ConfigurationSection sec = this.plugin.getConfig().getConfigurationSection("void-regions");
         if (sec != null) {
            sec.set(name, null);
         }

         this.plugin.saveConfig();
         return true;
      }
   }

   public Map<String, VoidRegion> all() {
      return new LinkedHashMap<>(this.regions);
   }
}
