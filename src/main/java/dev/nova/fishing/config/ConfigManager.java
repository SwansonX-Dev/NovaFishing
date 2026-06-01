package dev.nova.fishing.config;

import dev.nova.fishing.NovaFishing;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ConfigManager {
   private final NovaFishing plugin;
   private FileConfiguration mainCfg;
   private FileConfiguration rodsCfg;
   private FileConfiguration rewardsCfg;
   private FileConfiguration shopCfg;
   private FileConfiguration messagesCfg;

   public ConfigManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public void loadAll() {
      this.plugin.reloadConfig();
      this.mainCfg = this.plugin.getConfig();
      this.rodsCfg = this.loadOrCopy("rods.yml");
      this.rewardsCfg = this.loadOrCopy("rewards.yml");
      this.shopCfg = this.loadOrCopy("shop.yml");
      this.messagesCfg = this.loadOrCopy("messages.yml");
   }

   public void saveShop() {
      File f = new File(this.plugin.getDataFolder(), "shop.yml");

      try {
         this.shopCfg.save(f);
      } catch (Exception var3) {
         this.plugin.getLogger().warning("Saving shop.yml: " + var3.getMessage());
      }
   }

   public void saveMain() {
      this.plugin.saveConfig();
   }

   public void saveRewards() {
      File f = new File(this.plugin.getDataFolder(), "rewards.yml");

      try {
         this.rewardsCfg.save(f);
      } catch (Exception var3) {
         this.plugin.getLogger().warning("Saving rewards.yml: " + var3.getMessage());
      }
   }

   public void saveShopAsync() {
      String snapshot = this.shopCfg.saveToString();
      File f = new File(this.plugin.getDataFolder(), "shop.yml");
      this.writeAsync(f, snapshot, "shop.yml");
   }

   public void saveRewardsAsync() {
      String snapshot = this.rewardsCfg.saveToString();
      File f = new File(this.plugin.getDataFolder(), "rewards.yml");
      this.writeAsync(f, snapshot, "rewards.yml");
   }

   public void saveMainAsync() {
      String snapshot = this.mainCfg.saveToString();
      File f = new File(this.plugin.getDataFolder(), "config.yml");
      this.writeAsync(f, snapshot, "config.yml");
   }

   private void writeAsync(File f, String contents, String label) {
      this.plugin.db().runAsync(() -> {
         try {
            Files.writeString(f.toPath(), contents);
         } catch (Exception var5) {
            this.plugin.getLogger().warning("Saving " + label + " (async): " + var5.getMessage());
         }
      });
   }

   private FileConfiguration loadOrCopy(String name) {
      File f = new File(this.plugin.getDataFolder(), name);
      if (!f.exists()) {
         this.plugin.saveResource(name, false);
      }

      FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

      try (InputStream in = this.plugin.getResource(name)) {
         if (in != null) {
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            cfg.setDefaults(bundled);
         }
      } catch (Exception var9) {
         this.plugin.getLogger().warning("Loading bundled defaults for " + name + ": " + var9.getMessage());
      }

      return cfg;
   }

   public File resetToDefault(String name) {
      File f = new File(this.plugin.getDataFolder(), name);
      File backup = null;
      if (f.exists()) {
         backup = new File(this.plugin.getDataFolder(), name + ".bak-" + System.currentTimeMillis());
         if (!f.renameTo(backup)) {
            this.plugin.getLogger().warning("Failed to back up " + name + " before reset.");
            return null;
         }
      }

      try {
         this.plugin.saveResource(name, true);
      } catch (IllegalArgumentException var5) {
         this.plugin.getLogger().warning("No bundled default for " + name + ": " + var5.getMessage());
         return backup;
      }

      this.loadAll();
      return backup;
   }

   public static List<String> resettableNames() {
      return List.of("config.yml", "messages.yml", "rods.yml", "rewards.yml", "shop.yml", "challenges.yml", "tournaments.yml");
   }

   public FileConfiguration main() {
      return this.mainCfg;
   }

   public FileConfiguration rods() {
      return this.rodsCfg;
   }

   public FileConfiguration rewards() {
      return this.rewardsCfg;
   }

   public FileConfiguration shop() {
      return this.shopCfg;
   }

   public FileConfiguration messages() {
      return this.messagesCfg;
   }

   public String message(String key) {
      String s = this.messagesCfg.getString(key);
      if (s == null) {
         s = "<red>missing-message:" + key;
      }

      return this.prefixFor(key) + s;
   }

   public String rawMessage(String key) {
      String s = this.messagesCfg.getString(key);
      return s == null ? "" : s;
   }

   public String prefixFor(String key) {
      if (key == null) {
         return this.messagesCfg.getString("prefix", "");
      } else {
         return !key.startsWith("token.") && !key.startsWith("shop.")
            ? this.messagesCfg.getString("prefix", "")
            : this.messagesCfg.getString("prefix-token", this.messagesCfg.getString("prefix", ""));
      }
   }
}
