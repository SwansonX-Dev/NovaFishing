package dev.nova.fishing.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class TextUtil {
   private static final MiniMessage MM = MiniMessage.miniMessage();

   private TextUtil() {
   }

   public static Component mm(String input) {
      if (input != null && !input.isEmpty()) {
         String safe = normalizeLegacyCodes(input);

         try {
            return MM.deserialize(safe).decoration(TextDecoration.ITALIC, false);
         } catch (Throwable var5) {
            try {
               String stripped = safe.replaceAll("[§&].", "");
               return MM.deserialize(stripped).decoration(TextDecoration.ITALIC, false);
            } catch (Throwable var4) {
               return Component.text(safe);
            }
         }
      } else {
         return Component.empty();
      }
   }

   private static String normalizeLegacyCodes(String input) {
      if (input == null) {
         return "";
      } else if (input.indexOf(38) < 0 && input.indexOf(167) < 0) {
         return input;
      } else {
         StringBuilder out = new StringBuilder(input.length() + 16);

         for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if ((c == '&' || c == 167) && i + 1 < input.length()) {
               char code = Character.toLowerCase(input.charAt(i + 1));

               String mm = switch (code) {
                  case '0' -> "<black>";
                  case '1' -> "<dark_blue>";
                  case '2' -> "<dark_green>";
                  case '3' -> "<dark_aqua>";
                  case '4' -> "<dark_red>";
                  case '5' -> "<dark_purple>";
                  case '6' -> "<gold>";
                  case '7' -> "<gray>";
                  case '8' -> "<dark_gray>";
                  case '9' -> "<blue>";
                  default -> null;
                  case 'a' -> "<green>";
                  case 'b' -> "<aqua>";
                  case 'c' -> "<red>";
                  case 'd' -> "<light_purple>";
                  case 'e' -> "<yellow>";
                  case 'f' -> "<white>";
                  case 'k' -> "<obfuscated>";
                  case 'l' -> "<bold>";
                  case 'm' -> "<strikethrough>";
                  case 'n' -> "<underlined>";
                  case 'o' -> "<italic>";
                  case 'r' -> "<reset>";
               };
               if (mm != null) {
                  out.append(mm);
                  i++;
                  continue;
               }
            }

            out.append(c);
         }

         return out.toString();
      }
   }

   public static Component mm(String input, Map<String, String> placeholders) {
      return (Component)(input == null ? Component.empty() : mm(replace(input, placeholders)));
   }

   public static List<Component> mmList(List<String> lines) {
      List<Component> out = new ArrayList<>(lines.size());

      for (String s : lines) {
         out.add(mm(s));
      }

      return out;
   }

   public static List<Component> mmList(List<String> lines, Map<String, String> placeholders) {
      List<Component> out = new ArrayList<>(lines.size());

      for (String s : lines) {
         out.add(mm(replace(s, placeholders)));
      }

      return out;
   }

   public static String replace(String input, Map<String, String> placeholders) {
      if (input == null) {
         return "";
      } else {
         String r = input;
         if (placeholders != null) {
            for (Entry<String, String> e : placeholders.entrySet()) {
               r = r.replace("<" + e.getKey() + ">", e.getValue());
            }
         }

         return r;
      }
   }

   public static String stripTags(String input) {
      return PlainTextComponentSerializer.plainText().serialize(mm(input));
   }

   public static String legacy(String input) {
      return LegacyComponentSerializer.legacyAmpersand().serialize(mm(input));
   }
}
