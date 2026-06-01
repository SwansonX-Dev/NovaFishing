package dev.nova.fishing.rod;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.ability.AbilityType;
import dev.nova.fishing.reward.RewardTier;
import dev.nova.fishing.util.ItemBuilder;
import dev.nova.fishing.util.TextUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class RodManager {
   private final NovaFishing plugin;
   private final Map<String, RodDef> rods = new LinkedHashMap<>();
   private final Map<AbilityType, Map<String, Object>> abilityProps = new HashMap<>();
   private final Map<String, Integer> tierIndices = new HashMap<>();

   public RodManager(NovaFishing plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      this.rods.clear();
      this.abilityProps.clear();
      this.tierIndices.clear();
      FileConfiguration cfg = this.plugin.configs().rods();
      ConfigurationSection rodsSec = cfg.getConfigurationSection("rods");
      if (rodsSec != null) {
         for (String id : rodsSec.getKeys(false)) {
            ConfigurationSection s = rodsSec.getConfigurationSection(id);
            if (s != null) {
               try {
                  this.rods.put(id, this.parseRod(id, s));
               } catch (Exception var11) {
                  this.plugin.getLogger().warning("rods.yml: failed to load rod '" + id + "': " + var11.getMessage());
               }
            }
         }
      }

      ConfigurationSection abSec = cfg.getConfigurationSection("abilities");
      if (abSec != null) {
         for (String key : abSec.getKeys(false)) {
            AbilityType t = AbilityType.safe(key);
            if (t != null) {
               ConfigurationSection s = abSec.getConfigurationSection(key);
               if (s != null) {
                  Map<String, Object> props = new HashMap<>();

                  for (String k : s.getKeys(false)) {
                     props.put(k, s.get(k));
                  }

                  this.abilityProps.put(t, props);
               }
            }
         }
      }
   }

   private RodDef parseRod(String id, ConfigurationSection s) {
      Material mat = Material.matchMaterial(s.getString("material", "FISHING_ROD"));
      if (mat == null) {
         mat = Material.FISHING_ROD;
      }

      Integer cmd = s.contains("custom-model-data") ? s.getInt("custom-model-data") : null;
      Map<RewardTier, Integer> weights = new EnumMap<>(RewardTier.class);
      ConfigurationSection w = s.getConfigurationSection("catch-tier-weights");
      if (w != null) {
         for (String k : w.getKeys(false)) {
            RewardTier rt = RewardTier.safe(k);
            if (rt != null) {
               weights.put(rt, Math.max(0, w.getInt(k)));
            }
         }
      }

      EnumMap<AbilityType, Integer> unlocks = new EnumMap<>(AbilityType.class);
      ConfigurationSection a = s.getConfigurationSection("abilities");
      if (a != null) {
         for (String kx : a.getKeys(false)) {
            AbilityType t = AbilityType.safe(kx);
            if (t != null) {
               unlocks.put(t, a.getInt(kx));
            }
         }
      }

      int maxPrestige = s.getInt("max-prestige", 10);
      double presBonus = s.getDouble("prestige-xp-bonus-per-star", 0.1);
      String permission = s.getString("permission", null);
      RodDef.ParticleTheme theme = RodDef.ParticleTheme.DEFAULT;
      ConfigurationSection ps = s.getConfigurationSection("particles");
      if (ps != null) {
         theme = new RodDef.ParticleTheme(
            ps.getString("waiting-lava"), ps.getString("waiting-void"), ps.getString("bite-lava"), ps.getString("bite-water"), ps.getString("catch-burst")
         );
      }

      Map<String, RodDef.RodSkin> skins = new LinkedHashMap<>();
      ConfigurationSection sk = s.getConfigurationSection("skins");
      if (sk != null) {
         for (String skId : sk.getKeys(false)) {
            ConfigurationSection ss = sk.getConfigurationSection(skId);
            if (ss != null) {
               skins.put(
                  skId,
                  new RodDef.RodSkin(
                     skId,
                     ss.getString("display-name", "<gray>" + skId),
                     ss.getInt("custom-model-data", 0),
                     ss.getString("permission", null),
                     ss.getString("event", null)
                  )
               );
            }
         }
      }

      String upgradeTo = s.getString("upgrade-to", null);
      long upgradeCost = s.getLong("upgrade-cost", 0L);
      int vanillaXpPerCast = Math.max(0, s.getInt("vanilla-xp-per-cast", 0));
      return new RodDef(
         id,
         s.getString("display-name", id),
         mat,
         cmd,
         s.getInt("max-level", 25),
         s.getDouble("base-xp", 50.0),
         s.getDouble("xp-exponent", 1.5),
         s.getStringList("lore"),
         weights,
         unlocks,
         maxPrestige,
         presBonus,
         permission,
         theme,
         skins,
         upgradeTo,
         upgradeCost,
         vanillaXpPerCast
      );
   }

   public Collection<RodDef> getRods() {
      return this.rods.values();
   }

   public Set<String> getRodIds() {
      return this.rods.keySet();
   }

   public RodDef getRod(String id) {
      return this.rods.get(id);
   }

   public int tierIndex(String rodId) {
      if (rodId != null && this.rods.containsKey(rodId)) {
         Integer cached = this.tierIndices.get(rodId);
         if (cached != null) {
            return cached;
         } else {
            Map<String, String> parents = new HashMap<>();

            for (RodDef d : this.rods.values()) {
               if (d.upgradeTo() != null && this.rods.containsKey(d.upgradeTo())) {
                  parents.put(d.upgradeTo(), d.id());
               }
            }

            for (String id : this.rods.keySet()) {
               int depth = 0;
               String cur = id;

               for (Set<String> seen = new HashSet<>(); parents.containsKey(cur) && seen.add(cur); depth++) {
                  cur = parents.get(cur);
               }

               this.tierIndices.put(id, depth);
            }

            return this.tierIndices.getOrDefault(rodId, 0);
         }
      } else {
         return -1;
      }
   }

   public Map<String, Object> abilityProperties(AbilityType type) {
      return this.abilityProps.getOrDefault(type, Collections.emptyMap());
   }

   public boolean canUse(Player p, RodDef def) {
      return def == null || def.permission() == null || p.hasPermission(def.permission());
   }

   public ItemStack createRod(String rodId, int level, long xp) {
      return this.createRod(rodId, level, xp, 0, null);
   }

   public ItemStack createRod(String rodId, int level, long xp, int prestige, String skinId) {
      RodDef def = this.rods.get(rodId);
      if (def == null) {
         return null;
      } else {
         ItemBuilder b = new ItemBuilder(def.material());
         b.name(def.displayName());
         Integer cmd = this.resolveCmd(def, skinId);
         if (cmd != null) {
            b.customModelData(cmd);
         }

         ItemStack stack = b.build();
         this.writeRodTags(stack, rodId, level, xp, prestige, skinId);
         this.renderLore(stack, def, level, xp, prestige, skinId);
         return stack;
      }
   }

   private Integer resolveCmd(RodDef def, String skinId) {
      if (this.plugin.getConfig().getBoolean("settings.vanilla-rods", false)) {
         return null;
      } else {
         if (skinId != null) {
            RodDef.RodSkin skin = def.skins().get(skinId);
            if (skin != null) {
               return skin.customModelData();
            }
         }

         return def.customModelData();
      }
   }

   public void writeRodTags(ItemStack stack, String rodId, int level, long xp, int prestige, String skinId) {
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         PersistentDataContainer pdc = meta.getPersistentDataContainer();
         pdc.set(this.plugin.rodTypeKey, PersistentDataType.STRING, rodId);
         pdc.set(this.plugin.rodLevelKey, PersistentDataType.INTEGER, level);
         pdc.set(this.plugin.rodXpKey, PersistentDataType.LONG, xp);
         pdc.set(this.plugin.rodPrestigeKey, PersistentDataType.INTEGER, prestige);
         if (skinId != null) {
            pdc.set(this.plugin.rodSkinKey, PersistentDataType.STRING, skinId);
         } else {
            pdc.remove(this.plugin.rodSkinKey);
         }

         this.applyRodMetaPolish(meta);
         stack.setItemMeta(meta);
      }
   }

   private void applyRodMetaPolish(ItemMeta meta) {
      if (meta.isUnbreakable()) {
         meta.setUnbreakable(false);
      }

      if (this.plugin.getConfig().getBoolean("settings.vanilla-rods", false)) {
         try {
            meta.setCustomModelData(null);
         } catch (Throwable var4) {
         }
      }

      try {
         meta.addEnchant(Enchantment.UNBREAKING, 32767, true);
      } catch (Throwable var3) {
         meta.setUnbreakable(true);
      }

      meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_DYE});
   }

   public int polishExistingRods(Player p) {
      int polished = 0;
      ItemStack[] contents = p.getInventory().getContents();

      for (ItemStack stack : contents) {
         if (stack != null && this.isNovaRod(stack)) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
               this.applyRodMetaPolish(meta);
               stack.setItemMeta(meta);
               polished++;
            }
         }
      }

      return polished;
   }

   public RodInstance read(ItemStack stack) {
      if (stack == null) {
         return null;
      } else {
         ItemMeta meta = stack.getItemMeta();
         if (meta == null) {
            return null;
         } else {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            String type = (String)pdc.get(this.plugin.rodTypeKey, PersistentDataType.STRING);
            if (type == null) {
               return null;
            } else {
               RodDef def = this.rods.get(type);
               if (def == null) {
                  return null;
               } else {
                  int level = (Integer)pdc.getOrDefault(this.plugin.rodLevelKey, PersistentDataType.INTEGER, 1);
                  long xp = (Long)pdc.getOrDefault(this.plugin.rodXpKey, PersistentDataType.LONG, 0L);
                  int prestige = (Integer)pdc.getOrDefault(this.plugin.rodPrestigeKey, PersistentDataType.INTEGER, 0);
                  String skin = (String)pdc.get(this.plugin.rodSkinKey, PersistentDataType.STRING);
                  return new RodInstance(stack, def, level, xp, prestige, skin);
               }
            }
         }
      }
   }

   public boolean isNovaRod(ItemStack stack) {
      return this.read(stack) != null;
   }

   public void giveRod(Player p, String rodId, int level, long xp) {
      ItemStack rod = this.createRod(rodId, level, xp);
      if (rod != null) {
         Map<Integer, ItemStack> leftover = p.getInventory().addItem(new ItemStack[]{rod});
         if (!leftover.isEmpty()) {
            leftover.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
         }
      }
   }

   public RodInstance addXp(Player p, ItemStack stack, long xpDelta) {
      RodInstance r = this.read(stack);
      if (r == null) {
         return null;
      } else {
         RodDef def = r.def();
         long boosted = Math.round((double)xpDelta * r.prestigeMultiplier());
         long newXp = r.xp() + boosted;
         int newLv = r.level();
         boolean dingedMax = false;
         boolean levelChanged = false;

         while (newLv < def.maxLevel()) {
            long need = def.xpRequired(newLv);
            if (need <= 0L || newXp < need) {
               break;
            }

            newXp -= need;
            newLv++;
            levelChanged = true;

            for (Entry<AbilityType, Integer> ue : def.abilityUnlocks().entrySet()) {
               if (ue.getValue() == newLv) {
                  Map<String, String> ph = new HashMap<>();
                  ph.put("ability", this.abilityDisplayName(ue.getKey()));
                  p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.ability-unlocked"), ph));
               }
            }

            if (newLv == def.maxLevel()) {
               newXp = 0L;
               dingedMax = true;
            }
         }

         if (newLv >= def.maxLevel()) {
            newLv = def.maxLevel();
            newXp = 0L;
         }

         this.writeRodTags(stack, def.id(), newLv, newXp, r.prestige(), r.skinId());
         this.renderLore(stack, def, newLv, newXp, r.prestige(), r.skinId());
         if (levelChanged) {
            Map<String, String> ph = new HashMap<>();
            ph.put("rod", TextUtil.legacy(def.displayName()));
            ph.put("level", String.valueOf(newLv));
            p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.level-up"), ph));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.1F);
         }

         int currentTier = this.tierIndex(def.id());
         if (currentTier >= 0) {
            String stored = this.plugin.db().getHighestMaxRod(p.getUniqueId());
            int storedTier = stored == null ? -1 : this.tierIndex(stored);
            UUID uid = p.getUniqueId();
            String rodId = def.id();
            int lv = newLv;
            if (currentTier > storedTier) {
               this.plugin.db().runAsync(() -> {
                  this.plugin.db().setHighestMaxRod(uid, rodId);
                  this.plugin.db().setCurrentRodLevel(uid, lv);
               });
            } else if (currentTier == storedTier) {
               this.plugin.db().runAsync(() -> this.plugin.db().setCurrentRodLevel(uid, lv));
            }
         }

         if (dingedMax
            && this.plugin.getConfig().getBoolean("settings.broadcast.max-level", true)
            && !p.hasPermission("novafishing.broadcast.bypass")
            && this.plugin.db().recordBroadcast(p.getUniqueId(), def.id())) {
            Map<String, String> ph = new HashMap<>();
            ph.put("player", p.getName());
            ph.put("rod", TextUtil.legacy(def.displayName()));
            Component bc = TextUtil.mm(this.plugin.configs().rawMessage("rod.max-level-broadcast"), ph);
            Bukkit.broadcast(bc);
            if (this.plugin.discord() != null) {
               this.plugin.discord().postMaxLevel(p.getName(), def.id(), TextUtil.stripTags(def.displayName()));
            }
         }

         return new RodInstance(stack, def, newLv, newXp, r.prestige(), r.skinId());
      }
   }

   public boolean prestige(Player p, ItemStack stack) {
      RodInstance r = this.read(stack);
      if (r == null) {
         return false;
      } else if (!r.canPrestige()) {
         return false;
      } else {
         int newPrestige = r.prestige() + 1;
         this.writeRodTags(stack, r.def().id(), 1, 0L, newPrestige, r.skinId());
         this.renderLore(stack, r.def(), 1, 0L, newPrestige, r.skinId());
         UUID prestigeUid = p.getUniqueId();
         String prestigeRodId = r.def().id();
         this.plugin.db().runAsync(() -> this.plugin.db().bumpPrestige(prestigeUid, prestigeRodId, newPrestige));
         if (this.tierIndex(r.def().id()) == this.tierIndex(this.plugin.db().getHighestMaxRod(p.getUniqueId()))) {
            this.plugin.db().runAsync(() -> this.plugin.db().setCurrentRodLevel(prestigeUid, 1));
         }

         Map<String, String> ph = new HashMap<>();
         ph.put("rod", TextUtil.legacy(r.def().displayName()));
         ph.put("prestige", String.valueOf(newPrestige));
         ph.put("player", p.getName());
         p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.prestige"), ph));
         Bukkit.broadcast(TextUtil.mm(this.plugin.configs().rawMessage("rod.prestige-broadcast"), ph));
         p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
         return true;
      }
   }

   public RodManager.UpgradeResult upgrade(Player p, ItemStack stack) {
      RodInstance r = this.read(stack);
      if (r == null) {
         return RodManager.UpgradeResult.NO_UPGRADE_PATH;
      } else if (!r.isMaxLevel()) {
         return RodManager.UpgradeResult.NOT_MAX_LEVEL;
      } else {
         String nextId = r.def().upgradeTo();
         if (nextId != null && !nextId.isEmpty()) {
            RodDef next = this.rods.get(nextId);
            if (next == null) {
               return RodManager.UpgradeResult.UNKNOWN_TARGET;
            } else if (!this.canUse(p, next)) {
               return RodManager.UpgradeResult.NO_PERMISSION;
            } else {
               long cost = r.def().upgradeCost();
               if (cost > 0L && !this.plugin.tokens().take(p.getUniqueId(), cost)) {
                  return RodManager.UpgradeResult.INSUFFICIENT_TOKENS;
               } else {
                  ItemStack fresh = this.createRod(next.id(), 1, 0L, 0, null);
                  if (fresh != null) {
                     stack.setType(fresh.getType());
                     stack.setItemMeta(fresh.getItemMeta());
                  }

                  UUID upUid = p.getUniqueId();
                  String upRodId = next.id();
                  this.plugin.db().runAsync(() -> {
                     this.plugin.db().setHighestMaxRod(upUid, upRodId);
                     this.plugin.db().setCurrentRodLevel(upUid, 1);
                  });
                  Map<String, String> ph = new HashMap<>();
                  ph.put("player", p.getName());
                  ph.put("from", TextUtil.legacy(r.def().displayName()));
                  ph.put("to", TextUtil.legacy(next.displayName()));
                  p.sendMessage(TextUtil.mm(this.plugin.configs().message("rod.upgraded"), ph));
                  Bukkit.broadcast(TextUtil.mm(this.plugin.configs().rawMessage("rod.upgrade-broadcast"), ph));
                  p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8F, 1.2F);
                  p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
                  return RodManager.UpgradeResult.OK;
               }
            }
         } else {
            return RodManager.UpgradeResult.NO_UPGRADE_PATH;
         }
      }
   }

   public boolean setSkin(Player p, ItemStack stack, String skinId) {
      RodInstance r = this.read(stack);
      if (r == null) {
         return false;
      } else {
         if (skinId != null) {
            RodDef.RodSkin sk = r.def().skins().get(skinId);
            if (sk == null) {
               return false;
            }

            if (sk.permission() != null && !p.hasPermission(sk.permission())) {
               return false;
            }

            if (!this.plugin.events().isActive(sk.event())) {
               return false;
            }
         }

         this.writeRodTags(stack, r.def().id(), r.level(), r.xp(), r.prestige(), skinId);
         this.renderLore(stack, r.def(), r.level(), r.xp(), r.prestige(), skinId);
         return true;
      }
   }

   public Map<String, RodDef.RodSkin> availableSkins(RodDef def) {
      Map<String, RodDef.RodSkin> out = new LinkedHashMap<>();

      for (RodDef.RodSkin sk : def.skins().values()) {
         if (this.plugin.events().isActive(sk.event())) {
            out.put(sk.id(), sk);
         }
      }

      return out;
   }

   private String abilityDisplayName(AbilityType t) {
      Object name = this.abilityProperties(t).get("name");
      return name == null ? t.name() : name.toString();
   }

   public void renderLore(ItemStack stack, RodDef def, int level, long xp, int prestige, String skinId) {
      ItemMeta meta = stack.getItemMeta();
      if (meta != null) {
         long need = def.xpRequired(level);
         Map<String, String> ph = new HashMap<>();
         ph.put("level", String.valueOf(level));
         ph.put("max_level", String.valueOf(def.maxLevel()));
         ph.put("xp", String.valueOf(xp));
         ph.put("xp_required", need < 0L ? "MAX" : String.valueOf(need));
         ph.put("prestige", String.valueOf(prestige));
         ph.put("prestige_stars", this.prestigeStars(prestige));
         List<String> templateLines = def.lore();
         List<Component> lines = new ArrayList<>();

         for (String l : templateLines) {
            if (l.contains("<abilities>")) {
               List<Component> abLines = this.renderAbilities(def, level);
               if (abLines.isEmpty()) {
                  lines.add(TextUtil.mm("<dark_gray>(no abilities yet)"));
               } else {
                  lines.addAll(abLines);
               }
            } else {
               lines.add(TextUtil.mm(TextUtil.replace(l, ph)));
            }
         }

         if (prestige > 0) {
            lines.add(TextUtil.mm(""));
            lines.add(
               TextUtil.mm(
                  "<gold>Prestige: "
                     + this.prestigeStars(prestige)
                     + " <gray>(+"
                     + (int)Math.round((double)prestige * def.prestigeXpBonusPerStar() * 100.0)
                     + "% XP)"
               )
            );
         }

         if (skinId != null) {
            RodDef.RodSkin sk = def.skins().get(skinId);
            if (sk != null) {
               lines.add(TextUtil.mm("<light_purple>Skin: <gray>" + TextUtil.stripTags(sk.displayName())));
            }
         }

         if (level >= def.maxLevel() && def.upgradeTo() != null && !def.upgradeTo().isEmpty()) {
            RodDef next = this.rods.get(def.upgradeTo());
            if (next != null) {
               lines.add(TextUtil.mm(""));
               String costPart = def.upgradeCost() > 0L ? " <gray>(<yellow>" + def.upgradeCost() + " Tokens</yellow>)" : " <gray>(free)";
               lines.add(TextUtil.mm("<green>>> Upgrade to " + next.displayName() + "<green> with <yellow>/novafishing upgrade</yellow>" + costPart));
            }
         }

         meta.lore(lines);
         String name = def.displayName();
         if (skinId != null) {
            RodDef.RodSkin sk = def.skins().get(skinId);
            if (sk != null) {
               name = sk.displayName();
            }
         }

         if (prestige > 0) {
            name = "<gold>" + this.prestigeStars(prestige) + " " + name;
         }

         meta.displayName(TextUtil.mm(name));
         this.applyRodMetaPolish(meta);
         stack.setItemMeta(meta);
      }
   }

   public String prestigeStars(int prestige) {
      if (prestige <= 0) {
         return "";
      } else {
         return prestige <= 5 ? "*".repeat(prestige) : "*" + prestige;
      }
   }

   private List<Component> renderAbilities(RodDef def, int level) {
      List<Component> out = new ArrayList<>();

      for (Entry<AbilityType, Integer> e : def.unlocksByLevel().entrySet()) {
         AbilityType t = e.getKey();
         int unlockLv = e.getValue();
         String name = this.abilityDisplayName(t);
         if (level >= unlockLv) {
            out.add(TextUtil.mm(" <green>[+] " + name));
         } else {
            out.add(TextUtil.mm(" <dark_gray>[X] <gray>" + TextUtil.stripTags(name) + " <dark_gray>(Lv " + unlockLv + ")"));
         }
      }

      return out;
   }

   public boolean hasAbility(RodInstance rod, AbilityType type) {
      if (rod == null) {
         return false;
      } else {
         Integer unlockLv = rod.def().unlocksByLevel().get(type);
         return unlockLv != null && rod.level() >= unlockLv;
      }
   }

   public static enum UpgradeResult {
      OK,
      NOT_MAX_LEVEL,
      NO_UPGRADE_PATH,
      UNKNOWN_TARGET,
      INSUFFICIENT_TOKENS,
      NO_PERMISSION;
   }
}
