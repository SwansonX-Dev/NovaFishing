package dev.nova.fishing.event;

import dev.nova.fishing.NovaFishing;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public final class EventManager {
   private final NovaFishing plugin;

   public EventManager(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public boolean isActive(String event) {
      return event != null && !event.isBlank() ? this.plugin.getConfig().getBoolean("events." + event.toLowerCase(), false) : true;
   }

   public List<String> activeEvents() {
      List<String> out = new ArrayList<>();
      ConfigurationSection sec = this.plugin.getConfig().getConfigurationSection("events");
      if (sec == null) {
         return out;
      } else {
         for (String key : sec.getKeys(false)) {
            if (sec.getBoolean(key, false)) {
               out.add(key);
            }
         }

         return out;
      }
   }
}
