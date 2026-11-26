package net.plebmc.client.hud;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/** A text field that draws stars instead of what you type. */
final class PasswordField extends GuiTextField {

	PasswordField(int id, FontRenderer font, int x, int y, int w, int h) {
		super(id, font, x, y, w, h);
	}

	@Override
	public void drawTextBox() {
		String real = getText();
		int cursor = getCursorPosition();
		StringBuilder stars = new StringBuilder();
		for (int i = 0; i < real.length(); i++) {
			stars.append('*');
		}
		setText(stars.toString());
		setCursorPosition(cursor);
		super.drawTextBox();
		setText(real);
		setCursorPosition(cursor);
	}
}
