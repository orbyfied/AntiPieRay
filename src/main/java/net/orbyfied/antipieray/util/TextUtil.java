package net.orbyfied.antipieray.util;

import net.orbyfied.j8.util.StringReader;

import java.awt.*;

/**
 * Utilities for working with in-game text.
 */
public class TextUtil {

    /**
     * Parses a chat color from a string value.
     *
     * @param val The string value.
     * @return The color or null if the string was null.
     */
    public static net.md_5.bungee.api.ChatColor parseChatColor(String val) {
        if (val == null) {
            // by default return null
            return null;
        } else {
            try {
                // get named chat color
                return net.md_5.bungee.api.ChatColor.valueOf(val);
            } catch (Exception e) {
                // get hex chat color
                return net.md_5.bungee.api.ChatColor.of(val);
            }
        }
    }

    /**
     * Translate the given legacy description
     * text to proper legacy text with full
     * formatting.
     *
     * @param str The source string.
     * @return The legacy string.
     */
    public static String translate(String str) {
        StringBuilder b = new StringBuilder();
        StringReader reader = new StringReader(str);
        char c;
        while ((c = reader.current()) != StringReader.DONE) {
            // check for color code
            if (c == '&') {
                // output color
                net.md_5.bungee.api.ChatColor color;

                // check for hex code
                if (reader.next() == '#') {
                    // collect hex code
                    StringBuilder hexBuilder = new StringBuilder(6);
                    for (int i = 0; i < 6; i++)
                        hexBuilder.append(reader.next());
                    reader.next();

                    // parse hex code
                    color = net.md_5.bungee.api.ChatColor.of(
                            new Color(Integer.parseInt(hexBuilder.toString(), 6)));
                } else {
                    // get formatting by character
                    char f = reader.current();
                    color = net.md_5.bungee.api.ChatColor.getByChar(f);

                    // advance to next character
                    reader.next();
                }

                // append color
                b.append(color);
            } else {
                // append character
                b.append(c);

                // advance next
                reader.next();
            }
        }

        // return output
        return b.toString();
    }

}
