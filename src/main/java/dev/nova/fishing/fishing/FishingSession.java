package dev.nova.fishing.fishing;

import dev.nova.fishing.rod.RodInstance;
import java.util.UUID;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

public final class FishingSession {
   public final UUID playerId;
   public final FishHook hook;
   public final RodInstance rod;
   public final long startMs;
   public final boolean lava;
   public final boolean voidFishing;
   public FishingSession.Phase phase = FishingSession.Phase.WAITING;
   public long biteAtMs;
   public long biteEndMs;
   public int taskId = -1;

   public FishingSession(Player player, FishHook hook, RodInstance rod, boolean lava, boolean voidFishing) {
      this.playerId = player.getUniqueId();
      this.hook = hook;
      this.rod = rod;
      this.startMs = System.currentTimeMillis();
      this.lava = lava;
      this.voidFishing = voidFishing;
   }

   public static enum Phase {
      WAITING,
      BITING,
      DONE;
   }
}
