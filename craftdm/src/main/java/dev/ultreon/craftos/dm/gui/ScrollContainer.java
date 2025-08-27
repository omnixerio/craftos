package dev.ultreon.craftos.dm.gui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import org.jetbrains.annotations.Nullable;

public class ScrollContainer extends Container {
    private final Color background = new Color(0, 0, 0, 0.5f);
    private float scroll = 0f;
    private FrameBuffer buffer;
    private int oldWidth = -1, oldHeight = -1;
    private float scrollGoal;

    public ScrollContainer(@Nullable Container parent, int x, int y, int width, int height) {
        super(parent, x, y, width, height);
    }

    public float getScroll() {
        return scroll;
    }

    public void setScroll(float scroll) {
        this.scroll = scroll;
    }

    @Override
    public void render(Batch batch) {
        if (!ScissorStack.pushScissors(rect.set(x * 2, y * 2, width * 2, height * 2))) return;

        if (scrollGoal > scroll) {
            scroll += (scrollGoal - scroll) * 0.3f;
            if (scrollGoal < scroll) {
                scroll = scrollGoal;
            }
        } else if (scrollGoal < scroll) {
            scroll -= (scroll - scrollGoal) * 0.3f;
            if (scrollGoal > scroll) {
                scroll = scrollGoal;
            }
        }

        int maxY = 0;
        for (Widget child : getChildren()) {
            maxY = Math.min(maxY, child.y);
        }
        this.scroll = Math.clamp(scroll, maxY, 0);

        batch.getProjectionMatrix().translate(x, y, 0);
        batch.setProjectionMatrix(batch.getProjectionMatrix());

        fill(batch, 0, 0, width, height, background);
        batch.getProjectionMatrix().translate(0, -scroll, 0);
        batch.setProjectionMatrix(batch.getProjectionMatrix());

        try {
            for (Widget child : getChildren()) {
                child.render(batch);
            }
        } finally {
            batch.getProjectionMatrix().translate(0, scroll, 0);
            batch.getProjectionMatrix().translate(-x, -y, 0);
            batch.setProjectionMatrix(batch.getProjectionMatrix());
            ScissorStack.popScissors();
        }
    }

    @Override
    public void mouseMoved(int x, int y) {
        for (int i = 0; i < getChildren().size(); i++) {
            Widget child = getChildren().get(i);
            child.mouseMoved(x, y + (int) scroll - this.y);
        }
        if (parent != null) {
            hovered = x >= this.x && x <= this.x + width && y >= this.y && y <= this.y + height;
        } else {
            hovered = false;
        }
    }

    @Override
    public void mouseScrolled(float amount) {
        if (!hovered) return;
        super.mouseScrolled(amount);
        scrollGoal -= amount * 20;
    }
}
