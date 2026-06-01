package dev.nova.fishing.ability;

public enum AbilityType {
   LAVA_RESISTANT,
   MAGNET,
   DOUBLE_CATCH,
   TRIPLE_CATCH,
   AUTO_REEL,
   XP_BOOST_I,
   XP_BOOST_II,
   XP_BOOST_III,
   TREASURE_HUNTER_I,
   TREASURE_HUNTER_II,
   TREASURE_HUNTER_III,
   LUCKY_HOOK,
   VOID_WALKER,
   PHOENIX_REEL,
   MYTHIC_FORTUNE,
   ETERNAL_FLAME,
   ASCENDED,
   WEALTH_MAGNET,
   EXPERIENCE_AMP,
   LIGHTNING_ROD,
   AUTO_SMELT;

   public static AbilityType safe(String value) {
      if (value == null) {
         return null;
      } else {
         try {
            return valueOf(value.toUpperCase());
         } catch (IllegalArgumentException var2) {
            return null;
         }
      }
   }
}
