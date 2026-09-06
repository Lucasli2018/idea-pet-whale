package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetDialogue;
import com.dsh.petwhale.resource.PetManifest;
import com.dsh.petwhale.resource.PetResources;
import com.dsh.petwhale.state.PetActivityPhase;
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
    /** 拖拽按下点 X（-1 = 未按下） */
    private int pressX = -1;
    /** 拖拽按下点 Y（-1 = 未按下） */
    private int pressY = -1;
    /** 本次按下后是否发生过拖动（true 时松手不算点击） */
    private boolean draggedSincePress = false;
    /** 1 秒内的点击时间戳（判断"连点摸头"） */
    private final java.util.ArrayDeque<Long> clickTimes = new java.util.ArrayDeque<>();
    /** 连点判定窗口（毫秒） */
    static final int RAPID_CLICK_WINDOW_MS = 1000;
    /** 触发"撒娇"的连点次数阈值 */
    static final int RAPID_CLICK_THRESHOLD = 4;

    public PetPanel(@NotNull PetStateService service, @NotNull PetFrame frame) {
        this.service = service;
        this.frame = frame;
        setOpaque(false);
        setSize(PetManifest.CELL_WIDTH, PetManifest.CELL_HEIGHT);

        // === 拖拽支持 + 点击交互 ===
        // 按下记录偏移，拖动时把整个 Frame 跟着鼠标移动
        MouseAdapter press = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 记录按下点：拖动偏移 = 鼠标当前坐标 - 按下点坐标
                pressX = e.getX();
                pressY = e.getY();
                draggedSincePress = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    frame.showHoverPanel(e.getX(), e.getY());
                } else if (!draggedSincePress) {
                    // 按下→松手之间没有明显拖动 = 点击交互
                    handleClick();
                }
                // 松手 = 拖拽（或点击）结束，把最新位置持久化
                frame.savePosition();
                pressX = -1;
                pressY = -1;
            }
        };
        MouseMotionAdapter drag = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressX < 0 || pressY < 0) {
                    // 没有按下记录（异常事件序列），跳过本次拖动
                    return;
                }
                java.awt.Point p = frame.getLocation();
                frame.setLocation(
                        p.x + e.getX() - pressX,
                        p.y + e.getY() - pressY);
                draggedSincePress = true;
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
     * 点击交互（EDT 上调用）：
     * <ul>
     *   <li>单击 → {@code DONE}（跳跃庆祝动画）+ 随机台词气泡</li>
     *   <li>{@value #RAPID_CLICK_WINDOW_MS}ms 内连点 ≥ {@value #RAPID_CLICK_THRESHOLD} 次
     *       → {@code FAILED}（撒娇沮丧动画）+ 撒娇台词，连点计数清零</li>
     * </ul>
     * 台词库缺失时只切动画不冒气泡，交互永不抛异常。
     */
    private void handleClick() {
        long now = System.currentTimeMillis();
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > RAPID_CLICK_WINDOW_MS) {
            clickTimes.pollFirst();
        }
        clickTimes.addLast(now);

        if (clickTimes.size() >= RAPID_CLICK_THRESHOLD) {
            clickTimes.clear();
            service.setPhase(PetActivityPhase.FAILED, null, PetDialogue.random("rapid"));
            frame.showBubble(PetDialogue.random("rapid"));
            return;
        }
        service.setPhase(PetActivityPhase.DONE, null, PetDialogue.random("click"));
        frame.showBubble(PetDialogue.random("click"));
    }

    /**
     * Swing 在 EDT 上回调的绘制方法。
     * 先用 {@link java.awt.Composite#Clear} 整面擦除到全透明（防止上一帧精灵图
     * 残留在透明窗体上形成"重影"），再按当前面板尺寸缩放绘制当前帧。
     * 任何异常都走 finally 释放 Graphics2D 资源，避免泄漏。
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            // 整面清屏到全透明：非 opaque 面板的 super.paintComponent 不会清像素，
            // 不主动擦除的话旧帧会一直叠在新帧上
            g2.setComposite(java.awt.AlphaComposite.Clear);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setComposite(java.awt.AlphaComposite.SrcOver);

            PetResources.Theme theme = themeRef.get();
            if (theme == null) return;
            BufferedImage[] frames = theme.framesFor(currentAnimation.get());
            if (frames.length == 0) return;
            int idx = Math.min(frameIndex, frames.length - 1);
            // 缩放绘制：面板尺寸即目标尺寸（PetFrame 按设置的比例 setSize）
            g2.drawImage(frames[idx], 0, 0, getWidth(), getHeight(), null);
        } finally {
            g2.dispose();
        }
    }
}