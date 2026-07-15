package dev.nova.fishing.api;

import dev.nova.fishing.reward.RewardTier;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player lands a catch with a Nova rod, after the reward has been
 * rolled and applied. NovaFishing cancels the vanilla PlayerFishEvent at HIGH,
 * so external plugins never see a Nova catch through Bukkit's fishing event —
 * this is the supported way to observe one.
 *
 * <p>Purely a notification: the reward is already granted by the time it fires,
 * so it is not cancellable. Fired synchronously on the main thread.
 */
public final class NovaFishingCatchEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final RewardTier tier;
    private final String rodId;
    private final boolean lava;
    private final boolean voidFishing;

    public NovaFishingCatchEvent(@NotNull Player player,
                                 @NotNull RewardTier tier,
                                 @NotNull String rodId,
                                 boolean lava,
                                 boolean voidFishing) {
        super(player);
        this.tier = tier;
        this.rodId = rodId;
        this.lava = lava;
        this.voidFishing = voidFishing;
    }

    /** Rarity tier rolled for this catch, JUNK through MYTHIC. */
    public @NotNull RewardTier getTier() {
        return tier;
    }

    /** Config id of the rod used, as declared in rods.yml. */
    public @NotNull String getRodId() {
        return rodId;
    }

    /** True if the catch came from a lava pool. */
    public boolean isLava() {
        return lava;
    }

    /** True if the catch came from void fishing. */
    public boolean isVoidFishing() {
        return voidFishing;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
