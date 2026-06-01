package dev.nova.fishing.challenge;

import dev.nova.fishing.reward.RewardTier;
import java.util.List;

public record Challenge(
   String id, Challenge.Period period, Challenge.Goal goal, RewardTier targetTier, long target, String displayName, String description, Challenge.Reward reward
) {
   public static enum Goal {
      CATCH_ANY,
      CATCH_TIER,
      PRESTIGE_ANY;
   }

   public static enum Period {
      DAILY,
      WEEKLY;
   }

   public static record Reward(Challenge.Reward.Type type, long amount, List<String> commands) {
      public static enum Type {
         TOKEN,
         COMMAND;
      }
   }
}
