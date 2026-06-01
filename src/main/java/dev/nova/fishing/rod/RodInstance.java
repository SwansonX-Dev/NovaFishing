package dev.nova.fishing.rod;

import org.bukkit.inventory.ItemStack;

public record RodInstance(ItemStack stack, RodDef def, int level, long xp, int prestige, String skinId) {
   public boolean isMaxLevel() {
      return this.def != null && this.def.isMaxLevel(this.level);
   }

   public boolean canPrestige() {
      return this.isMaxLevel() && this.prestige < this.def.maxPrestige();
   }

   public boolean canUpgrade() {
      return this.isMaxLevel() && this.def != null && this.def.upgradeTo() != null && !this.def.upgradeTo().isEmpty();
   }

   public double prestigeMultiplier() {
      return this.def != null && this.prestige > 0 ? 1.0 + (double)this.prestige * this.def.prestigeXpBonusPerStar() : 1.0;
   }
}
