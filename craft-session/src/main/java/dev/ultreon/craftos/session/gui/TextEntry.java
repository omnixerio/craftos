package dev.ultreon.craftos.session.gui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

public class TextEntry extends Widget {
    private String text;
    private int cursor;
    private final Color background = new Color(0.9f, 0.9f, 0.9f, 1f);
    private final Color sideColor = new Color(0.7f, 0.7f, 0.7f, 1f);
    private final GlyphLayout layout = new GlyphLayout();

    public TextEntry(Container parent, String text, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.text = text;
        this.cursor = 0;
    }

    public String getText() {
        return text;
    }

    public int getCursor() {
        return cursor;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setCursor(int cursor) {
        this.cursor = cursor;
    }

    public void render(Batch batch) {
        int extent = isFocused() ? 2 : 1;
        int x = this.x;
        int y = this.y - extent;

        fill(batch, 0, height, width, extent, sideColor);
        fill(batch, 0, 0, width, height, background);

        BitmapFont font = getFont();

        if (cursor < text.length()) {
            font.setColor(0.8f, 0.8f, 0.8f, 1f);
            font.draw(batch, text.substring(0, cursor), x + 1, y + height / 2f + font.getCapHeight() / 2f);
            layout.setText(font, text.substring(0, cursor));
            font.setColor(1f, 1f, 1f, 1f);
            font.draw(batch, text.substring(cursor), x + 1 + layout.width, y + height / 2f + font.getCapHeight() / 2f);

            fill(batch, (int) layout.width, 0, 1, height, sideColor);
            return;
        }

        if (text.isEmpty()) {
            return;
        }
        font.setColor(1f, 1f, 1f, 1f);
        font.draw(batch, text, x + 1, y + height / 2f + font.getCapHeight() / 2f);
    }
}
