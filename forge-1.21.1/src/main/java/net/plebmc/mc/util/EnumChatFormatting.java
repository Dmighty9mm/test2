package net.plebmc.mc.util;

/** 1.8's EnumChatFormatting. */
public enum EnumChatFormatting {
	BLACK('0'), DARK_BLUE('1'), DARK_GREEN('2'), DARK_AQUA('3'), DARK_RED('4'), DARK_PURPLE('5'), GOLD('6'), GRAY('7'),
	DARK_GRAY('8'), BLUE('9'), GREEN('a'), AQUA('b'), RED('c'), LIGHT_PURPLE('d'), YELLOW('e'), WHITE('f'), OBFUSCATED('k'),
	BOLD('l'), STRIKETHROUGH('m'), UNDERLINE('n'), ITALIC('o'), RESET('r');

	private final char code;

	EnumChatFormatting(char c) {
		code = c;
	}

	@Override
	public String toString() {
		return "§" + code;
	}

	public char getFormattingCode() {
		return code;
	}

	public static String getTextWithoutFormattingCodes(String s) {
		return s == null ? null : net.minecraft.ChatFormatting.stripFormatting(s);
	}

	public static EnumChatFormatting getValueByName(String n) {
		try {
			return valueOf(n.toUpperCase(java.util.Locale.ROOT));
		} catch (Throwable t) {
			return null;
		}
	}
}
