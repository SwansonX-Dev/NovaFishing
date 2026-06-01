package dev.nova.fishing.hologram;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public final class Hologram {
   public static final double DEFAULT_LINE_HEIGHT = 0.28;
   public static final int DEFAULT_INTERVAL_TICKS = 40;
   private final String id;
   private String worldName;
   private double x;
   private double y;
   private double z;
   private double lineHeight;
   private int updateIntervalTicks;
   private final List<String> lines = new ArrayList<>();
   private final List<UUID> entityIds = new ArrayList<>();

   public Hologram(String id, String worldName, double x, double y, double z) {
      this.id = id;
      this.worldName = worldName;
      this.x = x;
      this.y = y;
      this.z = z;
      this.lineHeight = 0.28;
      this.updateIntervalTicks = 40;
   }

   public String id() {
      return this.id;
   }

   public String worldName() {
      return this.worldName;
   }

   public double x() {
      return this.x;
   }

   public double y() {
      return this.y;
   }

   public double z() {
      return this.z;
   }

   public double lineHeight() {
      return this.lineHeight;
   }

   public int updateIntervalTicks() {
      return this.updateIntervalTicks;
   }

   public List<String> lines() {
      return this.lines;
   }

   public List<UUID> entityIds() {
      return this.entityIds;
   }

   public void setLocation(String worldName, double x, double y, double z) {
      this.worldName = worldName;
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public void setLineHeight(double v) {
      this.lineHeight = Math.max(0.05, v);
   }

   public void setUpdateIntervalTicks(int v) {
      this.updateIntervalTicks = Math.max(5, v);
   }

   public Location toLocation() {
      World w = Bukkit.getWorld(this.worldName);
      return w == null ? null : new Location(w, this.x, this.y, this.z);
   }

   public Location lineLocation(int index) {
      Location base = this.toLocation();
      if (base == null) {
         return null;
      } else {
         double offset = (double)(-index) * this.lineHeight;
         return base.clone().add(0.0, offset, 0.0);
      }
   }

   public void writeTo(ConfigurationSection out) {
      out.set("world", this.worldName);
      out.set("x", this.x);
      out.set("y", this.y);
      out.set("z", this.z);
      out.set("line-height", this.lineHeight);
      out.set("update-interval-ticks", this.updateIntervalTicks);
      out.set("lines", new ArrayList<>(this.lines));
   }

   public static Hologram readFrom(String id, ConfigurationSection sec) {
      String world = sec.getString("world", "world");
      double x = sec.getDouble("x");
      double y = sec.getDouble("y");
      double z = sec.getDouble("z");
      Hologram h = new Hologram(id, world, x, y, z);
      h.setLineHeight(sec.getDouble("line-height", 0.28));
      h.setUpdateIntervalTicks(sec.getInt("update-interval-ticks", 40));
      List<String> lines = sec.getStringList("lines");
      h.lines.addAll(lines);
      return h;
   }
}
