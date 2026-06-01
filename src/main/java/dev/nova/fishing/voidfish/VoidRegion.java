package dev.nova.fishing.voidfish;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class VoidRegion {
   public final String name;
   public final String world;
   public final int minX;
   public final int minY;
   public final int minZ;
   public final int maxX;
   public final int maxY;
   public final int maxZ;

   public VoidRegion(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
      this.name = name;
      this.world = world;
      this.minX = Math.min(x1, x2);
      this.minY = Math.min(y1, y2);
      this.minZ = Math.min(z1, z2);
      this.maxX = Math.max(x1, x2);
      this.maxY = Math.max(y1, y2);
      this.maxZ = Math.max(z1, z2);
   }

   public boolean contains(Location l) {
      if (l != null && l.getWorld() != null) {
         if (!l.getWorld().getName().equalsIgnoreCase(this.world)) {
            return false;
         } else {
            int x = l.getBlockX();
            int y = l.getBlockY();
            int z = l.getBlockZ();
            return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
         }
      } else {
         return false;
      }
   }

   public static VoidRegion fromConfig(String name, ConfigurationSection s) {
      if (s == null) {
         return null;
      } else {
         World w = Bukkit.getWorld(s.getString("world", ""));
         String world = w != null ? w.getName() : s.getString("world", "world");
         return new VoidRegion(name, world, s.getInt("x1"), s.getInt("y1"), s.getInt("z1"), s.getInt("x2"), s.getInt("y2"), s.getInt("z2"));
      }
   }

   public Map<String, Object> toMap() {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("world", this.world);
      m.put("x1", this.minX);
      m.put("y1", this.minY);
      m.put("z1", this.minZ);
      m.put("x2", this.maxX);
      m.put("y2", this.maxY);
      m.put("z2", this.maxZ);
      return m;
   }
}
