package dev.nova.fishing.bossbar;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.ability.AbilityType;
import dev.nova.fishing.rod.RodInstance;
import dev.nova.fishing.util.TextUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.bossbar.BossBar.Color;
import net.kyori.adventure.bossbar.BossBar.Overlay;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class BossBarManager {
   private final NovaFishing plugin;
   private final Map<UUID, BossBar> bars = new HashMap<>();
   private BukkitTask task;

   public BossBarManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public void start() {
      if (this.task != null) {
         this.task.cancel();
      }

      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 20L, 10L);
   }

   public void stop() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      for (Entry<UUID, BossBar> e : this.bars.entrySet()) {
         Player p = Bukkit.getPlayer(e.getKey());
         if (p != null) {
            p.hideBossBar(e.getValue());
         }
      }

      this.bars.clear();
   }

   public void hide(UUID uuid) {
      BossBar bar = this.bars.remove(uuid);
      if (bar != null) {
         Player p = Bukkit.getPlayer(uuid);
         if (p != null) {
            p.hideBossBar(bar);
         }
      }
   }

   private void tick() {
      for (Player p : Bukkit.getOnlinePlayers()) {
         ItemStack inHand = p.getInventory().getItemInMainHand();
         RodInstance rod = this.plugin.rods().read(inHand);
         if (rod == null) {
            this.hide(p.getUniqueId());
         } else {
            BossBar bar = this.bars.computeIfAbsent(p.getUniqueId(), k -> {
               BossBar b = BossBar.bossBar(TextUtil.mm(" "), 0.0F, Color.YELLOW, Overlay.PROGRESS);
               p.showBossBar(b);
               return b;
            });
            String label;
            float pct;
            if (rod.isMaxLevel()) {
               String prestige = this.plugin.rods().prestigeStars(rod.prestige());
               label = rod.def().displayName() + " <dark_gray>| <gold>MAXED " + prestige + (rod.canPrestige() ? " <gray>(/novafishing prestige)" : "");
               pct = 1.0F;
            } else {
               long need = rod.def().xpRequired(rod.level());
               pct = need > 0L ? Math.min(1.0F, Math.max(0.0F, (float)rod.xp() / (float)need)) : 0.0F;
               String nextAbility = this.nextAbility(rod);
               label = rod.def().displayName()
                  + " <dark_gray>| <yellow>Lv "
                  + rod.level()
                  + "/"
                  + rod.def().maxLevel()
                  + " <dark_gray>| <aqua>"
                  + rod.xp()
                  + "/"
                  + need
                  + (nextAbility != null ? " <dark_gray>| <light_purple>Next: " + nextAbility : "");
            }

            bar.name(TextUtil.mm(label));
            bar.progress(pct);
            bar.color(rod.isMaxLevel() ? Color.PURPLE : Color.YELLOW);
         }
      }

      this.bars.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
   }

   private String nextAbility(RodInstance rod) {
      Integer nextLv = null;
      String nextName = null;

      for (Entry<AbilityType, Integer> e : rod.def().abilityUnlocks().entrySet()) {
         if (e.getValue() > rod.level() && (nextLv == null || e.getValue() < nextLv)) {
            nextLv = e.getValue();
            Object name = this.plugin.rods().abilityProperties(e.getKey()).get("name");
            nextName = name == null ? e.getKey().name() : name.toString();
         }
      }

      return nextName == null ? null : TextUtil.stripTags(nextName) + " (Lv " + nextLv + ")";
   }
}
