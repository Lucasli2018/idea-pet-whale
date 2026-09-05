package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetManifest;
import com.dsh.petwhale.resource.PetResources;
import com.dsh.petwhale.state.PetAnimation;
import com.dsh.petwhale.state.PetStateService;
import com.dsh.petwhale.state.PetStateSnapshot;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders one frame of the active animation and advances through the
 * per-track duration schedule. Repaints itself off the same {@Timer}
 * that {@PetFrame} uses for the bubble timer — keeping paint on the
 * EDT and free of any background-thread side effects.
 */
public final class PetPanel extends JPanel {

    private final PetStateService service;
    private final PetFrame frame;
    private final AtomicReference<PetResources.Theme> themeRef = new AtomicReference<>();
    private final AtomicReference<PetAnimation> currentAnimation =
            new AtomicReference<>(PetAnimation.IDLE);
    private final AtomicReference<long[]> currentDurations =
            new AtomicReference<>(new long[] { 500L });
    private int frameIndex = 0;
    private long frameStartedAt = System.currentTimeMillis();

    public PetPanel(@NotNull PetStateService service, @NotNull PetFrame frame) {
        this.service = service;
        this.frame = frame;
        setOpaque(false);
        setSize(PetManifest.CELL_WIDTH, PetManifest.CELL_HEIGHT);

        // Drag support — lift-and-drop the whole frame around the screen.
        MouseAdapter press = new MouseAdapter() {
            private int dragOffsetX;
            private int dragOffsetY;

            @Override
            public void mousePressed(MouseEvent e) {
                dragOffsetX = e.getX();
                dragOffsetY = e.getY();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) frame.showHoverPanel(e.getX(), e.getY());
            }
        };
        MouseMotionAdapter drag = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                java.awt.Point p = frame.getLocation();
                frame.setLocation(
                        p.x + e.getX() - 12,
                        p.y + e.getY() - 12);
            }
        };
        addMouseListener(press);
        addMouseMotionListener(drag);

        // Frame advancement — re-evaluate which animation we should be on
        // every 100ms; advance the inner frame index when its duration
        // elapses.
        Timer advance = new Timer(100, e -> {
            long now = System.currentTimeMillis();
            PetStateSnapshot snapshot = service.render();
            PetAnimation desired = snapshot.animation();
            if (desired != currentAnimation.get()) {
                currentAnimation.set(desired);
                frameIndex = 0;
                frameStartedAt = now;
                themeRef.set(service.currentTheme());
                int row = PetResources.rowOf(desired);
                currentDurations.set(safeDurations(service.currentTheme(), desired, row));
            }
            long[] durations = currentDurations.get();
            if (durations.length > 0) {
                int elapsed = (int) (now - frameStartedAt);
                int consumed = 0;
                int idx = frameIndex;
                while (idx < durations.length && consumed + durations[idx] <= elapsed) {
                    consumed += durations[idx];
                    idx++;
                }
                if (idx >= durations.length) {
                    // sequence loops
                    frameIndex = 0;
                    frameStartedAt = now;
                } else if (idx != frameIndex) {
                    frameIndex = idx;
                }
            }
            SwingUtilities.invokeLater(PetPanel.this::repaint);
        });
        advance.start();

        themeRef.set(service.currentTheme());
        currentDurations.set(initialDurations());
    }

    private long[] initialDurations() {
        PetAnimation animation = currentAnimation.get();
        int row = PetResources.rowOf(animation);
        return safeDurations(service.currentTheme(), animation, row);
    }

    private static long[] safeDurations(PetResources.Theme theme, PetAnimation animation, int row) {
        try {
            int[] track = theme.manifest().durations(animation);
            if (track.length > 0) return toLongArray(track);
            int frameCount = theme.manifest().frameCount(row);
            int[] def = new int[frameCount];
            int[] pattern = theme.manifest().defaultDurationsForRow(row);
            for (int i = 0; i < frameCount; i++) {
                def[i] = pattern[i % pattern.length];
            }
            return toLongArray(def);
        } catch (RuntimeException ex) {
            return new long[] { 500 };
        }
    }

    private static long[] toLongArray(int[] arr) {
        long[] out = new long[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[i];
        return out;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            PetResources.Theme theme = themeRef.get();
            if (theme == null) return;
            BufferedImage[] frames = theme.framesFor(currentAnimation.get());
            if (frames.length == 0) return;
            int idx = Math.min(frameIndex, frames.length - 1);
            g2.drawImage(frames[idx], 0, 0, null);
        } finally {
            g2.dispose();
        }
    }
}