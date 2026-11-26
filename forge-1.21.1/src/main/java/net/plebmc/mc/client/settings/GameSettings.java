package net.plebmc.mc.client.settings;

import net.minecraft.client.OptionInstance;

/**
 * 1.8's GameSettings over 1.21.1's Options. The fields mirror the real
 * options (refreshed on {@code Minecraft.getMinecraft()}); the few that
 * PlebClient writes are written back right away by {@link #push()}.
 */
public class GameSettings {
	public boolean smoothCamera, hideGUI, showDebugInfo, enableVsync, fancyGraphics, entityShadows, viewBobbing;
	public int thirdPersonView, renderDistanceChunks, limitFramerate, guiScale, particleSetting, clouds, ambientOcclusion;
	public float fovSetting, gammaSetting;
	public final KeyBinding keyBindForward = new KeyBinding(o -> o.keyUp), keyBindBack = new KeyBinding(o -> o.keyDown),
			keyBindLeft = new KeyBinding(o -> o.keyLeft), keyBindRight = new KeyBinding(o -> o.keyRight),
			keyBindJump = new KeyBinding(o -> o.keyJump), keyBindSneak = new KeyBinding(o -> o.keyShift),
			keyBindSprint = new KeyBinding(o -> o.keySprint), keyBindAttack = new KeyBinding(o -> o.keyAttack),
			keyBindUseItem = new KeyBinding(o -> o.keyUse), keyBindPlayerList = new KeyBinding(o -> o.keyPlayerList);

	private net.minecraft.client.Options o;

	public void refresh(net.minecraft.client.Options real) {
		o = real;
		smoothCamera = real.smoothCamera;
		hideGUI = real.hideGui;
		showDebugInfo = net.minecraft.client.Minecraft.getInstance().getDebugOverlay().showDebugScreen();
		enableVsync = real.enableVsync().get();
		fancyGraphics = real.graphicsMode().get() != net.minecraft.client.GraphicsStatus.FAST;
		entityShadows = real.entityShadows().get();
		viewBobbing = real.bobView().get();
		thirdPersonView = real.getCameraType().ordinal();
		renderDistanceChunks = real.renderDistance().get();
		limitFramerate = real.framerateLimit().get();
		guiScale = real.guiScale().get();
		particleSetting = real.particles().get().ordinal();
		clouds = real.cloudStatus().get().ordinal();
		ambientOcclusion = real.ambientOcclusion().get() ? 2 : 0;
		fovSetting = real.fov().get();
		gammaSetting = real.gamma().get().floatValue();
	}

	/** Writes the fields PlebClient changes back to the game. */
	public void push() {
		if (o != null) {
			o.smoothCamera = smoothCamera;
			o.hideGui = hideGUI;
		}
	}

	public void saveOptions() {
		push();
		if (o != null) {
			o.save();
		}
	}

	public String getKeyBinding(Options opt) {
		return opt == null ? "" : opt.label(o);
	}

	public float getOptionFloatValue(Options opt) {
		return opt.get(o);
	}

	public void setOptionFloatValue(Options opt, float v) {
		opt.set(o, v);
	}

	public boolean getOptionOrdinalValue(Options opt) {
		return opt.get(o) != 0f;
	}

	/** Cycles a switch or a list option by {@code by}. */
	public void setOptionValue(Options opt, int by) {
		opt.cycle(o, by);
	}

	/** 1.8's KeyBinding over a 1.21.1 KeyMapping. */
	public static final class KeyBinding {
		private final java.util.function.Function<net.minecraft.client.Options, net.minecraft.client.KeyMapping> f;

		KeyBinding(java.util.function.Function<net.minecraft.client.Options, net.minecraft.client.KeyMapping> f) {
			this.f = f;
		}

		public net.minecraft.client.KeyMapping real() {
			return f.apply(net.minecraft.client.Minecraft.getInstance().options);
		}

		public boolean isKeyDown() {
			return real().isDown();
		}

		public boolean isPressed() {
			return real().consumeClick();
		}

		public int getKeyCode() {
			return net.plebmc.mc.input.Keyboard.fromGlfw(real().getKey().getValue());
		}

		public static void setKeyBindState(KeyBinding k, boolean down) {
			k.real().setDown(down);
		}
	}

