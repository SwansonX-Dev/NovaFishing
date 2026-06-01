package dev.nova.fishing.gui;

import dev.nova.fishing.NovaFishing;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GUIManager implements Listener {
   private final NovaFishing plugin;

   public GUIManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public void openTokenShop(Player p) {
      TokenShopGUI.open(this.plugin, p);
   }

   public void openShopCategory(Player p, String categoryId) {
      TokenShopGUI.openCategory(this.plugin, p, categoryId);
   }

   public void openStaffRewards(Player p) {
      StaffRewardGUI.open(this.plugin, p);
   }

   public void openShopEditor(Player p) {
      ShopEditorGUI.open(this.plugin, p);
   }

   public void openShopCategoryEditor(Player p, String categoryId) {
      ShopEditorGUI.openCategory(this.plugin, p, categoryId);
   }

   @EventHandler
   public void onClick(InventoryClickEvent e) {
      Inventory top = e.getView().getTopInventory();
      if (top.getHolder() instanceof GUIManager.NovaHolder nh) {
         if (e.getWhoClicked() instanceof Player p) {
            Inventory clicked = e.getClickedInventory();
            if (clicked == null) {
               e.setCancelled(true);
            } else if (clicked == top) {
               e.setCancelled(true);
               nh.click(p, e);
            } else if (e.isShiftClick()) {
               e.setCancelled(true);
            } else {
               InventoryAction action = e.getAction();
               if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                  || action == InventoryAction.COLLECT_TO_CURSOR
                  || action == InventoryAction.HOTBAR_MOVE_AND_READD
                  || action == InventoryAction.HOTBAR_SWAP) {
                  e.setCancelled(true);
               }
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent e) {
      Inventory top = e.getView().getTopInventory();
      InventoryHolder h = top.getHolder();
      if (h instanceof GUIManager.NovaHolder) {
         for (int slot : e.getRawSlots()) {
            if (slot < top.getSize()) {
               e.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent e) {
      Inventory top = e.getView().getTopInventory();
      if (top.getHolder() instanceof GUIManager.NovaHolder nh && e.getPlayer() instanceof Player p) {
         nh.close(p);
      }
   }

   public interface NovaHolder extends InventoryHolder {
      UUID viewer();

      void click(Player var1, InventoryClickEvent var2);

      default void close(Player p) {
      }
   }
}
