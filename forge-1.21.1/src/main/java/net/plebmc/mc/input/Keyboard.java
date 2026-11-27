package net.plebmc.mc.input;

import org.lwjgl.glfw.GLFW;

/** LWJGL 2's Keyboard key codes (what PlebClient's code uses), mapped from GLFW on 1.21.1. */
public final class Keyboard {
	private Keyboard() {
	}

	public static final int KEY_NONE = 0, KEY_ESCAPE = 1, KEY_1 = 2, KEY_2 = 3, KEY_3 = 4, KEY_4 = 5, KEY_5 = 6, KEY_6 = 7,
			KEY_7 = 8, KEY_8 = 9, KEY_9 = 10, KEY_0 = 11, KEY_MINUS = 12, KEY_EQUALS = 13, KEY_BACK = 14, KEY_TAB = 15,
			KEY_Q = 16, KEY_W = 17, KEY_E = 18, KEY_R = 19, KEY_T = 20, KEY_Y = 21, KEY_U = 22, KEY_I = 23, KEY_O = 24,
			KEY_P = 25, KEY_LBRACKET = 26, KEY_RBRACKET = 27, KEY_RETURN = 28, KEY_LCONTROL = 29, KEY_A = 30, KEY_S = 31,
			KEY_D = 32, KEY_F = 33, KEY_G = 34, KEY_H = 35, KEY_J = 36, KEY_K = 37, KEY_L = 38, KEY_SEMICOLON = 39,
			KEY_APOSTROPHE = 40, KEY_GRAVE = 41, KEY_LSHIFT = 42, KEY_BACKSLASH = 43, KEY_Z = 44, KEY_X = 45, KEY_C = 46,
			KEY_V = 47, KEY_B = 48, KEY_N = 49, KEY_M = 50, KEY_COMMA = 51, KEY_PERIOD = 52, KEY_SLASH = 53,
			KEY_RSHIFT = 54, KEY_MULTIPLY = 55, KEY_LMENU = 56, KEY_SPACE = 57, KEY_CAPITAL = 58, KEY_F1 = 59, KEY_F2 = 60,
			KEY_F3 = 61, KEY_F4 = 62, KEY_F5 = 63, KEY_F6 = 64, KEY_F7 = 65, KEY_F8 = 66, KEY_F9 = 67, KEY_F10 = 68,
			KEY_F11 = 87, KEY_F12 = 88, KEY_NUMPADENTER = 156, KEY_RCONTROL = 157, KEY_RMENU = 184, KEY_HOME = 199,
			KEY_UP = 200, KEY_PRIOR = 201, KEY_LEFT = 203, KEY_RIGHT = 205, KEY_END = 207, KEY_DOWN = 208, KEY_NEXT = 209,
			KEY_INSERT = 210, KEY_DELETE = 211, KEY_LMETA = 219, KEY_RMETA = 220;

	private static final int[] FROM_GLFW = new int[GLFW.GLFW_KEY_LAST + 1];
	private static final int[] TO_GLFW = new int[256];

	private static void map(int glfw, int lw) {
		FROM_GLFW[glfw] = lw;
		TO_GLFW[lw] = glfw;
	}

