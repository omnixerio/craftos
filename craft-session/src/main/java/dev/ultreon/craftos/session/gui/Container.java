package dev.ultreon.craftos.session.gui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Container extends Widget {
    private final List<Widget> children = new ArrayList<>();
    protected final Rectangle rect = new Rectangle();
    public Widget focused;
    private Widget directFocused;

    public Container(@Nullable Container parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
    }

    public <T extends Widget> T addChild(T widget) {
        children.add(widget);
        return widget;
    }

    public void removeChild(Widget widget) {
        children.remove(widget);
    }

    public List<Widget> getChildren() {
        return children;
    }

    @Override
    public void render(Batch batch) {
        for (Widget child : children) {
            child.render(batch);
        }
    }

    @Override
    public void dispose() {
        for (Widget child : children) {
            child.dispose();
        }
    }

    public void focus(Widget widget) {
        this.focused = widget;
        if (parent != null) {
            parent.focus(this);
            parent.directFocus(widget);
        }
    }

    protected void directFocus(Widget widget) {
        this.directFocused = widget;
    }

    @Override
    public void mousePressed() {
        for (Widget child : children) {
            if (child.isHovered())
                child.mousePressed();
        }
    }

    @Override
    public void mouseReleased() {
        for (Widget child : children) {
            child.mouseReleased();
        }
    }

    @Override
    public void mouseClicked(int button) {
        for (Widget child : children) {
            if (child.isHovered())
                child.mouseClicked(button);
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);

        for (int i = children.size() - 1; i >= 0; i--) {
            Widget child = children.get(i);
            child.mouseMoved(x, y);
        }
    }

    @Override
    public void mouseRightClicked() {

    }

    @Override
    public void keyTyped(char character) {
        if (focused != null) {
            focused.keyTyped(character);
        }
    }

    @Override
    public void keyUp(int keycode) {
        if (focused != null) {
            focused.keyUp(keycode);
        }
    }

    @Override
    public void keyDown(int keycode) {
        if (focused != null) {
            focused.keyDown(keycode);
        }
    }

    protected void mouseScrolled(float amount) {
        for (Widget child : children) {
            child.mouseScrolled(amount);
        }
    }
}
