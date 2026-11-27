package net.plebmc.mc.client.gui;

import net.plebmc.mc.util.IChatComponent;

/** 1.8's GuiNewChat over 1.21.1's ChatComponent. */
public class GuiNewChat {
	private static final java.util.Map<Integer, net.minecraft.client.GuiMessageTag> TAGS = new java.util.HashMap<>();

	private static net.minecraft.client.gui.components.ChatComponent chat() {
		return net.minecraft.client.Minecraft.getInstance().gui.getChat();
	}

	public void printChatMessage(IChatComponent c) {
		chat().addMessage(c.toComponent());
	}

	/** Messages with the same id replace each other (1.8 behavior), done by deleting the earlier copy. */
	public void printChatMessageWithOptionalDeletion(IChatComponent c, int id) {
		add(c.toComponent(), id);
	}

	/** Adds a line; a non-zero id replaces the earlier line with the same id. */
	public static void add(net.minecraft.network.chat.Component c, int id) {
		if (id != 0) {
			delete(id);
		}
		net.minecraft.client.GuiMessageTag tag = new net.minecraft.client.GuiMessageTag(0x00FFFF, null, null, "plebmc:" + id);
		chat().addMessage(c, null, tag);
		if (id != 0) {
			TAGS.put(id, tag);
		}
	}

	public void deleteChatLine(int id) {
		delete(id);
	}

	static void delete(int id) {
		net.minecraft.client.GuiMessageTag tag = TAGS.remove(id);
		if (tag == null) {
			return;
		}
		try {
			java.lang.reflect.Field f = null;
			for (java.lang.reflect.Field x : net.minecraft.client.gui.components.ChatComponent.class.getDeclaredFields()) {
				if (x.getType() == java.util.List.class) {
					x.setAccessible(true);
					@SuppressWarnings("unchecked")
					java.util.List<Object> l = (java.util.List<Object>) x.get(chat());
					l.removeIf(o -> o instanceof net.minecraft.client.GuiMessage && ((net.minecraft.client.GuiMessage) o).tag() == tag);
					f = x;
				}
			}
			if (f != null) {
				chat().rescaleChat();
			}
		} catch (Throwable ignored) {
		}
	}

	public int getChatWidth() {
		return chat().getWidth();
	}
}
