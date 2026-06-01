package dev.nova.fishing.discord;

import dev.nova.fishing.NovaFishing;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.bukkit.Bukkit;

public final class DiscordWebhook {
   private final NovaFishing plugin;
   private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).build();

   public DiscordWebhook(NovaFishing plugin) {
      this.plugin = plugin;
   }

   public boolean enabled() {
      String url = this.plugin.getConfig().getString("settings.discord-webhook-url", "");
      return url != null && !url.isBlank();
   }

   public void post(String content) {
      if (this.enabled()) {
         String url = this.plugin.getConfig().getString("settings.discord-webhook-url");
         String json = "{\"content\":\"" + jsonEscape(content) + "\"}";
         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(5L))
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json, StandardCharsets.UTF_8))
            .build();
         Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try {
               HttpResponse<String> r = this.http.send(req, BodyHandlers.ofString());
               if (r.statusCode() / 100 != 2) {
                  this.plugin.getLogger().warning("Discord webhook " + r.statusCode() + ": " + r.body());
               }
            } catch (Exception var3x) {
               this.plugin.getLogger().warning("Discord webhook failed: " + var3x.getMessage());
            }
         });
      }
   }

   public void postMaxLevel(String playerName, String rodId, String rodDisplay) {
      this.post("**" + playerName + "** just maxed out the **" + rodDisplay + "** (`" + rodId + "`)!");
   }

   public void postTournamentEnd(String winnerName, long score) {
      this.post("Fishing Tournament finished — winner: **" + winnerName + "** with `" + score + "` points!");
   }

   private static String jsonEscape(String in) {
      StringBuilder sb = new StringBuilder(in.length() + 8);

      for (int i = 0; i < in.length(); i++) {
         char c = in.charAt(i);
         switch (c) {
            case '\t':
               sb.append("\\t");
               break;
            case '\n':
               sb.append("\\n");
               break;
            case '\r':
               sb.append("\\r");
               break;
            case '"':
               sb.append("\\\"");
               break;
            case '\\':
               sb.append("\\\\");
               break;
            default:
               sb.append(c);
         }
      }

      return sb.toString();
   }
}
