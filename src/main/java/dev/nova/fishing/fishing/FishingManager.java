package dev.nova.fishing.fishing;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.ability.AbilityType;
import dev.nova.fishing.particles.ParticleEffects;
import dev.nova.fishing.reward.Reward;
import dev.nova.fishing.reward.RewardManager;
import dev.nova.fishing.reward.RewardTier;
import dev.nova.fishing.rod.RodInstance;
import dev.nova.fishing.util.TextUtil;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class FishingManager {
   private final NovaFishing plugin;
   private final Map<UUID, FishingSession> sessions = new ConcurrentHashMap<>();
   private final Map<UUID, Long> catchCooldowns = new ConcurrentHashMap<>();
   private final Set<UUID> activeCastHooks = ConcurrentHashMap.newKeySet();
   private final Random rng = new Random();
   private static final String FILL = "▰";
   private static final String EMPTY = "▱";
   private static final String[] PULSE = new String[]{"◆◇◇◇◇", "◇◆◇◇◇", "◇◇◆◇◇", "◇◇◇◆◇", "◇◇◇◇◆", "◇◇◇◆◇", "◇◇◆◇◇", "◇◆◇◇◇"};

   public FishingManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public void shutdown() {
      this.sessions.values().forEach(s -> {
         if (s.taskId != -1) {
            Bukkit.getScheduler().cancelTask(s.taskId);
         }
      });
      this.sessions.clear();
   }

   public FishingSession getSession(Player p) {
      return this.sessions.get(p.getUniqueId());
   }

   public void beginCast(final Player p, final FishHook hook, RodInstance rod) {
      if (hook != null) {
         if (this.activeCastHooks.add(hook.getUniqueId())) {
            try {
               hook.setMinWaitTime(36000);
               hook.setMaxWaitTime(36000);
               hook.setApplyLure(false);
            } catch (Throwable var7) {
            }

            final RodInstance capturedRod = rod;
            final UUID hookId = hook.getUniqueId();
            (new BukkitRunnable() {
                  int waits = 0;

                  public void run() {
                     if (hook == null || hook.isDead()) {
                        FishingManager.this.activeCastHooks.remove(hookId);
                        this.cancel();
                     } else if (!p.isOnline()) {
                        hook.remove();
                        FishingManager.this.activeCastHooks.remove(hookId);
                        this.cancel();
                     } else {
                        Block b = hook.getLocation().getBlock();
                        Block above = b.getRelative(0, 1, 0);
                        boolean clippedThroughLava = above.getType() == Material.LAVA
                           && b.getType() != Material.LAVA
                           && b.getType() != Material.WATER
                           && !b.isEmpty();
                        boolean inLava = FishingManager.safeInLava(hook) || b.getType() == Material.LAVA || clippedThroughLava;
                        boolean inWater = FishingManager.safeInWater(hook) || b.getType() == Material.WATER;
                        boolean inAir = !inLava && !inWater && b.isEmpty();
                        boolean inVoidRegion = inAir && FishingManager.this.plugin.voids().isInRegion(hook.getLocation());
                        boolean lavaAnywhere = FishingManager.this.plugin.getConfig().getBoolean("settings.allow-lava-anywhere", true);
                        boolean allowWater = FishingManager.this.plugin.getConfig().getBoolean("settings.allow-water-fishing", true);
                        if (inLava
                           && !lavaAnywhere
                           && !FishingManager.this.plugin.voids().isInRegion(hook.getLocation())
                           && hook.getWorld().getEnvironment() != Environment.NETHER) {
                           p.sendActionBar(TextUtil.mm(FishingManager.this.plugin.configs().rawMessage("fishing.bad-location")));
                           hook.remove();
                           FishingManager.this.activeCastHooks.remove(hookId);
                           this.cancel();
                        } else if (inLava
                           && FishingManager.this.plugin.getConfig().getBoolean("settings.lava-requires-ability", true)
                           && !FishingManager.this.plugin.rods().hasAbility(capturedRod, AbilityType.LAVA_RESISTANT)) {
                           p.sendActionBar(TextUtil.mm("<red>This rod can't survive lava — unlock <yellow>Lava Resistant</yellow> on an Iron rod or higher."));
                           hook.remove();
                           FishingManager.this.activeCastHooks.remove(hookId);
                           this.cancel();
                        } else if (inVoidRegion
                           && !FishingManager.this.plugin.rods().hasAbility(capturedRod, AbilityType.VOID_WALKER)
                           && !p.hasPermission("novafishing.voidfish")) {
                           FishingManager.this.activeCastHooks.remove(hookId);
                           this.cancel();
                        } else if (!inLava && !inVoidRegion && (!inWater || !allowWater)) {
                           if (++this.waits > 100) {
                              p.sendActionBar(TextUtil.mm("<red>Cast into water, lava, or a void region to fish."));
                              FishingManager.this.activeCastHooks.remove(hookId);
                              this.cancel();
                           }
                        } else {
                           FishingManager.this.startSession(p, hook, capturedRod, inLava, inVoidRegion);
                           FishingManager.this.activeCastHooks.remove(hookId);
                           this.cancel();
                        }
                     }
                  }
               })
               .runTaskTimer(this.plugin, 1L, 1L);
         }
      }
   }

   private static boolean safeInWater(FishHook h) {
      try {
         return h.isInWater();
      } catch (Throwable var2) {
         return false;
      }
   }

   private static boolean safeInLava(FishHook h) {
      try {
         return h.isInLava();
      } catch (Throwable var2) {
         return false;
      }
   }

   private static void clearStuckState(FishHook hook) {
      try {
         Object handle = hook.getClass().getMethod("getHandle").invoke(hook);
         setBoolean(handle, "inGround", false);
         setBoolean(handle, "noPhysics", true);
      } catch (Throwable var2) {
      }
   }

   private static void setBoolean(Object obj, String field, boolean value) {
      Class<?> c = obj.getClass();

      while (c != null) {
         try {
            Field f = c.getDeclaredField(field);
            f.setAccessible(true);
            f.setBoolean(obj, value);
            return;
         } catch (NoSuchFieldException var5) {
            c = c.getSuperclass();
         } catch (Throwable var6) {
            return;
         }
      }
   }

   public void startSession(Player p, FishHook hook, RodInstance rod, boolean lava, boolean voidFishing) {
      FishingSession existing = this.sessions.remove(p.getUniqueId());
      if (existing != null && existing.taskId != -1) {
         Bukkit.getScheduler().cancelTask(existing.taskId);
      }

      final FishingSession session = new FishingSession(p, hook, rod, lava, voidFishing);
      int xpPerCast = rod.def().vanillaXpPerCast();
      if (xpPerCast > 0) {
         p.giveExp(xpPerCast);
      }

      double minBiteSec = this.plugin.getConfig().getDouble("settings.cast-time-min-seconds", 3.0);
      double maxBiteSec = this.plugin.getConfig().getDouble("settings.cast-time-max-seconds", 13.0);
      if (maxBiteSec < minBiteSec) {
         maxBiteSec = minBiteSec;
      }

      double mult = this.plugin.rods().hasAbility(rod, AbilityType.LUCKY_HOOK) ? 0.5 : 1.0;
      double biteSec = (minBiteSec + this.rng.nextDouble() * (maxBiteSec - minBiteSec)) * mult;
      session.biteAtMs = System.currentTimeMillis() + Math.round(biteSec * 1000.0);
      session.biteEndMs = session.biteAtMs + 3000L;

      try {
         hook.setMinWaitTime(36000);
         hook.setMaxWaitTime(36000);
         hook.setApplyLure(false);
      } catch (Throwable var21) {
      }

      if (voidFishing) {
         try {
            hook.setGravity(false);
            hook.setVelocity(new Vector(0, 0, 0));
         } catch (Throwable var20) {
         }
      }

      if (lava) {
         try {
            Block hb = hook.getLocation().getBlock();
            if (hb.getType() != Material.LAVA) {
               Block scan = hb;

               for (int i = 0; i < 8 && scan.getType() != Material.LAVA; i++) {
                  scan = scan.getRelative(0, 1, 0);
               }

               if (scan.getType() == Material.LAVA) {
                  hb = scan;
               }
            }

            if (hb.getType() == Material.LAVA) {
               Block top = hb;

               while (top.getRelative(0, 1, 0).getType() == Material.LAVA) {
                  top = top.getRelative(0, 1, 0);
               }

               Location loc = hook.getLocation();
               hook.teleport(new Location(loc.getWorld(), loc.getX(), (double)top.getY() + 0.9, loc.getZ(), loc.getYaw(), loc.getPitch()));
            }

            hook.setGravity(false);
            hook.setVelocity(new Vector(0, 0, 0));
            clearStuckState(hook);
         } catch (Throwable var22) {
         }
      }

      if (this.plugin.getConfig().getBoolean("settings.effects.casting-trail", true)) {
         ParticleEffects.castingTrail(p.getLocation().add(0.0, 1.2, 0.0));
      }

      BukkitTask task = (new BukkitRunnable() {
         int ticks = 0;

         public void run() {
            this.ticks++;
            FishingSession s = FishingManager.this.sessions.get(session.playerId);
            if (s == null) {
               this.cancel();
            } else {
               Player pl = Bukkit.getPlayer(s.playerId);
               if (pl == null || !pl.isOnline()) {
                  FishingManager.this.closeSession(s.playerId);
                  this.cancel();
               } else if (s.hook != null && !s.hook.isDead()) {
                  Location at = s.hook.getLocation().clone().add(0.0, 0.25, 0.0);
                  long now = System.currentTimeMillis();
                  if (s.voidFishing || s.lava) {
                     s.hook.setVelocity(new Vector(0, 0, 0));
                  }

                  if (s.phase == FishingSession.Phase.WAITING) {
                     if (this.ticks % 4 == 0) {
                        if (s.voidFishing) {
                           ParticleEffects.waitingVoid(at, s.rod.def().particles());
                        } else if (s.lava) {
                           ParticleEffects.waitingLava(at, s.rod.def().particles());
                        }
                     }

                     if (this.ticks % 2 == 0) {
                        FishingManager.this.sendWaitingBar(pl, s, this.ticks, now);
                     }

                     if (now >= s.biteAtMs) {
                        s.phase = FishingSession.Phase.BITING;
                        if (FishingManager.this.plugin.getConfig().getBoolean("settings.effects.bite-warning", true)) {
                           ParticleEffects.biteIncoming(at, s.lava, s.rod.def().particles());
                        }

                        pl.playSound(pl.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.6F, 1.6F);
                        FishingManager.this.sendBitingBar(pl, s, this.ticks, now);
                        if (FishingManager.this.plugin.rods().hasAbility(s.rod, AbilityType.AUTO_REEL)) {
                           FishingManager.this.resolveCatch(pl, s);
                           FishingManager.this.closeSession(s.playerId);
                           this.cancel();
                           return;
                        }
                     }
                  } else if (s.phase == FishingSession.Phase.BITING) {
                     if (this.ticks % 2 == 0) {
                        ParticleEffects.biteIncoming(at, s.lava, s.rod.def().particles());
                     }

                     FishingManager.this.sendBitingBar(pl, s, this.ticks, now);
                     if (now >= s.biteEndMs) {
                        FishingManager.this.sendMissBar(pl);
                        FishingManager.this.closeSession(s.playerId);
                        this.cancel();
                     }
                  }
               } else {
                  FishingManager.this.closeSession(s.playerId);
                  this.cancel();
               }
            }
         }
      }).runTaskTimer(this.plugin, 0L, 1L);
      session.taskId = task.getTaskId();
      this.sessions.put(p.getUniqueId(), session);
      if (this.plugin.antiAutofish() != null) {
         this.plugin.antiAutofish().onCastStart(p);
      }
   }

   public void enterBitePhase(Player p) {
      FishingSession s = this.sessions.get(p.getUniqueId());
      if (s != null) {
         if (s.phase == FishingSession.Phase.WAITING) {
            s.phase = FishingSession.Phase.BITING;
            s.biteAtMs = System.currentTimeMillis();
            s.biteEndMs = s.biteAtMs + 3000L;
            p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.6F, 1.6F);
         }
      }
   }

   public void forceCatch(Player p) {
      FishingSession s = this.sessions.get(p.getUniqueId());
      if (s != null) {
         if (s.phase != FishingSession.Phase.DONE) {
            s.phase = FishingSession.Phase.BITING;
            if (this.plugin.antiAutofish() == null || this.plugin.antiAutofish().allowCatch(p, s)) {
               this.resolveCatch(p, s);
            }
         }

         this.closeSession(p.getUniqueId());
      }
   }

   public boolean tryReel(Player p) {
      FishingSession s = this.sessions.get(p.getUniqueId());
      if (s == null) {
         return false;
      } else {
         long cdMs = this.plugin.getConfig().getLong("settings.catch-cooldown-ms", 750L);
         Long last = this.catchCooldowns.get(p.getUniqueId());
         if (last != null && System.currentTimeMillis() - last < cdMs) {
            p.sendActionBar(TextUtil.mm(this.plugin.configs().rawMessage("fishing.too-fast")));
            return false;
         } else {
            if (s.phase == FishingSession.Phase.BITING && (this.plugin.antiAutofish() == null || this.plugin.antiAutofish().allowCatch(p, s))) {
               this.resolveCatch(p, s);
            }

            this.closeSession(p.getUniqueId());
            return true;
         }
      }
   }

   public void closeSession(UUID id) {
      FishingSession s = this.sessions.remove(id);
      if (s != null) {
         if (s.taskId != -1) {
            Bukkit.getScheduler().cancelTask(s.taskId);
         }

         if (s.hook != null && !s.hook.isDead()) {
            s.hook.remove();
         }
      }
   }

   private void resolveCatch(Player p, FishingSession s) {
      this.catchCooldowns.put(p.getUniqueId(), System.currentTimeMillis());
      RewardTier tier = this.plugin.rewards().rollTier(s.rod, p, this.rng);
      Reward reward = this.plugin.rewards().rollReward(tier, this.rng, p);
      if (reward != null) {
         ItemStack visual = this.plugin.rewards().applyReward(p, s.rod, reward, tier);
         if (this.plugin.rods().hasAbility(s.rod, AbilityType.TRIPLE_CATCH) && this.rng.nextDouble() < 0.05) {
            Reward extra = this.plugin.rewards().rollReward(tier, this.rng, p);
            if (extra != null) {
               this.plugin.rewards().applyReward(p, s.rod, extra, tier);
            }

            extra = this.plugin.rewards().rollReward(tier, this.rng, p);
            if (extra != null) {
               this.plugin.rewards().applyReward(p, s.rod, extra, tier);
            }
         } else if (this.plugin.rods().hasAbility(s.rod, AbilityType.DOUBLE_CATCH) && this.rng.nextDouble() < 0.1) {
            Reward extrax = this.plugin.rewards().rollReward(tier, this.rng, p);
            if (extrax != null) {
               this.plugin.rewards().applyReward(p, s.rod, extrax, tier);
            }
         }

         String envJackpot = s.lava ? "lava" : (s.voidFishing ? "void" : null);
         this.rollJackpot(p, s, tier, this.rng, "global");
         if (envJackpot != null) {
            this.rollJackpot(p, s, tier, this.rng, envJackpot);
         }

         long xp = this.computeXp(s.rod, reward, tier);
         if (xp > 0L) {
            ItemStack inHand = p.getInventory().getItemInMainHand();
            if (this.plugin.rods().isNovaRod(inHand)) {
               this.plugin.rods().addXp(p, inHand, xp);
            } else {
               for (ItemStack it : p.getInventory().getContents()) {
                  if (this.plugin.rods().isNovaRod(it)) {
                     this.plugin.rods().addXp(p, it, xp);
                     break;
                  }
               }
            }

            UUID statsUid = p.getUniqueId();
            this.plugin.db().runAsync(() -> this.plugin.db().addStats(statsUid, xp, 1L));
         }

         if (tier == RewardTier.MYTHIC && this.plugin.rods().hasAbility(s.rod, AbilityType.LIGHTNING_ROD)) {
            s.hook.getWorld().strikeLightningEffect(s.hook.getLocation());
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0F, 1.0F);
         }

         if (this.plugin.rods().hasAbility(s.rod, AbilityType.EXPERIENCE_AMP)) {
            int orbXp = 5 + this.rng.nextInt(16);
            ExperienceOrb orb = (ExperienceOrb)p.getWorld().spawn(p.getLocation(), ExperienceOrb.class);
            orb.setExperience(orbXp);
         }

         UUID tierUid = p.getUniqueId();
         this.plugin.db().runAsync(() -> this.plugin.db().recordTierCatch(tierUid, tier));
         if (this.plugin.challenges() != null) {
            this.plugin.challenges().onCatch(p, tier);
         }

         if (this.plugin.tournament() != null) {
            this.plugin.tournament().onCatch(p, tier);
         }

         if (this.plugin.getConfig().getBoolean("settings.effects.catch-burst", true)) {
            ParticleEffects.catchBurst(p, s.hook.getLocation().add(0.0, 0.5, 0.0), this.plugin.rewards().tierLabel(tier), s.rod.def().particles());
         }

         this.sendCatchBar(p, tier);
         Map<String, String> ph = new HashMap<>();
         ph.put("xp", String.valueOf(xp));
         String itemName;
         if (visual != null && visual.getItemMeta() != null && visual.getItemMeta().hasDisplayName()) {
            itemName = (String)MiniMessage.miniMessage().serialize(visual.getItemMeta().displayName());
         } else {
            itemName = this.plugin.rewards().tierLabel(tier);
         }

         ph.put("item", itemName);
         p.sendMessage(TextUtil.mm(this.plugin.configs().message("fishing.caught"), ph));
         if (this.plugin.getConfig().getBoolean("settings.broadcast.rare-catch", true)) {
            String thresholdName = this.plugin.getConfig().getString("settings.broadcast.rare-catch-min-tier", "MYTHIC");
            RewardTier threshold = RewardTier.safe(thresholdName);
            if (threshold != null && tier.ordinal() >= threshold.ordinal()) {
               Component msg = TextUtil.mm(
                  "<gradient:#FFD700:#FF3300><bold>★ </bold></gradient><yellow>"
                     + p.getName()
                     + "</yellow><gray> just caught a </gray>"
                     + this.plugin.rewards().tierLabel(tier)
                     + "<gray>!</gray>"
               );
               Bukkit.broadcast(msg);
            }
         }
      }
   }

   private void rollJackpot(Player p, FishingSession s, RewardTier tier, Random rng, String poolName) {
      RewardManager.JackpotPool pool = this.plugin.rewards().getJackpot(poolName);
      if (pool != null && !(pool.chance <= 0.0) && !pool.entries.isEmpty()) {
         if (!(rng.nextDouble() >= pool.chance)) {
            Reward jackpot = this.plugin.rewards().rollJackpotEntry(pool, rng, p);
            if (jackpot != null) {
               this.plugin.rewards().applyReward(p, s.rod, jackpot, tier);
               Map<String, String> ph = new HashMap<>();
               ph.put("player", p.getName());
               ph.put("jackpot", pool.broadcastName);
               Bukkit.broadcast(TextUtil.mm(this.plugin.configs().message("fishing.jackpot"), ph));
               p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
            }
         }
      }
   }

   private long computeXp(RodInstance rod, Reward reward, RewardTier tier) {
      long base = switch (tier) {
         case JUNK -> this.plugin.getConfig().getBoolean("settings.xp-on-junk", true) ? 4L : 0L;
         case COMMON -> 20L;
         case UNCOMMON -> 50L;
         case RARE -> 120L;
         case EPIC -> 280L;
         case LEGENDARY -> 640L;
         case MYTHIC -> 1500L;
      };
      double mult = 1.0;
      if (this.plugin.rods().hasAbility(rod, AbilityType.XP_BOOST_I)) {
         mult = Math.max(mult, 1.25);
      }

      if (this.plugin.rods().hasAbility(rod, AbilityType.XP_BOOST_II)) {
         mult = Math.max(mult, 1.5);
      }

      if (this.plugin.rods().hasAbility(rod, AbilityType.XP_BOOST_III)) {
         mult = Math.max(mult, 2.0);
      }

      if (this.plugin.rods().hasAbility(rod, AbilityType.ETERNAL_FLAME)) {
         mult *= 2.0;
      }

      return Math.round((double)base * mult);
   }

   private void sendWaitingBar(Player pl, FishingSession s, int ticks, long now) {
      long waitTotal = Math.max(1L, s.biteAtMs - s.startMs);
      long waitElapsed = Math.max(0L, now - s.startMs);
      float pct = Math.min(1.0F, (float)waitElapsed / (float)waitTotal);
      long remainSec = Math.max(0L, s.biteAtMs - now) / 1000L;
      String pulse = PULSE[ticks / 3 % PULSE.length];
      String bar = bar(pct, 20, "<gradient:#FFD700:#FFAA00>", "<dark_gray>");
      String env = s.voidFishing ? "<dark_purple>Void Casting</dark_purple>" : (s.lava ? "<red>Lava Fishing</red>" : "<aqua>Casting</aqua>");
      String text = "<gold>" + pulse + "</gold> " + env + " <dark_gray>┃</dark_gray> " + bar + " <yellow>" + remainSec + "s";
      pl.sendActionBar(TextUtil.mm(text));
   }

   private void sendBitingBar(Player pl, FishingSession s, int ticks, long now) {
      long window = Math.max(1L, s.biteEndMs - s.biteAtMs);
      long elapsed = Math.max(0L, now - s.biteAtMs);
      float pct = 1.0F - Math.min(1.0F, (float)elapsed / (float)window);
      String flashColor = ticks % 8 < 4 ? "#FF3300" : "#FFEE00";
      String dim = ticks % 8 < 4 ? "#660000" : "#664400";
      String bar = bar(pct, 20, "<color:" + flashColor + ">", "<color:" + dim + ">");
      String text = "<color:" + flashColor + "><bold>❗ BITE! REEL NOW! ❗</bold></color> <dark_gray>┃</dark_gray> " + bar;
      pl.sendActionBar(TextUtil.mm(text));
   }

   public void sendCatchBar(Player pl, RewardTier tier) {
      String tierColor = this.plugin.rewards().tierLabel(tier);
      pl.sendActionBar(TextUtil.mm("<gradient:#FFD700:#FF7F00><bold>✦ CAUGHT ✦</bold></gradient> <dark_gray>┃</dark_gray> " + tierColor));
   }

   private void sendMissBar(Player pl) {
      pl.sendActionBar(TextUtil.mm("<red>… it got away."));
   }

   private static String bar(float pct, int length, String fillPrefix, String emptyPrefix) {
      int filled = Math.max(0, Math.min(length, Math.round(pct * (float)length)));
      StringBuilder sb = new StringBuilder(length * 2 + 8);
      sb.append(fillPrefix);

      for (int i = 0; i < filled; i++) {
         sb.append("▰");
      }

      sb.append(emptyPrefix);

      for (int i = filled; i < length; i++) {
         sb.append("▱");
      }

      return sb.toString();
   }

   public static boolean inLava(FishHook hook) {
      Block b = hook.getLocation().getBlock();
      return b.getType() == Material.LAVA;
   }

   public boolean inVoid(FishHook hook) {
      Block b = hook.getLocation().getBlock();
      return b.isEmpty() && this.plugin.voids().isInRegion(hook.getLocation());
   }

   public boolean isBlockOccupiedByOtherHook(Block placed, UUID placerId) {
      for (FishingSession s : this.sessions.values()) {
         if (!s.playerId.equals(placerId)) {
            FishHook h = s.hook;
            if (h != null && !h.isDead() && h.getWorld().equals(placed.getWorld()) && h.getLocation().getBlock().equals(placed)) {
               return true;
            }
         }
      }

      return false;
   }
}
