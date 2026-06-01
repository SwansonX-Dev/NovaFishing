package dev.nova.fishing.rod;

import dev.nova.fishing.ability.AbilityType;
import dev.nova.fishing.reward.RewardTier;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record RodDef(
   String id,
   String displayName,
   Material material,
   Integer customModelData,
   int maxLevel,
   double baseXp,
   double xpExponent,
   List<String> lore,
   Map<RewardTier, Integer> catchTierWeights,
   Map<AbilityType, Integer> abilityUnlocks,
   int maxPrestige,
   double prestigeXpBonusPerStar,
   String permission,
   RodDef.ParticleTheme particles,
   Map<String, RodDef.RodSkin> skins,
   String upgradeTo,
   long upgradeCost,
   int vanillaXpPerCast
) {
   public long xpRequired(int level) {
      return level >= this.maxLevel ? -1L : (long)Math.floor(this.baseXp * Math.pow((double)(level + 1), this.xpExponent));
   }

   public boolean isMaxLevel(int level) {
      return level >= this.maxLevel;
   }

   public Map<AbilityType, Integer> unlocksByLevel() {
      return this.abilityUnlocks;
   }

   public static record ParticleTheme(String waitingLava, String waitingVoid, String biteLava, String biteWater, String catchBurst) {
      public static final RodDef.ParticleTheme DEFAULT = new RodDef.ParticleTheme(null, null, null, null, null);
   }

   public static record RodSkin(String id, String displayName, int customModelData, String permission, String event) {
   }
}
