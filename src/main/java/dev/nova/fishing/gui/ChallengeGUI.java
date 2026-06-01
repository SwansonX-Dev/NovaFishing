package dev.nova.fishing.gui;

import dev.nova.fishing.NovaFishing;
import dev.nova.fishing.challenge.Challenge;
import dev.nova.fishing.database.DatabaseManager;
import dev.nova.fishing.util.ItemBuilder;
import dev.nova.fishing.util.TextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class ChallengeGUI {
   private ChallengeGUI() {
   }

   public static void open(NovaFishing plugin, Player p) {
      ChallengeGUI.Holder h = new ChallengeGUI.Holder(p.getUniqueId());
      Inventory inv = Bukkit.createInventory(h, 36, TextUtil.mm("<dark_gray>Daily & Weekly Challenges"));
      h.inv = inv;
      Map<String, Challenge> all = plugin.challenges().getAll();
      int dailySlot = 10;
      int weeklySlot = 19;

      for (Challenge c : all.values()) {
         DatabaseManager.ChallengeProgress prog = plugin.challenges().progressOf(p.getUniqueId(), c);
         Material icon = c.period() == Challenge.Period.DAILY ? Material.CLOCK : Material.NETHER_STAR;
         if (prog.completed()) {
            icon = Material.LIME_DYE;
         }

         ItemBuilder b = new ItemBuilder(icon).name(c.displayName());
         List<String> lore = new ArrayList<>();
         lore.add(c.description());
         lore.add("");
         lore.add("<gray>Progress: <yellow>" + prog.progress() + "<gray>/<yellow>" + c.target());
         lore.add("<gray>Reward: " + describeReward(c));
         lore.add("");
         lore.add(prog.completed() ? "<green><bold>COMPLETED" : "<gray>In progress…");
         b.lore(lore);
         int slot = c.period() == Challenge.Period.DAILY ? dailySlot++ : weeklySlot++;
         if (slot < inv.getSize()) {
            inv.setItem(slot, b.build());
         }
      }

      p.openInventory(inv);
   }

   private static String describeReward(Challenge c) {
      return switch (c.reward().type()) {
         case TOKEN -> "<yellow>" + c.reward().amount() + " <gold>Nova Tokens";
         case COMMAND -> "<light_purple>Special";
      };
   }

   static final class Holder implements GUIManager.NovaHolder {
      private final UUID viewer;
      Inventory inv;

      Holder(UUID v) {
         this.viewer = v;
      }

      public Inventory getInventory() {
         return this.inv;
      }

      @Override
      public UUID viewer() {
         return this.viewer;
      }

      @Override
      public void click(Player p, InventoryClickEvent e) {
      }
   }
}
