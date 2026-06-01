package dev.nova.fishing.integration;

import dev.nova.fishing.NovaFishing;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultHook {
   private final Economy economy;

   private VaultHook(Economy economy) {
      this.economy = economy;
   }

   public static VaultHook attempt(NovaFishing plugin) {
      try {
         if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
         } else {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
               return null;
            } else {
               plugin.getLogger().info("Vault economy hooked: " + ((Economy)rsp.getProvider()).getName());
               return new VaultHook((Economy)rsp.getProvider());
            }
         }
      } catch (Throwable var2) {
         plugin.getLogger().warning("Vault hook failed: " + var2.getMessage());
         return null;
      }
   }

   public boolean deposit(OfflinePlayer p, double amount) {
      return this.economy == null ? false : this.economy.depositPlayer(p, amount).transactionSuccess();
   }

   public double balance(OfflinePlayer p) {
      return this.economy == null ? 0.0 : this.economy.getBalance(p);
   }
}
