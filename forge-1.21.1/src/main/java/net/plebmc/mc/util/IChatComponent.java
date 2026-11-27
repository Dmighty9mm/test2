package net.plebmc.mc.util;

/** 1.8's IChatComponent over a 1.21.1 Component. */
public interface IChatComponent {
	net.minecraft.network.chat.Component toComponent();

	default String getUnformattedText() {
		return toComponent().getString();
	}

	default String getFormattedText() {
		return Legacy.of(toComponent());
	}

	default IChatComponent createCopy() {
		return wrap(toComponent().copy());
	}

	default IChatComponent appendSibling(IChatComponent c) {
		net.minecraft.network.chat.MutableComponent m = toComponent() instanceof net.minecraft.network.chat.MutableComponent
				? (net.minecraft.network.chat.MutableComponent) toComponent()
				: toComponent().copy();
		m.append(c.toComponent());
		return wrap(m);
	}

	default IChatComponent appendText(String s) {
		return appendSibling(new ChatComponentText(s));
	}

	static IChatComponent wrap(net.minecraft.network.chat.Component c) {
		return () -> c;
	}

	/** Turns a component back into legacy § text (1.8's getFormattedText). */
	final class Legacy {
		private Legacy() {
		}

		public static String of(net.minecraft.network.chat.Component c) {
			StringBuilder b = new StringBuilder();
			c.visit((style, text) -> {
				if (!text.isEmpty()) {
					b.append("§r");
					net.minecraft.network.chat.TextColor col = style.getColor();
					if (col != null) {
						net.minecraft.ChatFormatting f = null;
						for (net.minecraft.ChatFormatting x : net.minecraft.ChatFormatting.values()) {
							if (x.isColor() && x.getColor() != null && x.getColor() == col.getValue()) {
								f = x;
								break;
							}
						}
						if (f != null) {
							b.append(f.toString());
						} else {
							b.append(nearest(col.getValue()));
						}
					}
					if (style.isBold()) {
						b.append("§l");
					}
					if (style.isItalic()) {
						b.append("§o");
					}
					if (style.isUnderlined()) {
						b.append("§n");
					}
					if (style.isStrikethrough()) {
						b.append("§m");
					}
					if (style.isObfuscated()) {
						b.append("§k");
					}
					b.append(text);
				}
				return java.util.Optional.empty();
			}, net.minecraft.network.chat.Style.EMPTY);
			return b.toString();
		}

		private static String nearest(int rgb) {
			net.minecraft.ChatFormatting best = net.minecraft.ChatFormatting.WHITE;
			long bd = Long.MAX_VALUE;
			for (net.minecraft.ChatFormatting x : net.minecraft.ChatFormatting.values()) {
				if (!x.isColor() || x.getColor() == null) {
					continue;
				}
				int c = x.getColor();
				long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255), dg = ((c >> 8) & 255) - ((rgb >> 8) & 255), db = (c & 255) - (rgb & 255);
				long d = dr * dr + dg * dg + db * db;
				if (d < bd) {
					bd = d;
					best = x;
				}
			}
			return best.toString();
		}
	}
}
