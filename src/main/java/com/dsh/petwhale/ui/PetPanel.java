package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetDialogue;
import com.dsh.petwhale.resource.PetManifest;
import com.dsh.petwhale.resource.PetResources;
import com.dsh.petwhale.state.PetActivityPhase;
import com.dsh.petwhale.state.PetAnimation;
import com.dsh.petwhale.state.PetSettingsState;
import com.dsh.petwhale.state.PetStateService;
import com.dsh.petwhale.state.PetStateSnapshot;
import org.jetbrains.annotations.NotNull;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 桌宠主绘图面板：按帧绘制当前动画 + 推进内部帧索引 + 鼠标悬停交互。
 *
 * <p><b>职责：</b>
 * <ol>
 *   <li>每 100ms 从 {@link PetStateService} 拉一次快照，决定当前该播哪个动画</li>
 *   <li>按 {@link PetManifest#durations} 的帧时长推进内部帧索引，到尾部就回到 0</li>
 *   <li>在 EDT 上调用 {@code repaint()} 触发 {@link #paintComponent} 重绘</li>
 *   <li>处理鼠标拖拽（修改 PetFrame 的位置）、点击交互、悬停浮现交互面板</li>
 *   <li>对 {@link PetAnimation#JUMPING} 叠加垂直弹跳偏移，让庆祝动画真正"跳起来"</li>
 * </ol>
 *
 * <p><b>线程模型：</b>所有 Swing 操作都强制走 {@link SwingUtilities#invokeLater}，
 * Timer 自身也在 EDT 上触发，所以本类是单线程的。
 * 三个 {@link AtomicReference} 字段仅出于习惯写法——本类不被多线程访问。</p>
 *
 * <p><b>透明背景：</b>{@code setOpaque(false)} 让 JPanel 不绘制自身背景，
 * 桌宠窗口的透明区才能透出桌面。</p>
 *
 * <p><b>跳跃缓冲：</b>窗口在宠物上下各扩展 {@value #JUMP_ROOM} 像素（按缩放比例缩放），
 * 平时宠物居中绘制，跳跃时向上偏移而不被窗口顶部裁切。</p>
 */
public final class PetPanel extends JPanel {

    /** 跳跃缓冲高度（100% 缩放时），给跳跃动作留出头部空间 */
    public static final int JUMP_ROOM = 28;
    /** 当前主题（精灵图 + 帧数据） */
    private final PetStateService service;
    private final PetFrame frame;
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
    /** 主题切换监听（shutdown 时注销，防止插件重载后泄漏） */
    private final PetStateService.Listener themeListener;
    /** 连点判定窗口（毫秒） */
    static final int RAPID_CLICK_WINDOW_MS = 1000;
    /** 触发"撒娇"的连点次数阈值 */
    static final int RAPID_CLICK_THRESHOLD = 4;
    /** 事件台词对点击的抑制窗口（毫秒）：点击自己冒台词，事件通道让路 */
    static final int EVENT_LINE_SUPPRESS_MS = 1500;
    /** 最近一次点击时间戳（事件台词去重用） */
    private long lastClickAt = 0;
    /** 上一次点击时间戳，用于双击判定 */
    private long previousClickAt = 0;
    /** 最近一次交互时间戳，用于空闲台词计时 */
    private long lastInteractAt = System.currentTimeMillis();
    /** 临时覆盖的动画（拖拽挥手、双击反馈等），null 表示由状态机接管 */
    private PetAnimation overrideAnimation;
    /** 临时覆盖动画的过期时间 */
    private long overrideUntil = 0;
    /** 双击判定窗口（毫秒） */
    static final int DOUBLE_CLICK_WINDOW_MS = 400;

    public PetPanel(@NotNull PetStateService service, @NotNull PetFrame frame) {
        this.service = service;
        this.frame = frame;
        setOpaque(false);
        setPreferredSize(preferredPetSize(100));

        // === 主题切换即时刷新（自愈式：任何快照都校验渲染主题与运行时主题一致，
        //     不依赖具体 reason 字符串，避免预览切换被吞或错过广播） ===
        this.themeListener = (snapshot, reason) -> SwingUtilities.invokeLater(() -> {
            PetResources.Theme rendered = themeRef.get();
            if (rendered == null || rendered.id() != service.theme()) {
                refreshThemeResources();
            }
        });
        service.addListener(themeListener);

        // === 拖拽支持 + 点击交互 + 悬停交互 ===
        MouseAdapter press = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                pressX = e.getX();
                pressY = e.getY();
                draggedSincePress = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    frame.savePosition();
                } else if (draggedSincePress) {
                    onDragFinished();
                } else {
                    handleClick();
                }
                frame.savePosition();
                pressX = -1;
                pressY = -1;
                draggedSincePress = false;
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                updateHoverZone(e);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                frame.hideHoverPanel();
            }
        };
        MouseMotionAdapter drag = new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressX < 0 || pressY < 0) return;
                java.awt.Point p = frame.getLocation();
                frame.setLocation(
                        p.x + e.getX() - pressX,
                        p.y + e.getY() - pressY);
                draggedSincePress = true;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (pressX >= 0 || pressY >= 0) return; // 拖拽中不判区
                updateHoverZone(e);
            }
        };
        addMouseListener(press);
        addMouseMotionListener(drag);

        // === 帧推进定时器 ===
        Timer advance = new Timer(100, e -> {
            long now = System.currentTimeMillis();
            PetStateSnapshot snapshot = service.render();
            PetAnimation desired = snapshot.animation();

            // 临时动画覆盖：拖拽挥手、双击反馈等视觉事件优先级高于状态机
            if (overrideAnimation != null && now < overrideUntil) {
                desired = overrideAnimation;
            } else {
                overrideAnimation = null;
            }

            if (desired != currentAnimation.get()) {
                PetAnimation previous = currentAnimation.get();
                currentAnimation.set(desired);
                frameIndex = 0;
                frameStartedAt = now;
                themeRef.set(service.currentTheme());
                int row = PetResources.rowOf(desired);
                currentDurations.set(safeDurations(service.currentTheme(), desired, row));
                showEventLineIfNeeded(previous, desired, now);
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
                    frameIndex = 0;
                    frameStartedAt = now;
                } else if (idx != frameIndex) {
                    frameIndex = idx;
                }
            }
            SwingUtilities.invokeLater(PetPanel.this::repaint);
        });
        advance.start();

        // === 久坐关怀定时器 ===
        Timer careTimer = new Timer(60_000, e -> {
            boolean enabled = careEnabled();
            if (service.care().shouldRemind(System.currentTimeMillis(), enabled)) {
                frame.showBubble(PetDialogue.random("care"));
            }
        });
        careTimer.start();

        // === 空闲随机台词定时器：每 35~55 秒在空闲时冒泡 ===
        Timer idleTimer = new Timer(nextIdleBubbleDelayMs(), e -> {
            if (service.isShowDecorations() && currentAnimation.get() == PetAnimation.IDLE) {
                long now = System.currentTimeMillis();
                if (now - lastInteractAt >= 30_000) {
                    String line = isSleepyTime(now) ? PetDialogue.random("sleepy") : PetDialogue.random("bored");
                    if (line != null) frame.showBubble(line);
                }
            }
            ((Timer) e.getSource()).setDelay(nextIdleBubbleDelayMs());
        });
        idleTimer.start();

        themeRef.set(service.currentTheme());
        currentDurations.set(initialDurations());
    }

    /**
     * 带跳跃缓冲的宠物窗口首选尺寸。
     * 宽度 = 精灵宽度 × 缩放；高度 = 精灵高度 × 缩放 + 上下各 {@value #JUMP_ROOM}。
     */
    @NotNull
    public static Dimension preferredPetSize(int sizePercent) {
        int w = PetSettingsState.scaledWidth(sizePercent);
        int h = PetSettingsState.scaledHeight(sizePercent) + scaledJumpRoom(sizePercent) * 2;
        return new Dimension(w, h);
    }

    /** 按当前缩放比例计算跳跃缓冲高度。 */
    public static int scaledJumpRoom(int sizePercent) {
        return Math.max(1, JUMP_ROOM * PetSettingsState.clampSizePercent(sizePercent) / 100);
    }

    /** 宠物精灵在面板内的基准 Y 坐标（顶部留出一半缓冲）。 */
    public static int petBaseY(int sizePercent) {
        return scaledJumpRoom(sizePercent);
    }

    /** 读"久坐关怀"开关；设置服务不可用时默认开启。 */
    private static boolean careEnabled() {
        try {
            if (com.intellij.openapi.application.ApplicationManager.getApplication() == null) {
                return true;
            }
            PetSettingsState settings = com.intellij.openapi.application.ApplicationManager
                    .getApplication().getService(PetSettingsState.class);
            return settings == null || settings.isCareEnabled();
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 按鼠标在精灵内的纵向位置分区触发悬浮层：
     * 顶边下方 25% 高度以上（头部）→ 数值胶囊；底边上方 25% 高度以下（脚部）→ 按钮卡片；
     * 中部与上下跳跃缓冲区不触发。
     */
    private void updateHoverZone(MouseEvent e) {
        int sizePercent = frame.currentSizePercent();
        int spriteH = PetSettingsState.scaledHeight(sizePercent);
        int spriteY = e.getY() - petBaseY(sizePercent); // 转换为精灵内 Y（面板顶部有跳跃缓冲）
        if (spriteY < 0 || spriteY >= spriteH) {
            frame.hideHoverPanel(); // 落在缓冲区：不触发
            return;
        }
        if (spriteY < spriteH / 4) {
            frame.showStatsOverlay(); // 头部
        } else if (spriteY >= spriteH * 3 / 4) {
            frame.showCardOverlay(); // 脚部
        } else {
            frame.hideHoverPanel(); // 中部
        }
    }

    private void showEventLineIfNeeded(PetAnimation previous, PetAnimation desired, long now) {
        if (!service.isShowDecorations()) return;
        if (now - lastClickAt <= EVENT_LINE_SUPPRESS_MS) return;
        if (desired == PetAnimation.JUMPING && previous != PetAnimation.JUMPING) {
            frame.showBubble(PetDialogue.random("done"));
        } else if (desired == PetAnimation.FAILED && previous != PetAnimation.FAILED) {
            frame.showBubble(PetDialogue.random("failed"));
        }
    }

    /** 拖拽结束触发挥手动画 + 拖拽台词。 */
    private void onDragFinished() {
        lastInteractAt = System.currentTimeMillis();
        playOverrideAnimation(PetAnimation.WAVING, 1_800);
        String line = PetDialogue.random("drag");
        if (line != null) frame.showBubble(line);
    }

    /** 临时播放指定动画一段时间，过期后自动交回状态机。 */
    private void playOverrideAnimation(PetAnimation animation, int durationMs) {
        overrideAnimation = animation;
        overrideUntil = System.currentTimeMillis() + durationMs;
        currentAnimation.set(animation);
        frameIndex = 0;
        frameStartedAt = System.currentTimeMillis();
        themeRef.set(service.currentTheme());
        currentDurations.set(safeDurations(service.currentTheme(), animation, PetResources.rowOf(animation)));
        repaint();
    }

    /** 下一次空闲冒泡的随机间隔（35~55 秒）。 */
    private static int nextIdleBubbleDelayMs() {
        return 35_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(20_000);
    }

    /** 判断当前是否为深夜（22:00 ~ 05:59），用于空闲台词切换。 */
    private static boolean isSleepyTime(long timestamp) {
        int hour = java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()).getHour();
        return hour >= 22 || hour < 6;
    }

    /** 立即按运行时主题刷新精灵图与帧时长并重绘（设置页切主题的直通刷新入口）。 */
    void refreshThemeResources() {
        PetAnimation animation = currentAnimation.get();
        themeRef.set(service.currentTheme());
        currentDurations.set(safeDurations(service.currentTheme(), animation, PetResources.rowOf(animation)));
        frameIndex = 0;
        frameStartedAt = System.currentTimeMillis();
        repaint();
    }

    public void shutdown() {
        service.removeListener(themeListener);
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

    private void handleClick() {
        long now = System.currentTimeMillis();
        lastClickAt = now;
        lastInteractAt = now;
        while (!clickTimes.isEmpty() && now - clickTimes.peekFirst() > RAPID_CLICK_WINDOW_MS) {
            clickTimes.pollFirst();
        }
        clickTimes.addLast(now);

        // 点击时先收起悬停面板，避免面板遮挡点击反馈
        frame.hideHoverPanel();

        // 连点阈值优先
        if (clickTimes.size() >= RAPID_CLICK_THRESHOLD) {
            clickTimes.clear();
            service.setPhase(PetActivityPhase.FAILED, null, PetDialogue.random("rapid"));
            frame.showBubble(PetDialogue.random("rapid"));
            return;
        }

        // 双击检测：距上次点击 < 400ms 触发开心反馈
        if (previousClickAt > 0 && now - previousClickAt <= DOUBLE_CLICK_WINDOW_MS) {
            playOverrideAnimation(PetAnimation.WAVING, 1_500);
            frame.showBubble(PetDialogue.random("happy"));
            previousClickAt = 0;
            return;
        }

        previousClickAt = lastClickAt;
        service.setPhase(PetActivityPhase.DONE, null, PetDialogue.random("click"));
        frame.showBubble(PetDialogue.random("click"));
    }

    /**
     * 计算跳跃动画的垂直偏移（像素，已按当前缩放比例缩放）。
     * 使用经典的 anticipation → 跃起 → 落地曲线：
     * <pre>
     *   帧 0: 0        （待机预备）
     *   帧 1: -0.4h    （蹬地屈膝后的最低点，anticipation）
     *   帧 2: -1.0h    （最高点）
     *   帧 3: -0.5h    （下落中）
     *   帧 4: 0        （落地）
     * </pre>
     * 其中 h = JUMP_ROOM，保证最高点刚好用到全部头部缓冲而不裁切。
     */
    private int jumpYOffset(int sizePercent) {
        if (currentAnimation.get() != PetAnimation.JUMPING) return 0;
        int room = scaledJumpRoom(sizePercent);
        switch (frameIndex) {
            case 0: return 0;
            case 1: return (int) Math.round(-0.40 * room);
            case 2: return -room;
            case 3: return (int) Math.round(-0.50 * room);
            case 4: return 0;
            default: return 0;
        }
    }

    /**
     * Swing 在 EDT 上回调的绘制方法。
     * 先用 {@link java.awt.Composite#Clear} 整面擦除到全透明，再按当前面板尺寸缩放
     * 绘制当前帧；跳跃动画会在基准 Y 上叠加弹跳偏移。
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setComposite(java.awt.AlphaComposite.Clear);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setComposite(java.awt.AlphaComposite.SrcOver);

            PetResources.Theme theme = themeRef.get();
            if (theme == null) return;
            BufferedImage[] frames = theme.framesFor(currentAnimation.get());
            if (frames.length == 0) return;
            int idx = Math.min(frameIndex, frames.length - 1);

            int sizePercent = frame.currentSizePercent();
            int baseY = petBaseY(sizePercent);
            int y = baseY + jumpYOffset(sizePercent);
            int spriteH = PetSettingsState.scaledHeight(sizePercent);
            int spriteW = PetSettingsState.scaledWidth(sizePercent);
            // 缩放绘制到精灵尺寸（而非填满整个窗口高度，窗口额外高度是缓冲）
            g2.drawImage(frames[idx], 0, y, spriteW, y + spriteH, null);
        } finally {
            g2.dispose();
        }
    }
}
