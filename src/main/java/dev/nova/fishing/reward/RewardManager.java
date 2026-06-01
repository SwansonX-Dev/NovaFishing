package dev.nova.fishing.reward;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.ability.AbilityType;
import dev.nova.fishing.rod.RodDef;
import dev.nova.fishing.rod.RodInstance;
import dev.nova.fishing.util.ItemBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class RewardManager {
   private static final int DEFAULT_MAX_ENCHANT_LEVEL = 7;
   private final NovaFishing plugin;
   private int maxEnchantLevel = 7;
   private final EnumMap<RewardTier, List<Reward>> entriesByTier = new EnumMap<>(RewardTier.class);
   private final EnumMap<RewardTier, String> tierNames = new EnumMap<>(RewardTier.class);
   private final EnumMap<RewardTier, String> tierColors = new EnumMap<>(RewardTier.class);
   private final Map<String, RewardManager.JackpotPool> jackpots = new HashMap<>();

   public RewardManager(NovaFishing plugin) {
      this.plugin = plugin;
      this.reload();
   }

   public void reload() {
      this.entriesByTier.clear();
      this.tierNames.clear();
      this.tierColors.clear();
      this.jackpots.clear();
      this.maxEnchantLevel = Math.max(1, this.plugin.configs().rewards().getInt("max-enchant-level", 7));
      this.loadJackpots();
      ConfigurationSection tiers = this.plugin.configs().rewards().getConfigurationSection("tiers");
      if (tiers != null) {
         for (RewardTier t : RewardTier.values()) {
            ConfigurationSection s = tiers.getConfigurationSection(t.name());
            if (s == null) {
               this.entriesByTier.put(t, new ArrayList<>());
            } else {
               this.tierNames.put(t, s.getString("name", t.name()));
               this.tierColors.put(t, s.getString("color", "<white>"));
               List<Reward> list = new ArrayList<>();
               List<?> raw = s.getList("entries");
               if (raw != null) {
                  int i = 0;

                  for (Object o : raw) {
                     if (o instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>)o;
                        ConfigurationSection wrapper = s.createSection("__tmp_entry_" + i, m);
                        Reward r = Reward.fromConfig(wrapper);
                        s.set("__tmp_entry_" + i, null);
                        if (r != null) {
                           list.add(r);
                        }

                        i++;
                     }
                  }
               }

               this.entriesByTier.put(t, list);
            }
         }
      }
   }

   public int getTierCount() {
      return this.entriesByTier.size();
   }

   public List<Reward> getRewards(RewardTier tier) {
      return this.entriesByTier.getOrDefault(tier, new ArrayList<>());
   }

   public String tierLabel(RewardTier t) {
      return this.tierColors.getOrDefault(t, "<white>") + this.tierNames.getOrDefault(t, t.name());
   }

   public void addReward(RewardTier tier, Reward r) {
      this.entriesByTier.computeIfAbsent(tier, k -> new ArrayList<>()).add(r);
      this.save();
   }

   public boolean removeReward(RewardTier tier, int index) {
      List<Reward> list = this.entriesByTier.get(tier);
      if (list != null && index >= 0 && index < list.size()) {
         list.remove(index);
         this.save();
         return true;
      } else {
         return false;
      }
   }

   public void save() {
      ConfigurationSection root = this.plugin.configs().rewards().getConfigurationSection("tiers");
      if (root == null) {
         root = this.plugin.configs().rewards().createSection("tiers");
      }

      for (RewardTier t : RewardTier.values()) {
         ConfigurationSection s = root.getConfigurationSection(t.name());
         if (s == null) {
            s = root.createSection(t.name());
         }

         s.set("name", this.tierNames.getOrDefault(t, t.name()));
         s.set("color", this.tierColors.getOrDefault(t, "<white>"));
         List<Map<String, Object>> serialized = new ArrayList<>();

         for (Reward r : this.getRewards(t)) {
            serialized.add(r.toMap());
         }

         s.set("entries", serialized);
      }

      this.plugin.configs().saveRewardsAsync();
   }

   public RewardTier rollTier(RodInstance rod, Player p, Random rng) {
      RodDef def = rod.def();
      EnumMap<RewardTier, Integer> weights = new EnumMap<>(RewardTier.class);

      for (Entry<RewardTier, Integer> e : def.catchTierWeights().entrySet()) {
         weights.merge(e.getKey(), e.getValue(), Integer::sum);
      }

      if (this.plugin.rods().hasAbility(rod, AbilityType.MYTHIC_FORTUNE)) {
         weights.merge(RewardTier.MYTHIC, weights.getOrDefault(RewardTier.MYTHIC, Integer.valueOf(0)), Integer::sum);
      }

      int luckLevel = 0;
      if (p != null) {
         for (PotionEffect eff : p.getActivePotionEffects()) {
            if (eff.getType().getKey().getKey().equals("luck")) {
               luckLevel = eff.getAmplifier() + 1;
               break;
            }
         }
      }

      if (luckLevel > 0) {
         int drainJunk = weights.getOrDefault(RewardTier.JUNK, Integer.valueOf(0)) * luckLevel / 4;
         int drainCommon = weights.getOrDefault(RewardTier.COMMON, Integer.valueOf(0)) * luckLevel / 4;
         weights.merge(RewardTier.JUNK, Integer.valueOf(-drainJunk), Integer::sum);
         weights.merge(RewardTier.COMMON, Integer.valueOf(-drainCommon), Integer::sum);
         int redistribute = drainJunk + drainCommon;
         int higherTotal = 0;

         for (RewardTier t : List.of(RewardTier.RARE, RewardTier.EPIC, RewardTier.LEGENDARY, RewardTier.MYTHIC)) {
            higherTotal += Math.max(0, weights.getOrDefault(t, Integer.valueOf(0)));
         }

         if (higherTotal > 0 && redistribute > 0) {
            for (RewardTier t : List.of(RewardTier.RARE, RewardTier.EPIC, RewardTier.LEGENDARY, RewardTier.MYTHIC)) {
               int share = redistribute * Math.max(0, weights.getOrDefault(t, Integer.valueOf(0))) / higherTotal;
               weights.merge(t, Integer.valueOf(share), Integer::sum);
            }
         } else if (redistribute > 0) {
            weights.merge(RewardTier.UNCOMMON, Integer.valueOf(redistribute), Integer::sum);
         }

         weights.replaceAll((k, v) -> Math.max(0, v));
      }

      int total = weights.values().stream().mapToInt(Integer::intValue).sum();
      if (total <= 0) {
         return RewardTier.COMMON;
      } else {
         int r = rng.nextInt(total);

         for (Entry<RewardTier, Integer> e : weights.entrySet()) {
            r -= e.getValue();
            if (r < 0) {
               RewardTier picked = e.getKey();
               double upgrade = this.treasureHunterChance(rod);
               if (upgrade > 0.0 && rng.nextDouble() < upgrade) {
                  picked = picked.upgrade();
               }

               if (this.plugin.rods().hasAbility(rod, AbilityType.ASCENDED)) {
                  double ascChance = this.ascendedChance();
                  if (ascChance > 0.0 && rng.nextDouble() < ascChance) {
                     picked = picked.upgrade();
                  }
               }

               return picked;
            }
         }

         return RewardTier.COMMON;
      }
   }

   private void loadJackpots() {
      ConfigurationSection root = this.plugin.configs().rewards().getConfigurationSection("jackpots");
      if (root != null) {
         for (String poolName : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(poolName);
            if (s != null) {
               double chance = s.getDouble("chance", 0.0);
               String name = s.getString("broadcast-name", "<gold><bold>JACKPOT</bold></gold>");
               List<Reward> entries = new ArrayList<>();
               List<?> raw = s.getList("entries");
               if (raw != null) {
                  int i = 0;

                  for (Object o : raw) {
                     if (o instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>)o;
                        ConfigurationSection wrapper = s.createSection("__tmp_jackpot_" + i, m);
                        Reward r = Reward.fromConfig(wrapper);
                        s.set("__tmp_jackpot_" + i, null);
                        if (r != null) {
                           entries.add(r);
                        }

                        i++;
                     }
                  }
               }

               this.jackpots.put(poolName.toLowerCase(Locale.ROOT), new RewardManager.JackpotPool(chance, name, entries));
            }
         }
      }
   }

   public RewardManager.JackpotPool getJackpot(String name) {
      return name == null ? null : this.jackpots.get(name.toLowerCase(Locale.ROOT));
   }

   public Collection<String> getJackpotNames() {
      return new ArrayList<>(this.jackpots.keySet());
   }

   public void addJackpotEntry(String poolName, Reward r) {
      RewardManager.JackpotPool pool = this.getJackpot(poolName);
      if (pool != null) {
         pool.entries.add(r);
         this.saveJackpots();
      }
   }

   public boolean removeJackpotEntry(String poolName, int index) {
      RewardManager.JackpotPool pool = this.getJackpot(poolName);
      if (pool == null) {
         return false;
      } else if (index >= 0 && index < pool.entries.size()) {
         pool.entries.remove(index);
         this.saveJackpots();
         return true;
      } else {
         return false;
      }
   }

   public void updateJackpotEntry(String poolName, int index, Reward r) {
      RewardManager.JackpotPool pool = this.getJackpot(poolName);
      if (pool != null) {
         if (index >= 0 && index < pool.entries.size()) {
            pool.entries.set(index, r);
            this.saveJackpots();
         }
      }
   }

   public void setJackpotChance(String poolName, double chance) {
      RewardManager.JackpotPool pool = this.getJackpot(poolName);
      if (pool != null) {
         pool.chance = Math.max(0.0, Math.min(1.0, chance));
         this.saveJackpots();
      }
   }

   public void setJackpotBroadcastName(String poolName, String broadcast) {
      RewardManager.JackpotPool pool = this.getJackpot(poolName);
      if (pool != null && broadcast != null) {
         pool.broadcastName = broadcast;
         this.saveJackpots();
      }
   }

   public void saveJackpots() {
      ConfigurationSection rewards = this.plugin.configs().rewards();
      rewards.set("jackpots", null);
      ConfigurationSection root = rewards.createSection("jackpots");

      for (Entry<String, RewardManager.JackpotPool> e : this.jackpots.entrySet()) {
         ConfigurationSection s = root.createSection(e.getKey());
         RewardManager.JackpotPool pool = e.getValue();
         s.set("chance", pool.chance);
         s.set("broadcast-name", pool.broadcastName);
         List<Map<String, Object>> serialized = new ArrayList<>();

         for (Reward r : pool.entries) {
            serialized.add(r.toMap());
         }

         s.set("entries", serialized);
      }

      this.plugin.configs().saveRewardsAsync();
   }

   public Reward rollJackpotEntry(RewardManager.JackpotPool pool, Random rng, Player p) {
      if (pool != null && !pool.entries.isEmpty()) {
         List<Reward> candidates = new ArrayList<>();
         int total = 0;

         for (Reward r : pool.entries) {
            if ((r.permission == null || p.hasPermission(r.permission)) && this.plugin.events().isActive(r.event)) {
               candidates.add(r);
               total += r.weight;
            }
         }

         if (!candidates.isEmpty() && total > 0) {
            int n = rng.nextInt(total);

            for (Reward rx : candidates) {
               n -= rx.weight;
               if (n < 0) {
                  return rx;
               }
            }

            return candidates.getLast();
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public Reward rollReward(RewardTier tier, Random rng, Player p) {
      List<Reward> candidates = new ArrayList<>();
      int total = 0;

      for (Reward r : this.getRewards(tier)) {
         if ((r.permission == null || p.hasPermission(r.permission)) && this.plugin.events().isActive(r.event)) {
            candidates.add(r);
            total += r.weight;
         }
      }

      if (!candidates.isEmpty() && total > 0) {
         int n = rng.nextInt(total);

         for (Reward rx : candidates) {
            n -= rx.weight;
            if (n < 0) {
               return rx;
            }
         }

         return candidates.getLast();
      } else {
         return null;
      }
   }

   private double treasureHunterChance(RodInstance rod) {
      double chance = 0.0;
      if (this.plugin.rods().hasAbility(rod, AbilityType.TREASURE_HUNTER_I)) {
         chance = Math.max(chance, 0.05);
      }

      if (this.plugin.rods().hasAbility(rod, AbilityType.TREASURE_HUNTER_II)) {
         chance = Math.max(chance, 0.1);
      }

      if (this.plugin.rods().hasAbility(rod, AbilityType.TREASURE_HUNTER_III)) {
         chance = Math.max(chance, 0.2);
      }

      return chance;
   }

   private double ascendedChance() {
      ConfigurationSection s = this.plugin.configs().rods().getConfigurationSection("abilities.ASCENDED");
      return s == null ? 0.25 : s.getDouble("chance", 0.25);
   }

   public ItemStack applyReward(Player p, RodInstance rod, Reward reward, RewardTier tier) {
      Random rng = ThreadLocalRandom.current();
      int amount = reward.rollAmount(rng);
      switch (reward.type) {
         case ITEM:
            Material mat = reward.material == null ? Material.COD : reward.material;
            if (this.plugin.rods().hasAbility(rod, AbilityType.AUTO_SMELT)) {
               if (mat == Material.COD) {
                  mat = Material.COOKED_COD;
               } else if (mat == Material.SALMON) {
                  mat = Material.COOKED_SALMON;
               }
            }

            ItemStack stack = new ItemStack(mat, amount);
            ItemBuilder b = new ItemBuilder(stack);
            if (reward.displayName != null) {
               b.name(reward.displayName);
            }

            if (reward.displayLore != null && !reward.displayLore.isEmpty()) {
               b.lore(reward.displayLore);
            }

            if (!reward.enchantments.isEmpty()) {
               Map<Enchantment, Integer> capped = new HashMap<>();

               for (Entry<Enchantment, Integer> e : reward.enchantments.entrySet()) {
                  capped.put(e.getKey(), Math.min(e.getValue(), this.maxEnchantLevel));
               }

               b.enchants(capped);
            }

            stack = b.build();
            this.deliverItem(p, rod, stack);
            return stack;
         case COMMAND:
            for (String c : reward.commands) {
               String cmd = c.replace("<player>", p.getName());
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }

            if (reward.displayMaterial != null) {
               ItemBuilder b = new ItemBuilder(reward.displayMaterial);
               if (reward.displayName != null) {
                  b.name(reward.displayName);
               }

               return b.build();
            }

            return null;
         case MONEY:
            if (this.plugin.vault() != null) {
               this.plugin.vault().deposit(p, (double)amount);
            }

            ItemBuilder b = new ItemBuilder(Material.GOLD_NUGGET).name("<gold>$" + amount);
            return b.build();
         case TOKEN:
            int tokens = amount;
            if (this.plugin.rods().hasAbility(rod, AbilityType.WEALTH_MAGNET)) {
               tokens = amount * 2;
            }

            if (this.plugin.rods().hasAbility(rod, AbilityType.PHOENIX_REEL)
               && this.plugin.fishing().getSession(p) != null
               && this.plugin.fishing().getSession(p).lava) {
               tokens = (int)Math.round((double)tokens * 1.25);
            }

            this.plugin.tokens().give(p.getUniqueId(), (long)tokens, false);
            ItemBuilder b = new ItemBuilder(Material.SUNFLOWER).name("<yellow>" + tokens + " Nova Tokens");
            return b.build();
         case XP:
            ItemStack rodStack = rod.stack();
            if (rodStack != null) {
               this.plugin.rods().addXp(p, rodStack, (long)amount);
            }

            ItemBuilder b = new ItemBuilder(Material.EXPERIENCE_BOTTLE).name("<aqua>+" + amount + " XP");
            return b.build();
         default:
            return null;
      }
   }

   private void deliverItem(Player p, RodInstance rod, ItemStack stack) {
      if (this.plugin.rods().hasAbility(rod, AbilityType.MAGNET)) {
         Map<Integer, ItemStack> overflow = p.getInventory().addItem(new ItemStack[]{stack});

         for (ItemStack o : overflow.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), o);
         }
      } else {
         p.getWorld().dropItemNaturally(p.getLocation(), stack);
      }
   }

   public static final class JackpotPool {
      public double chance;
      public String broadcastName;
      public final List<Reward> entries;

      public JackpotPool(double chance, String broadcastName, List<Reward> entries) {
         this.chance = chance;
         this.broadcastName = broadcastName;
         this.entries = entries;
      }
   }
}
