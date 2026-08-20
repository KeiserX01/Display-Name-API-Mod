package com.keiserx01.displaynameapi.parser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.NotNull;

/**
 * Parser for legacy formatting codes using '$' instead of '§'.
 * <p>
 * Supports the following codes:
 * </p>
 * <ul>
 *   <li>$0-$9: Black, Dark Blue, Dark Green, Dark Aqua, Dark Red, Dark Purple, Gold, Gray, Dark Gray, Blue</li>
 *   <li>$a-$f: Green, Aqua, Red, Light Purple, Yellow, White</li>
 *   <li>$k: Obfuscated</li>
 *   <li>$l: Bold</li>
 *   <li>$m: Strikethrough</li>
 *   <li>$n: Underline</li>
 *   <li>$o: Italic</li>
 *   <li>$r: Reset</li>
 *   <li>$$: Escaped literal '$'</li>
 * </ul>
 * <p>
 * Automatically appends a reset ($r) at the end of each parsed component
 * to ensure visual isolation between adjacent prefixes/suffixes.
 * </p>
 */
public final class LegacyParser {
    
    private static final char ESCAPE_CHAR = '$';
    private static final char ESCAPE_SEQUENCE = '$';
    
    /**
     * Private constructor to prevent instantiation.
     */
    private LegacyParser() {}
    
    /**
     * Parses a legacy formatting string into a Component.
     * 
     * @param input The input string with $ formatting codes
     * @return The parsed Component with automatic reset appended
     * @throws IllegalArgumentException if input is null
     */
    @NotNull
    public static Component parse(@NotNull String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        
        if (input.isEmpty()) {
            return Component.empty();
        }
        
        MutableComponent result = Component.empty();
        Style currentStyle = Style.EMPTY;
        StringBuilder plainText = new StringBuilder();
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            
            if (c == ESCAPE_CHAR) {
                // Check for escape sequence $$
                if (i + 1 < input.length() && input.charAt(i + 1) == ESCAPE_SEQUENCE) {
                    plainText.append(ESCAPE_CHAR);
                    i++; // Skip the second $
                    continue;
                }
                
                // Check if there's a formatting code after $
                if (i + 1 < input.length()) {
                    char code = input.charAt(i + 1);
                    ChatFormatting formatting = getFormattingByCode(code);
                    
                    if (formatting != null) {
                        // Flush any accumulated plain text
                        if (!plainText.isEmpty()) {
                            result.append(Component.literal(plainText.toString()).withStyle(currentStyle));
                            plainText.setLength(0);
                        }
                        
                        // Apply formatting
                        currentStyle = applyFormatting(currentStyle, formatting);
                        i++; // Skip the code character
                        continue;
                    }
                }
            }
            
            // Regular character
            plainText.append(c);
        }
        
        // Flush remaining plain text
        if (!plainText.isEmpty()) {
            result.append(Component.literal(plainText.toString()).withStyle(currentStyle));
        }
        
        // Append automatic reset for visual isolation
        result.append(Component.empty().withStyle(Style.EMPTY));
        
        return result;
    }
    
    /**
     * Gets a ChatFormatting by its legacy code character.
     * 
     * @param code The legacy formatting code character
     * @return The ChatFormatting, or null if not found
     */
    private static ChatFormatting getFormattingByCode(char code) {
        return switch (code) {
            case '0' -> ChatFormatting.BLACK;
            case '1' -> ChatFormatting.DARK_BLUE;
            case '2' -> ChatFormatting.DARK_GREEN;
            case '3' -> ChatFormatting.DARK_AQUA;
            case '4' -> ChatFormatting.DARK_RED;
            case '5' -> ChatFormatting.DARK_PURPLE;
            case '6' -> ChatFormatting.GOLD;
            case '7' -> ChatFormatting.GRAY;
            case '8' -> ChatFormatting.DARK_GRAY;
            case '9' -> ChatFormatting.BLUE;
            case 'a' -> ChatFormatting.GREEN;
            case 'b' -> ChatFormatting.AQUA;
            case 'c' -> ChatFormatting.RED;
            case 'd' -> ChatFormatting.LIGHT_PURPLE;
            case 'e' -> ChatFormatting.YELLOW;
            case 'f' -> ChatFormatting.WHITE;
            case 'k' -> ChatFormatting.OBFUSCATED;
            case 'l' -> ChatFormatting.BOLD;
            case 'm' -> ChatFormatting.STRIKETHROUGH;
            case 'n' -> ChatFormatting.UNDERLINE;
            case 'o' -> ChatFormatting.ITALIC;
            case 'r' -> ChatFormatting.RESET;
            default -> null;
        };
    }
    
    /**
     * Applies a formatting code to the current style.
     * 
     * @param style       The current style
     * @param formatting  The formatting to apply
     * @return The new style with formatting applied
     */
    private static Style applyFormatting(Style style, ChatFormatting formatting) {
        return switch (formatting) {
            case BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE,
                 GOLD, GRAY, DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE ->
                style.withColor(formatting);
            case OBFUSCATED -> style.withObfuscated(true);
            case BOLD -> style.withBold(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case UNDERLINE -> style.withUnderlined(true);
            case ITALIC -> style.withItalic(true);
            case RESET -> Style.EMPTY;
            default -> style;
        };
    }
}