	/** The video options PlebClient's Video Settings screen shows, over 1.21.1's options. */
	public enum Options {
		RENDER_DISTANCE(2, 32, 1), FRAMERATE_LIMIT(10, 260, 10), GAMMA(0, 1, 0), MIPMAP_LEVELS(0, 4, 1), FOV(30, 110, 1),
		GUI_SCALE, GRAPHICS, PARTICLES, RENDER_CLOUDS, AMBIENT_OCCLUSION(true), ENABLE_VSYNC(true), VIEW_BOBBING(true),
		ENTITY_SHADOWS(true), USE_FULLSCREEN(true);

		private final boolean isFloat, isBool;
		private final float min, max, step;

		Options(float min, float max, float step) {
			this.isFloat = true;
			this.isBool = false;
			this.min = min;
			this.max = max;
			this.step = step;
		}

		Options(boolean bool) {
			this.isFloat = false;
			this.isBool = bool;
			this.min = this.max = this.step = 0;
		}

		Options() {
			this(false);
		}

		public boolean getEnumFloat() {
			return isFloat;
		}

		public boolean getEnumBoolean() {
			return isBool;
		}

		public float normalizeValue(float v) {
			return Math.max(0f, Math.min(1f, (snap(v) - min) / (max - min)));
		}

		public float denormalizeValue(float v) {
			return snap(min + (max - min) * Math.max(0f, Math.min(1f, v)));
		}

		private float snap(float v) {
			if (step > 0) {
				v = step * Math.round(v / step);
			}
			return Math.max(min, Math.min(max, v));
		}

		private OptionInstance<?> inst(net.minecraft.client.Options o) {
			switch (this) {
			case RENDER_DISTANCE:
				return o.renderDistance();
			case FRAMERATE_LIMIT:
				return o.framerateLimit();
			case GAMMA:
				return o.gamma();
			case MIPMAP_LEVELS:
				return o.mipmapLevels();
			case FOV:
				return o.fov();
			case GUI_SCALE:
				return o.guiScale();
			case GRAPHICS:
				return o.graphicsMode();
			case PARTICLES:
				return o.particles();
			case RENDER_CLOUDS:
				return o.cloudStatus();
			case AMBIENT_OCCLUSION:
				return o.ambientOcclusion();
			case ENABLE_VSYNC:
				return o.enableVsync();
			case VIEW_BOBBING:
				return o.bobView();
			case ENTITY_SHADOWS:
				return o.entityShadows();
			case USE_FULLSCREEN:
				return o.fullscreen();
			default:
				return null;
			}
		}

		float get(net.minecraft.client.Options o) {
			Object v = inst(o).get();
			if (v instanceof Boolean) {
				return (Boolean) v ? 1f : 0f;
			}
			if (v instanceof Number) {
				return ((Number) v).floatValue();
			}
			if (v instanceof Enum) {
				return ((Enum<?>) v).ordinal();
			}
			return 0f;
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		void set(net.minecraft.client.Options o, float v) {
			OptionInstance inst = inst(o);
			Object cur = inst.get();
			if (cur instanceof Double) {
				inst.set((double) v);
			} else if (cur instanceof Integer) {
				inst.set(Math.round(v));
			} else if (cur instanceof Boolean) {
				inst.set(v != 0f);
			}
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		void cycle(net.minecraft.client.Options o, int by) {
			OptionInstance inst = inst(o);
			Object cur = inst.get();
			if (cur instanceof Boolean) {
				inst.set(!(Boolean) cur);
			} else if (this == GUI_SCALE) {
				var mc = net.minecraft.client.Minecraft.getInstance();
				int max = mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
				inst.set(((Integer) cur + by + max + 1) % (max + 1));
				mc.resizeDisplay();
			} else if (cur instanceof Enum) {
				Object[] all = cur.getClass().getEnumConstants();
				int n = all.length;
				int i = (((Enum<?>) cur).ordinal() + by + n) % n;
				inst.set(all[i]);
			}
		}

		String label(net.minecraft.client.Options o) {
			OptionInstance<?> inst = inst(o);
			return inst == null ? "" : ((OptionInstance) inst).createButton(o, 0, 0, 10).getMessage().getString();
		}
	}
}
