package net.plebmc.mc.util;

/** 1.8's ChatComponentText: legacy § codes are kept as they are (1.21.1 still draws them). */
public class ChatComponentText implements IChatComponent {
	private final net.minecraft.network.chat.MutableComponent c;

	public ChatComponentText(String s) {
		c = net.minecraft.network.chat.Component.literal(s == null ? "" : s);
	}

	@Override
	public net.minecraft.network.chat.Component toComponent() {
		return c;
	}

	@Override
	public IChatComponent appendSibling(IChatComponent o) {
		c.append(o.toComponent());
		return this;
	}
}
