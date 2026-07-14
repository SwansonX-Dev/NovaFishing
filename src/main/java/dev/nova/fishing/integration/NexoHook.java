package dev.nova.fishing.integration;

import dev.nova.fishing.NovaFishing;
import java.lang.reflect.Method;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Soft, reflection-based hook into Nexo's custom-item API. Lets a reward store a Nexo
 * item id and rebuild the real custom item on delivery, instead of collapsing down to
 * the base vanilla material. There is no compile-time dependency on Nexo — NovaFishing
 * runs fine when Nexo is absent, mirroring the {@link NovaBlockHook} pattern.
 */
public final class NexoHook {
   private final Method idFromItem;
   private final Method itemFromId;
   private final Method exists;
   private final Method build;

   private NexoHook(Method idFromItem, Method itemFromId, Method exists, Method build) {
      this.idFromItem = idFromItem;
      this.itemFromId = itemFromId;
      this.exists = exists;
      this.build = build;
   }

   public static NexoHook attempt(NovaFishing plugin) {
      try {
         Plugin nexo = plugin.getServer().getPluginManager().getPlugin("Nexo");
         if (nexo == null) {
            return null;
         } else {
            Class<?> api = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method idFromItem = api.getMethod("idFromItem", ItemStack.class);
            Method itemFromId = api.getMethod("itemFromId", String.class);
            Method exists = api.getMethod("exists", String.class);
            Method build = itemFromId.getReturnType().getMethod("build");
            plugin.getLogger().info("Nexo hooked: custom items can be used as rewards.");
            return new NexoHook(idFromItem, itemFromId, exists, build);
         }
      } catch (Throwable t) {
         plugin.getLogger().warning("Nexo hook failed: " + t.getMessage());
         return null;
      }
   }

   /** The Nexo item id backing this stack, or null if it isn't a Nexo item. */
   public String idOf(ItemStack stack) {
      if (stack == null) {
         return null;
      } else {
         try {
            return (String)this.idFromItem.invoke(null, stack);
         } catch (Throwable t) {
            return null;
         }
      }
   }

   /** Whether Nexo still knows this id (config could have removed it). */
   public boolean exists(String id) {
      if (id == null) {
         return false;
      } else {
         try {
            return Boolean.TRUE.equals(this.exists.invoke(null, id));
         } catch (Throwable t) {
            return false;
         }
      }
   }

   /** Build a fresh copy of the Nexo item at the given amount, or null if the id is unknown. */
   public ItemStack build(String id, int amount) {
      if (id == null) {
         return null;
      } else {
         try {
            Object builder = this.itemFromId.invoke(null, id);
            if (builder == null) {
               return null;
            } else {
               ItemStack stack = (ItemStack)this.build.invoke(builder);
               if (stack != null && amount > 0) {
                  stack.setAmount(amount);
               }

               return stack;
            }
         } catch (Throwable t) {
            return null;
         }
      }
   }
}
