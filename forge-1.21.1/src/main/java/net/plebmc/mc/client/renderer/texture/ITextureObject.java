package net.plebmc.mc.client.renderer.texture;

/** 1.8's ITextureObject. */
public interface ITextureObject {
	void setBlurMipmap(boolean blur, boolean mipmap);

	int getGlTextureId();

	final class Wrap implements ITextureObject {
		final net.minecraft.client.renderer.texture.AbstractTexture t;

		Wrap(net.minecraft.client.renderer.texture.AbstractTexture t) {
			this.t = t;
		}

		@Override
		public void setBlurMipmap(boolean blur, boolean mipmap) {
			t.setFilter(blur, mipmap);
		}

		@Override
		public int getGlTextureId() {
			return t.getId();
		}
	}
}
