package dev.nova.fishing.hologram;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.util.TextUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

public final class HologramManager implements Listener {
   static final String ENTITY_TAG = "NovaFishingHologram";
   private final NovaFishing plugin;
   private final Map<String, Hologram> holograms = new LinkedHashMap<>();
   private final Map<String, BukkitTask> refreshTasks = new HashMap<>();
   private final File file;
   private final boolean papiAvailable;

   public HologramManager(NovaFishing plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "holograms.yml");
      this.papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
   }

   public void start() {
      this.reapOrphans();
      this.load();
      Bukkit.getPluginManager().registerEvents(this, this.plugin);

      for (Hologram h : this.holograms.values()) {
         this.trySpawn(h);
      }
   }

   public void shutdown() {
      for (BukkitTask t : this.refreshTasks.values()) {
         t.cancel();
      }

      this.refreshTasks.clear();

      for (Hologram h : this.holograms.values()) {
         this.despawn(h);
      }

      this.save();
   }

   public Collection<Hologram> all() {
      return this.holograms.values();
   }

   public Hologram get(String id) {
      return this.holograms.get(id.toLowerCase());
   }

   public Hologram create(String id, Location loc, List<String> initialLines) {
      String key = id.toLowerCase();
      if (this.holograms.containsKey(key)) {
         return null;
      } else {
         Hologram h = new Hologram(key, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
         if (initialLines != null) {
            h.lines().addAll(initialLines);
         }

         this.holograms.put(key, h);
         this.trySpawn(h);
         this.save();
         return h;
      }
   }

   public boolean delete(String id) {
      Hologram h = this.holograms.remove(id.toLowerCase());
      if (h == null) {
         return false;
      } else {
         this.despawn(h);
         this.save();
         return true;
      }
   }

   public boolean move(String id, Location loc) {
      Hologram h = this.get(id);
      if (h == null) {
         return false;
      } else {
         h.setLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
         this.respawn(h);
         this.save();
         return true;
      }
   }

   public boolean setLine(String id, int index, String text) {
      Hologram h = this.get(id);
      if (h == null) {
         return false;
      } else if (index < 0) {
         return false;
      } else {
         while (h.lines().size() <= index) {
            h.lines().add("");
         }

         h.lines().set(index, text);
         this.respawn(h);
         this.save();
         return true;
      }
   }

   public boolean addLine(String id, String text) {
      Hologram h = this.get(id);
      if (h == null) {
         return false;
      } else {
         h.lines().add(text);
         this.respawn(h);
         this.save();
         return true;
      }
   }

   public boolean removeLine(String id, int index) {
      Hologram h = this.get(id);
      if (h == null) {
         return false;
      } else if (index >= 0 && index < h.lines().size()) {
         h.lines().remove(index);
         this.respawn(h);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public boolean setLineHeight(String id, double v) {
      Hologram h = this.get(id);
      if (h == null) {
         return false;
      } else {
         h.setLineHeight(v);
         this.respawn(h);
         this.save();
         return true;
      }
   }

   public boolean setInterval(String id, int ticks) {
      Hologram h = this.get(id);
      if (h == null) {
         return false;
      } else {
         h.setUpdateIntervalTicks(ticks);
         BukkitTask t = this.refreshTasks.remove(id.toLowerCase());
         if (t != null) {
            t.cancel();
         }

         this.scheduleRefresh(h);
         this.save();
         return true;
      }
   }

   public boolean reload(String id) {
      Hologram h = this.get(id);
      if (h == null) {
         return false;
      } else {
         this.respawn(h);
         return true;
      }
   }

   private void trySpawn(Hologram h) {
      World w = Bukkit.getWorld(h.worldName());
      if (w != null) {
         this.despawn(h);
         if (!h.lines().isEmpty()) {
            for (int i = 0; i < h.lines().size(); i++) {
               Location loc = h.lineLocation(i);
               if (loc == null) {
                  return;
               }

               TextDisplay td = (TextDisplay)w.spawn(loc, TextDisplay.class, e -> {
                  e.addScoreboardTag("NovaFishingHologram");
                  e.setBillboard(Billboard.CENTER);
                  e.setPersistent(false);
                  e.setAlignment(TextAlignment.CENTER);
                  e.setSeeThrough(false);
                  e.setShadowed(false);
                  e.setDefaultBackground(true);
               });
               td.text(this.renderLine(h.lines().get(i)));
               h.entityIds().add(td.getUniqueId());
            }

            this.scheduleRefresh(h);
         }
      }
   }

   private void despawn(Hologram h) {
      for (UUID id : h.entityIds()) {
         Entity e = Bukkit.getEntity(id);
         if (e != null) {
            e.remove();
         }
      }

      h.entityIds().clear();
      BukkitTask t = this.refreshTasks.remove(h.id());
      if (t != null) {
         t.cancel();
      }
   }

   private void respawn(Hologram h) {
      this.despawn(h);
      this.trySpawn(h);
   }

   private void scheduleRefresh(Hologram h) {
      int period = Math.max(5, h.updateIntervalTicks());
      BukkitTask task = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> this.refresh(h), (long)period, (long)period);
      this.refreshTasks.put(h.id(), task);
   }

   private void refresh(Hologram h) {
      if (h.entityIds().size() != h.lines().size()) {
         this.respawn(h);
      } else {
         for (int i = 0; i < h.lines().size(); i++) {
            if (!(Bukkit.getEntity(h.entityIds().get(i)) instanceof TextDisplay td)) {
               this.respawn(h);
               return;
            }

            td.text(this.renderLine(h.lines().get(i)));
         }
      }
   }

   private Component renderLine(String raw) {
      if (raw != null && !raw.isEmpty()) {
         String resolved = raw;
         if (this.papiAvailable) {
            try {
               resolved = PlaceholderAPI.setPlaceholders((OfflinePlayer)null, raw);
            } catch (Throwable var4) {
            }
         }

         return TextUtil.mm(resolved);
      } else {
         return Component.empty();
      }
   }

   @EventHandler
   public void onWorldLoad(WorldLoadEvent ev) {
      for (Hologram h : this.holograms.values()) {
         if (h.worldName().equals(ev.getWorld().getName())) {
            this.respawn(h);
         }
      }
   }

   @EventHandler
   public void onWorldUnload(WorldUnloadEvent ev) {
      for (Hologram h : this.holograms.values()) {
         if (h.worldName().equals(ev.getWorld().getName())) {
            h.entityIds().clear();
            BukkitTask t = this.refreshTasks.remove(h.id());
            if (t != null) {
               t.cancel();
            }
         }
      }
   }

   private void reapOrphans() {
      int n = 0;

      for (World w : Bukkit.getWorlds()) {
         for (Entity e : w.getEntitiesByClass(TextDisplay.class)) {
            if (e.getScoreboardTags().contains("NovaFishingHologram")) {
               e.remove();
               n++;
            }
         }
      }

      if (n > 0) {
         this.plugin.getLogger().info("Reaped " + n + " orphan hologram entity/entities.");
      }
   }

   public void load() {
      this.holograms.clear();
      if (this.file.exists()) {
         FileConfiguration cfg = YamlConfiguration.loadConfiguration(this.file);
         ConfigurationSection root = cfg.getConfigurationSection("holograms");
         if (root != null) {
            for (String key : root.getKeys(false)) {
               ConfigurationSection sec = root.getConfigurationSection(key);
               if (sec != null) {
                  try {
                     Hologram h = Hologram.readFrom(key.toLowerCase(), sec);
                     this.holograms.put(h.id(), h);
                  } catch (Exception var7) {
                     this.plugin.getLogger().warning("Skipping bad hologram '" + key + "': " + var7.getMessage());
                  }
               }
            }
         }
      }
   }

   public void save() {
      FileConfiguration cfg = new YamlConfiguration();

      for (Hologram h : this.holograms.values()) {
         ConfigurationSection sec = cfg.createSection("holograms." + h.id());
         h.writeTo(sec);
      }

      try {
         if (!this.file.getParentFile().exists()) {
            this.file.getParentFile().mkdirs();
         }

         cfg.save(this.file);
      } catch (Exception var5) {
         this.plugin.getLogger().warning("Saving holograms.yml: " + var5.getMessage());
      }
   }

   public List<String> buildLeaderboardLines() {
      ConfigurationSection sec = this.plugin.getConfig().getConfigurationSection("holograms.leaderboard");
      int rows = sec == null ? 10 : sec.getInt("rows", 10);
      String title = sec == null ? "<aqua><bold>NOVA FISHING LEADERBOARD" : sec.getString("title", "<aqua><bold>NOVA FISHING LEADERBOARD");
      String subtitle = sec == null ? "" : sec.getString("subtitle", "");
      String defaultFmt = "<white>#<rank> <white><name><dark_gray>: <gray><rod> <yellow><level>";
      String rowFmt = sec == null ? defaultFmt : sec.getString("row-format", defaultFmt);
      String footer = sec == null ? "" : sec.getString("footer", "");
      List<String> out = new ArrayList<>();
      out.add(title);
      if (subtitle != null && !subtitle.isEmpty()) {
         out.add(subtitle);
      }

      for (int r = 1; r <= rows; r++) {
         String name = "%novafishing_top_rod_name_" + r + "%";
         String rod = "%novafishing_top_rod_display_" + r + "%";
         String level = "%novafishing_top_rod_level_" + r + "%";
         String tier = "%novafishing_top_rod_tier_" + r + "%";
         String line = rowFmt.replace("<rank>", String.valueOf(r))
            .replace("<name>", name)
            .replace("<rod>", rod)
            .replace("<level>", level)
            .replace("<tier>", tier);
         out.add(line);
      }

      if (footer != null && !footer.isEmpty()) {
         out.add(footer);
      }

      return out;
   }

   public String defaultLeaderboardId() {
      ConfigurationSection sec = this.plugin.getConfig().getConfigurationSection("holograms.leaderboard");
      return sec == null ? "lb" : sec.getString("default-id", "lb");
   }
}
