package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetManifest;
import com.dsh.petwhale.resource.PetTheme;
import com.dsh.petwhale.state.PetStateService;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The global transparent frame. Lives outside the IDEA window tree
 * (always-on-top, no decoration) so it floats over every editor and
 * tool window. A 1-pixel border helps users discover the clickable
 * area; right-click pops the hover panel; dragging moves the frame.
 *
 * <p>The Hide action disposes the frame and summons a tiny one-click
 * summon button anchored to the bottom-right of the screen — matching
 * the dsh-pet "summon {name}" affordance.
 */
public final class PetFrame {

    private final PetStateService service;
    private JFrame frame;
    private JWindow hover;
    private JWindow summon;
    private boolean visible = true;

    public PetFrame(@NotNull PetStateService service) {
        this.service = service;
    }

    public void show() {
        SwingUtilities.invokeLater(this::buildFrame);
    }

    public void dispose() {
        SwingUtilities.invokeLater(() -> {
            if (hover != null) hover.dispose();
            if (frame != null) frame.dispose();
            if (summon != null) summon.dispose();
            visible = false;
        });
    }

    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.setVisible(false);
            if (hover != null) hover.setVisible(false);
            if (summon == null) summon = buildSummon();
            summon.setVisible(true);
        });
    }

    public boolean isVisible() {
        return visible;
    }

    public java.awt.Point getLocation() {
        return frame == null ? new java.awt.Point() : frame.getLocation();
    }

    public void setLocation(int x, int y) {
        if (frame != null) frame.setLocation(x, y);
    }

    public void showHoverPanel(int localX, int localY) {
        if (frame == null) return;
        if (hover == null) {
            hover = new JWindow(frame);
            hover.setContentPane(new PetHoverPanel(this, service));
        }
        Point origin = frame.getLocation();
        hover.setLocation(origin.x + localX - 30, origin.y + localY + 12);
        hover.pack();
        hover.setVisible(true);
    }

    private void buildFrame() {
        frame = new JFrame("DSH Pet Whale");
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setSize(PetManifest.CELL_WIDTH, PetManifest.CELL_HEIGHT);
        frame.setLocation(defaultLocation());
        frame.setContentPane(new PetPanel(service, this));
        frame.setIconImage(makeIcon());
        frame.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showHoverPanel(e.getX(), e.getY());
            }
        });
        frame.setVisible(true);
        visible = true;
    }

    private JWindow buildSummon() {
        JWindow window = new JWindow();
        JButton btn = new JButton("召唤鲸鱼娘");
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            window.dispose();
            summon = null;
            if (frame != null) {
                frame.setVisible(true);
                return;
            }
            buildFrame();
        }));
        window.getContentPane().add(btn);
        window.pack();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        window.setLocation(
                screen.x + screen.width - window.getWidth() - 24,
                screen.y + screen.height - window.getHeight() - 24);
        return window;
    }

    private Point defaultLocation() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int x = bounds.x + bounds.width - PetManifest.CELL_WIDTH - 32;
        int y = bounds.y + bounds.height - PetManifest.CELL_HEIGHT - 64;
        if (x < 0) x = Math.max(0, screen.width - PetManifest.CELL_WIDTH - 32);
        if (y < 0) y = Math.max(0, screen.height - PetManifest.CELL_HEIGHT - 64);
        return new Point(x, y);
    }

    private java.awt.Image makeIcon() {
        // Tiny transparent placeholder — the taskbar doesn't need a fancy icon.
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        return img;
    }
}