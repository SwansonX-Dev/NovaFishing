package dev.nova.fishing.reward;

public enum RewardTier {
   JUNK,
   COMMON,
   UNCOMMON,
   RARE,
   EPIC,
   LEGENDARY,
   MYTHIC;

   public static RewardTier safe(String s) {
      if (s == null) {
         return null;
      } else {
         try {
            return valueOf(s.toUpperCase());
         } catch (IllegalArgumentException var2) {
            return null;
         }
      }
   }

   public RewardTier upgrade() {
      RewardTier[] v = values();
      return this.ordinal() >= v.length - 1 ? this : v[this.ordinal() + 1];
   }
}
