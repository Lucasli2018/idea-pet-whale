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
 * 桌宠主绘图面板：按帧绘制当前动画 + 推进内部帧索引。
 *
 * <p><b>职责：</b>
 * <ol>
 *   <li>每 100ms 从 {@link PetStateService} 拉一次快照，决定当前该播哪个动画</li>
 *   <li>按 {@link PetManifest#durations} 的帧时长推进内部帧索引，到尾部就回到 0</li>
 *   <li>在 EDT 上调用 {@code repaint()} 触发 {@link #paintComponent} 重绘</li>
 *   <li>处理鼠标拖拽（修改 PetFrame 的位置）和右键唤起悬停面板</li>
 * </ol>
 *
 * <p><b>线程模型：</b>所有 Swing 操作都强制走 {@link SwingUtilities#invokeLater}，
 * Timer 自身也在 EDT 上触发，所以本类是单线程的。
 * 三个 {@link AtomicReference} 字段仅出于习惯写法——本类不被多线程访问。</p>
 *
 * <p><b>透明背景：</b>{@code setOpaque(false)} 让 JPanel 不绘制自身背景，
 * 桌宠窗口的透明区才能透出桌面。</p>
 */
public final class PetPanel extends JPanel {

    private final PetStateService service;
    private final PetFrame frame;
    /** 当前主题（精灵图 + 帧数据） */
    private final AtomicReference<PetResources.Theme> themeRef = new AtomicReference<>();
    /** 当前正在播放的动画 */
    private final AtomicReference<PetAnimation> currentAnimation =
            new AtomicReference<>(PetAnimation.IDLE);
    /** 当前动画的帧时长序列（毫秒） */
    private final AtomicReference<long[]> currentDurations =
            new AtomicReference<>(new long[] { 500L });
    /** 当前帧在序列中的索引 */
    private int frameIndex = 0;
    /** 当前动画起始时间戳，用于计算帧切换 */
    private long frameStartedAt = System.currentTimeMillis();

    public PetPanel(@NotNull PetStateService service, @NotNull PetFrame frame) {
        this.service = service;
        this.frame = frame;
        setOpaque(false);
        setSize(PetManifest.CELL_WIDTH, PetManifest.CELL_HEIGHT);

        // === 拖拽支持 ===
        // 按下记录偏移，拖动时把整个 Frame 跟着鼠标移动
        MouseAdapter press = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 偏移量：按下时的鼠标相对位置，拖动时用于计算新坐标
                // （目前用 e.getX/getY 直接定位，简单够用）
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

        // === 帧推进定时器 ===
        // 每 100ms 重新决策当前动画、推进帧索引、触发重绘
        Timer advance = new Timer(100, e -> {
            long now = System.currentTimeMillis();
            PetStateSnapshot snapshot = service.render();
            PetAnimation desired = snapshot.animation();

            // 动画变了：重置帧索引 + 加载新时长表 + 同步主题资源
            if (desired != currentAnimation.get()) {
                currentAnimation.set(desired);
                frameIndex = 0;
                frameStartedAt = now;
                themeRef.set(service.currentTheme());
                int row = PetResources.rowOf(desired);
                currentDurations.set(safeDurations(service.currentTheme(), desired, row));
            }

            // 推进帧索引：累计时长够了就前进
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
                    // 走完一轮，重置回起点（循环播放）
                    frameIndex = 0;
                    frameStartedAt = now;
                } else if (idx != frameIndex) {
                    frameIndex = idx;
                }
            }
            // EDT 上触发重绘
            SwingUtilities.invokeLater(PetPanel.this::repaint);
        });
        advance.start();

        // 初始化：把当前主题/时长表装好
        themeRef.set(service.currentTheme());
        currentDurations.set(initialDurations());
    }

    /** 构造时的初始时长表（用当前动画 = IDLE 算）。 */
    private long[] initialDurations() {
        PetAnimation animation = currentAnimation.get();
        int row = PetResources.rowOf(animation);
        return safeDurations(service.currentTheme(), animation, row);
    }

    /**
     * 安全地拿动画的帧时长：
     * 1. 优先用 manifest 里 tracks[anim].durations；
     * 2. 若空，用 defaultDurationsForRow（按 idle 节奏兜底）；
     * 3. 任何异常都返回单帧 500ms，避免卡死。
     */
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

    /** int[] 转 long[]（避免 32 位 int 毫秒累加溢出）。 */
    private static long[] toLongArray(int[] arr) {
        long[] out = new long[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[i];
        return out;
    }

    /**
     * Swing 在 EDT 上回调的绘制方法。
     * 绘制当前帧精灵图到 (0,0)。
     * 任何异常都走 finally 释放 Graphics2D 资源，避免泄漏。
     */
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