	static {
		String row = "1234567890";
		for (int i = 0; i < 10; i++) {
			map(GLFW.GLFW_KEY_0 + ((i + 1) % 10), KEY_1 + i);
		}
		String letters = "QWERTYUIOPASDFGHJKLZXCVBNM";
		int[] codes = { KEY_Q, KEY_W, KEY_E, KEY_R, KEY_T, KEY_Y, KEY_U, KEY_I, KEY_O, KEY_P, KEY_A, KEY_S, KEY_D, KEY_F, KEY_G,
				KEY_H, KEY_J, KEY_K, KEY_L, KEY_Z, KEY_X, KEY_C, KEY_V, KEY_B, KEY_N, KEY_M };
		for (int i = 0; i < letters.length(); i++) {
			map(GLFW.GLFW_KEY_A + (letters.charAt(i) - 'A'), codes[i]);
		}
		map(GLFW.GLFW_KEY_ESCAPE, KEY_ESCAPE);
		map(GLFW.GLFW_KEY_MINUS, KEY_MINUS);
		map(GLFW.GLFW_KEY_EQUAL, KEY_EQUALS);
		map(GLFW.GLFW_KEY_BACKSPACE, KEY_BACK);
		map(GLFW.GLFW_KEY_TAB, KEY_TAB);
		map(GLFW.GLFW_KEY_LEFT_BRACKET, KEY_LBRACKET);
		map(GLFW.GLFW_KEY_RIGHT_BRACKET, KEY_RBRACKET);
		map(GLFW.GLFW_KEY_ENTER, KEY_RETURN);
		map(GLFW.GLFW_KEY_LEFT_CONTROL, KEY_LCONTROL);
		map(GLFW.GLFW_KEY_SEMICOLON, KEY_SEMICOLON);
		map(GLFW.GLFW_KEY_APOSTROPHE, KEY_APOSTROPHE);
		map(GLFW.GLFW_KEY_GRAVE_ACCENT, KEY_GRAVE);
		map(GLFW.GLFW_KEY_LEFT_SHIFT, KEY_LSHIFT);
		map(GLFW.GLFW_KEY_BACKSLASH, KEY_BACKSLASH);
		map(GLFW.GLFW_KEY_COMMA, KEY_COMMA);
		map(GLFW.GLFW_KEY_PERIOD, KEY_PERIOD);
		map(GLFW.GLFW_KEY_SLASH, KEY_SLASH);
		map(GLFW.GLFW_KEY_RIGHT_SHIFT, KEY_RSHIFT);
		map(GLFW.GLFW_KEY_KP_MULTIPLY, KEY_MULTIPLY);
		map(GLFW.GLFW_KEY_LEFT_ALT, KEY_LMENU);
		map(GLFW.GLFW_KEY_SPACE, KEY_SPACE);
		map(GLFW.GLFW_KEY_CAPS_LOCK, KEY_CAPITAL);
		for (int i = 0; i < 10; i++) {
			map(GLFW.GLFW_KEY_F1 + i, KEY_F1 + i);
		}
		map(GLFW.GLFW_KEY_F11, KEY_F11);
		map(GLFW.GLFW_KEY_F12, KEY_F12);
		map(GLFW.GLFW_KEY_KP_ENTER, KEY_NUMPADENTER);
		map(GLFW.GLFW_KEY_RIGHT_CONTROL, KEY_RCONTROL);
		map(GLFW.GLFW_KEY_RIGHT_ALT, KEY_RMENU);
		map(GLFW.GLFW_KEY_HOME, KEY_HOME);
		map(GLFW.GLFW_KEY_UP, KEY_UP);
		map(GLFW.GLFW_KEY_PAGE_UP, KEY_PRIOR);
		map(GLFW.GLFW_KEY_LEFT, KEY_LEFT);
		map(GLFW.GLFW_KEY_RIGHT, KEY_RIGHT);
		map(GLFW.GLFW_KEY_END, KEY_END);
		map(GLFW.GLFW_KEY_DOWN, KEY_DOWN);
		map(GLFW.GLFW_KEY_PAGE_DOWN, KEY_NEXT);
		map(GLFW.GLFW_KEY_INSERT, KEY_INSERT);
		map(GLFW.GLFW_KEY_DELETE, KEY_DELETE);
		map(GLFW.GLFW_KEY_LEFT_SUPER, KEY_LMETA);
		map(GLFW.GLFW_KEY_RIGHT_SUPER, KEY_RMETA);
		if (row.isEmpty()) {
			throw new IllegalStateException();
		}
	}

	/** GLFW key to LWJGL 2 key code (0 if unknown). */
	public static int fromGlfw(int glfw) {
		return glfw >= 0 && glfw < FROM_GLFW.length ? FROM_GLFW[glfw] : 0;
	}

	public static int toGlfw(int lw) {
		return lw > 0 && lw < TO_GLFW.length ? TO_GLFW[lw] : -1;
	}

	public static boolean isKeyDown(int lw) {
		int g = toGlfw(lw);
		if (g < 0) {
			return false;
		}
		return com.mojang.blaze3d.platform.InputConstants.isKeyDown(
				net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), g);
	}

	public static String getKeyName(int lw) {
		int g = toGlfw(lw);
		if (g < 0) {
			return "NONE";
		}
		String s = GLFW.glfwGetKeyName(g, 0);
		return s == null ? com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM.getOrCreate(g).getDisplayName().getString()
				: s.toUpperCase();
	}

	// the event being handled (set by the hooks)
	public static int eventKey;
	public static boolean eventState;
	public static boolean eventRepeat;
	public static char eventChar;

	public static int getEventKey() {
		return eventKey;
	}

	public static boolean getEventKeyState() {
		return eventState;
	}

	public static boolean isRepeatEvent() {
		return eventRepeat;
	}

	public static char getEventCharacter() {
		return eventChar;
	}

	public static void enableRepeatEvents(boolean b) {
	}
}
