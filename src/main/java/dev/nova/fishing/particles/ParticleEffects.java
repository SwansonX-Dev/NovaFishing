package dev.nova.fishing.particles;

import dev.nova.fishing.rod.RodDef;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.Particle.DustOptions;
import org.bukkit.entity.Player;

public final class ParticleEffects {
   private ParticleEffects() {
   }

   private static Particle resolve(String name, Particle fallback) {
      if (name != null && !name.isBlank()) {
         try {
            return Particle.valueOf(name.toUpperCase());
         } catch (IllegalArgumentException var3) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   public static void waitingLava(Location at, RodDef.ParticleTheme theme) {
      World w = at.getWorld();
      if (w != null) {
         w.spawnParticle(resolve(theme == null ? null : theme.waitingLava(), Particle.SMALL_FLAME), at.clone().add(0.0, 0.4, 0.0), 4, 0.15, 0.05, 0.15, 0.005);
         w.spawnParticle(Particle.LAVA, at, 1, 0.1, 0.05, 0.1, 0.0);
      }
   }

   public static void waitingVoid(Location at, RodDef.ParticleTheme theme) {
      World w = at.getWorld();
      if (w != null) {
         w.spawnParticle(resolve(theme == null ? null : theme.waitingVoid(), Particle.PORTAL), at, 6, 0.25, 0.25, 0.25, 0.1);
         w.spawnParticle(Particle.END_ROD, at, 1, 0.1, 0.1, 0.1, 0.01);
      }
   }

   public static void biteIncoming(Location at, boolean lava, RodDef.ParticleTheme theme) {
      World w = at.getWorld();
      if (w != null) {
         Particle p = lava
            ? resolve(theme == null ? null : theme.biteLava(), Particle.FLAME)
            : resolve(theme == null ? null : theme.biteWater(), Particle.BUBBLE_POP);
         w.spawnParticle(p, at, 8, 0.2, 0.15, 0.2, 0.02);
      }
   }

   public static void catchBurst(Player p, Location at, String tierColor, RodDef.ParticleTheme theme) {
      World w = at.getWorld();
      if (w != null) {
         Particle main = resolve(theme == null ? null : theme.catchBurst(), Particle.FIREWORK);
         w.spawnParticle(main, at, 30, 0.4, 0.4, 0.4, 0.3);
         w.spawnParticle(Particle.DUST, at, 30, 0.5, 0.5, 0.5, 0.0, new DustOptions(parseColor(tierColor), 1.2F));
         p.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.5F);
         p.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.4F);
      }
   }

   public static void castingTrail(Location at) {
      World w = at.getWorld();
      if (w != null) {
         w.spawnParticle(Particle.ENCHANT, at, 12, 0.2, 0.2, 0.2, 0.5);
      }
   }

   private static Color parseColor(String tierColor) {
      if (tierColor == null) {
         return Color.fromRGB(16777215);
      } else {
         String s = tierColor.toLowerCase();
         if (s.contains("dark_gray")) {
            return Color.fromRGB(5592405);
         } else if (s.contains("white")) {
            return Color.fromRGB(16777215);
         } else if (s.contains("green")) {
            return Color.fromRGB(5635925);
         } else if (s.contains("aqua")) {
            return Color.fromRGB(5636095);
         } else if (s.contains("light_purple")) {
            return Color.fromRGB(16733695);
         } else if (s.contains("gold")) {
            return Color.fromRGB(16755200);
         } else {
            return !s.contains("ff3300") && !s.contains("ffd700") ? Color.fromRGB(16777215) : Color.fromRGB(16766720);
         }
      }
   }
}
