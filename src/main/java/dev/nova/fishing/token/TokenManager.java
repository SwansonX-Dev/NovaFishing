package dev.nova.fishing.token;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.util.TextUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class TokenManager {
   private final NovaFishing plugin;
   private final Map<UUID, Long> cache = new HashMap<>();

   public TokenManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public synchronized long get(UUID uuid) {
      Long cached = this.cache.get(uuid);
      if (cached != null) {
         return cached;
      } else {
         long v = this.plugin.db().getTokens(uuid);
         this.cache.put(uuid, v);
         return v;
      }
   }

   public synchronized void set(UUID uuid, long amount) {
      long clamped = Math.max(0L, amount);
      this.cache.put(uuid, clamped);
      this.plugin.db().runAsync(() -> this.plugin.db().setTokens(uuid, clamped));
   }

   public synchronized void give(UUID uuid, long amount, boolean notify) {
      long now = Math.max(0L, this.get(uuid) + amount);
      this.set(uuid, now);
      if (notify) {
         Player p = Bukkit.getPlayer(uuid);
         if (p != null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("amount", String.valueOf(amount));
            p.sendMessage(TextUtil.mm(this.plugin.configs().message("token.receive"), ph));
         }
      }
   }

   public synchronized void giveFromFishing(UUID uuid, long amount, boolean notify) {
      if (amount <= 0L) {
         return;
      }
      this.give(uuid, amount, notify);
      long ts = System.currentTimeMillis();
      this.plugin.db().runAsync(() -> {
         this.plugin.db().addFishingTokensEarned(uuid, amount);
         this.plugin.db().logTokenEarning(uuid, amount, ts);
      });
   }

   public synchronized boolean take(UUID uuid, long amount) {
      long current = this.get(uuid);
      if (current < amount) {
         return false;
      } else {
         this.set(uuid, current - amount);
         return true;
      }
   }
}
