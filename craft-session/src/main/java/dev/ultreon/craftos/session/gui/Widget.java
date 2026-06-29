package dev.ultreon.craftos.session.gui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import dev.ultreon.craftos.session.CraftSession;
import org.jetbrains.annotations.Nullable;

public abstract class Widget {
    protected final @Nullable Container parent;
    public int x;
    public int y;
    public int width;
    public int height;
    protected boolean hovered = false;

    protected int mouseX;
    protected int mouseY;

    public Widget(@Nullable Container parent, int x, int y, int width, int height) {
        this.parent = parent;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public BitmapFont getFont() {
        return CraftSession.get().getFont();
    }

    public abstract void render(Batch batch);

    public void fill(Batch batch, int x, int y, float width, float height, Color background) {
        batch.setColor(background);
        batch.draw(CraftSession.get().getWhite(), x, y, width, height);
        batch.setColor(Color.WHITE);
    }

    public void mouseMoved(int x, int y) {
        this.mouseX = x;
        this.mouseY = y;

        if (parent != null) {
            hovered = x >= this.x && x <= this.x + width && y >= this.y && y <= this.y + height;
        } else {
            hovered = false;
        }
    }

    public boolean isHovered() {
        return hovered;
    }

    public void mouseClicked(int button) {
        if (button == 0) {
            mouseClicked();
        } else if (button == Input.Buttons.RIGHT) {
            mouseRightClicked();
        }
    }

    public void mouseRightClicked() {
        if (parent != null) {
            parent.removeChild(this);
        }

        onContextMenu();
    }

    protected void onContextMenu() {

    }

    public void mouseClicked() {
        if (parent != null) {
            parent.focus(this);
        }
    }

    public void keyTyped(char character) {
        if (character == '\n') {
            keyTypedEnter();
        } else if (character == '\t') {
            keyTypedTab();
        } else {
            keyTypedOther(character);
        }
    }

    protected void keyTypedOther(char character) {

    }

    protected void keyTypedEnter() {

    }

    protected void keyTypedTab() {

    }

    public boolean isFocused() {
        return parent == null || parent.focused == this;
    }

    public void dispose() {

    }

    public void mouseReleased() {

    }

    public void keyUp(int keycode) {

    }

    public void keyDown(int keycode) {

    }

    public void mousePressed() {

    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    protected void mouseScrolled(float amount) {

    }
}
