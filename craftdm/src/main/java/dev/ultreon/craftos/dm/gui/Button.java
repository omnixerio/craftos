package dev.ultreon.craftos.dm.gui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;

public class Button extends Widget {
    private String text;
    private final Color background = new Color(0.9f, 0.9f, 0.9f, 1f);
    private final Color sideColor = new Color(0.5f, 0.5f, 0.5f, 1f);
    private final Color border = new Color(0.7f, 0.7f, 0.7f, 1f);
    private final Color backgroundDisabled = new Color(0.5f, 0.5f, 0.5f, 1f);
    private final Color sideColorDisabled = new Color(0.3f, 0.3f, 0.3f, 1f);
    private final Color borderDisabled = new Color(0.4f, 0.4f, 0.4f, 1f);
    private final GlyphLayout layout = new GlyphLayout();
    private Runnable callback = () -> {};
    private boolean enabled = true;

    public Button(Container parent, String text, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public Runnable getCallback() {
        return callback;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setCallback(Runnable callback) {
        this.callback = callback;
    }

    public void render(Batch batch) {
        int extent = isHovered() && Gdx.input.isButtonPressed(Input.Buttons.LEFT) && enabled ? 2 : 4;
        int x = this.x;
        int y = this.y - extent;

        fill(batch, x, y + extent, width, extent, enabled ? sideColor : sideColorDisabled);
        fill(batch, x, y + extent + extent, width, height, enabled ? border : borderDisabled);
        fill(batch, x + 1, y + extent + 1 + extent, width - 2, height - 2, enabled ? background : backgroundDisabled);

        if (text.isEmpty()) return;
        BitmapFont font = getFont();
        layout.setText(font, text);
        if (!enabled)
            font.setColor(0.3f, 0.3f, 0.3f, 1f);
        else
            font.setColor(0.2f, 0.2f, 0.2f, 1f);
        font.draw(batch, text, x + width / 2 - layout.width / 2, y + extent + extent + height - (font.getLineHeight() * font.getScaleY()));
    }

    @Override
    public void mouseClicked() {
        if (!enabled) return;
        this.callback.run();
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void disable() {
        this.enabled = false;
    }

    public void enable() {
        this.enabled = true;
    }
}
