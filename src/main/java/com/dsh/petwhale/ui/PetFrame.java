package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetDialogue;
import com.dsh.petwhale.state.PetSettingsState;
import com.dsh.petwhale.state.PetStateService;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;

/**
 * 全局透明桌宠窗口。
 *
 * <p>关键设计：
 * <ul>
 *   <li>用 {@link JWindow} 而不是 {@code JFrame} —— 无边框窗体<b>不出现在任务栏和
 *       Alt-Tab 窗口列表里</b>，桌宠只是浮在桌面上的宠物，不是一个"程序窗口"</li>
 *   <li>{@code always-on-top=true} + 背景 {@code (0,0,0,0)} 完全透明 ——
 *       永远置顶，桌面背景透出来</li>
 *   <li>启动时从 {@link PetSettingsState} 恢复大小 / 不透明度 / 上次位置 / 主题；
 *       没有保存过位置则自动定位右下角（用 {@code getMaximumWindowBounds} 排除任务栏）</li>
 *   <li>鼠标悬停宠物时浮现 {@link PetHoverPanel} 交互面板（喂食 / 改名 / 隐藏）；
 *       隐藏/显示也可走设置页按钮</li>
 *   <li>鼠标拖动由 {@link PetPanel} 的 MouseAdapter 处理，本类只暴露位置读写；
 *       拖拽结束 / 隐藏 / 销毁时把当前位置写回设置持久化</li>
 *   <li>"隐藏"动作收起桌宠 + 显示一个小的"召唤鲸鱼娘"召唤按钮；隐藏状态跨重启记忆；
 *       {@link #unhide()} 反向恢复（设置页"显示"按钮调用）</li>
 *   <li>窗口上下扩展 {@link PetPanel#JUMP_ROOM} 给跳跃动作预留头部缓冲；
 *       所有保存/读取/拖拽位置都以<b>宠物视觉左上角</b>为准，兼容旧存档。</li>
 * </ul>
 *
 * <p>所有 Swing 操作都通过 {@link SwingUtilities#invokeLater} 切到 EDT。
 * 本类是被 {@link com.dsh.petwhale.startup.PetStartupActivity} 在任意线程上调用的，
 * 不假设调用方在 EDT。</p>
 */
public final class PetFrame {

    private final PetStateService service;
    /** 主桌宠窗口（JWindow：不进任务栏、不进 Alt-Tab） */
    private JWindow frame;
    /** 桌宠绘图面板（dispose 时注销主题监听用） */
    private PetPanel panel;
    /** 台词气泡（懒加载，单实例复用） */
    private PetBubble bubble;
    /** 悬停交互面板（懒加载） */
    private PetHoverPanel hover;
    /** "召唤鲸鱼娘"按钮（懒加载，仅在 hide 后存在） */
    private JWindow summon;
    /** 桌宠当前是否可见（可见包括 summon 状态——召唤按钮也算"用户能看到"） */
    private boolean visible = true;
    /** 用户设置（应用级持久化）；构造时解析，null 仅出现在平台测试环境 */
    private final PetSettingsState settings;
    /**
     * 实时预览的缩放百分比（设置页拖动大小滑块时写入）；null = 未在预览，
     * 渲染按持久化值。只影响绘制与定位计算，不落盘；Apply 后与持久化值一致，
     * Cancel / 关闭设置页时由 {@link #clearPreviewOverride()} 清除。
     */
    private volatile Integer previewSizePercent;
    /** 持久化主题是否已同步进运行时服务（仅首次 buildFrame 同步一次） */
    private boolean themeSynced;

    public PetFrame(@NotNull PetStateService service) {
        this.service = service;
        this.settings = loadSettings();
    }

    /** 应用级服务获取；headless/测试环境下可能拿不到，返回 null 并让调用方降级。 */
    private static PetSettingsState loadSettings() {
        try {
            if (ApplicationManager.getApplication() == null) return null;
            return ApplicationManager.getApplication().getService(PetSettingsState.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 显示桌宠窗口（EDT 异步）。 */
    public void show() {
        SwingUtilities.invokeLater(this::buildFrame);
    }

    /** 完全销毁所有窗口（EDT 异步）。不可恢复，需要重新再 {@link #show}。 */
    public void dispose() {
        SwingUtilities.invokeLater(() -> {
            savePosition();
            if (panel != null) panel.shutdown();
            if (bubble != null) bubble.dispose();
            if (hover != null) hover.dispose();
            if (frame != null) frame.dispose();
            if (summon != null) summon.dispose();
            visible = false;
        });
    }

    /**
     * 隐藏桌宠（不销毁）：桌宠窗口消失，右下角留下"召唤鲸鱼娘"按钮。
     * 隐藏状态写入设置持久化，下次启动直接收起。
     */
    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (settings != null) settings.setStartHidden(true);
            if (frame != null) frame.setVisible(false);
            if (bubble != null) bubble.hideNow();
            if (hover != null) hover.hideNow();
            if (summon == null) summon = buildSummon();
            summon.setVisible(true);
        });
    }

    /**
     * 恢复显示桌宠（设置页"显示鲸鱼娘"按钮调用）：
     * 销毁召唤按钮、重置"启动时收起"、把桌宠窗口放回来。
     */
    public void unhide() {
        SwingUtilities.invokeLater(() -> {
            if (settings != null) settings.setStartHidden(false);
            if (summon != null) {
                summon.dispose();
                summon = null;
            }
            if (frame != null) {
                frame.setVisible(true);
            } else {
                buildFrame();
            }
        });
    }

    /** 当前是否对用户可见（桌宠本体或召唤按钮任一显示都算可见） */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 取桌宠视觉左上角坐标（屏幕坐标系）。<br>
     * 注意：由于窗口在宠物上下扩展了跳跃缓冲，返回值是宠物 sprite 的实际屏幕位置，
     * 而不是 JWindow 的左上角。
     */
    public Point getLocation() {
        if (frame == null) return new Point();
        Point w = frame.getLocation();
        return new Point(w.x, w.y + jumpRoom());
    }

    /**
     * 移动桌宠到指定视觉位置。PetPanel 拖拽时调用。
     * 内部会把窗口定位到 (x, y - jumpRoom)，并同步更新悬浮框位置。
     */
    public void setLocation(int x, int y) {
        if (frame != null) frame.setLocation(x, y - jumpRoom());
        updateHoverLocation(x, y);
        updateBubbleLocation(x, y);
    }

    /** 气泡显示中时实时跟随宠物（拖拽/缩放都经过 setLocation，EDT 上调用）。 */
    private void updateBubbleLocation(int visualX, int visualY) {
        if (bubble == null || !bubble.isShowingNow()) return;
        int w = PetSettingsState.scaledWidth(currentSizePercent());
        int h = PetSettingsState.scaledHeight(currentSizePercent());
        bubble.follow(visualX, visualX + w, visualY, h);
    }

    private void updateHoverLocation(int visualX, int visualY) {
        if (hover == null) return;
        Dimension size = frame == null ? new Dimension() : frame.getSize();
        int centerX = visualX + size.width / 2;
        int bottomY = visualY + PetSettingsState.scaledHeight(currentSizePercent());
        hover.updateLocation(centerX, visualY, bottomY);
    }

    /**
     * 拖拽结束时由 PetPanel 调用：把当前视觉位置持久化。
     */
    public void savePosition() {
        if (frame == null || settings == null) return;
        Point p = getLocation();
        settings.setWindowLocation(p.x, p.y);
    }

    /**
     * 实时应用新的大小与不透明度（设置页 Apply / 实时预览时调用）。
     * 先记录预览缩放值（让绘制端立即按新尺寸画精灵），再同步窗口尺寸与透明度。
     * EDT 异步；桌宠未构建时仅记住预览值，等 buildFrame 再生效。
     */
    public void applySettings(int sizePercent, int opacityPercent) {
        int clamped = PetSettingsState.clampSizePercent(sizePercent);
        SwingUtilities.invokeLater(() -> {
            previewSizePercent = clamped;
            if (frame != null) {
                applySettingsInternal(clamped, opacityPercent);
            }
        });
    }

    /** EDT 内部：按给定缩放与透明度同步窗口尺寸、位置与不透明度。 */
    private void applySettingsInternal(int sizePercent, int opacityPercent) {
        Dimension size = PetPanel.preferredPetSize(sizePercent);
        Point visual = getLocation();
        frame.setSize(size.width, size.height);
        // 保持宠物视觉位置不变：窗口大小改变后，窗口左上角要重新计算
        setLocation(visual.x, visual.y);
        applyOpacity(opacityPercent);
    }

    /**
     * 显示台词气泡（EDT 异步）。桌宠未构建或文本为空时 no-op。
     * 气泡显示在宠物<b>左侧或右侧</b>（右侧优先、放不下换左侧），垂直与头部平行，
     * 与上方数值胶囊、下方档案卡片互不遮挡。
     */
    public void showBubble(@NotNull String text) {
        SwingUtilities.invokeLater(() -> {
            if (frame == null || text.isBlank()) return;
            if (bubble == null) bubble = new PetBubble();
            Point anchor = getLocation();
            int w = PetSettingsState.scaledWidth(currentSizePercent());
            int h = PetSettingsState.scaledHeight(currentSizePercent());
            bubble.showBeside(anchor.x, anchor.x + w, anchor.y, h, text);
        });
    }

    /** 鼠标停留宠物头部区域（顶边下方 25% 以上）：显示头顶数值胶囊（EDT 异步）。 */
    public void showStatsOverlay() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            if (hover == null) hover = new PetHoverPanel(this, service);
            Point anchor = getLocation();
            int h = PetSettingsState.scaledHeight(currentSizePercent());
            hover.showZone(true, anchor.x + frame.getSize().width / 2, anchor.y, anchor.y + h);
        });
    }

    /** 鼠标停留宠物脚部区域（底边上方 25% 以下）：显示脚底按钮卡片（EDT 异步）。 */
    public void showCardOverlay() {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            if (hover == null) hover = new PetHoverPanel(this, service);
            Point anchor = getLocation();
            int h = PetSettingsState.scaledHeight(currentSizePercent());
            hover.showZone(false, anchor.x + frame.getSize().width / 2, anchor.y, anchor.y + h);
        });
    }

    /** 隐藏悬停交互面板（鼠标离开宠物时由 PetPanel 调用）。 */
    public void hideHoverPanel() {
        SwingUtilities.invokeLater(() -> {
            if (hover != null) hover.onPetExited();
        });
    }

    /** 当前缩放百分比：设置页预览值优先，否则用持久化值；设置不可用时返回默认值。 */
    public int currentSizePercent() {
        Integer preview = previewSizePercent;
        if (preview != null) return preview;
        return settings == null ? PetSettingsState.DEFAULT_SIZE_PERCENT : settings.getSizePercent();
    }

    /**
     * 清除大小预览覆盖，恢复按持久化值渲染与窗口尺寸。
     * 设置页 Cancel / 关闭 / Apply 之后调用，保证持久化值重新成为唯一事实来源。
     */
    public void clearPreviewOverride() {
        SwingUtilities.invokeLater(() -> {
            previewSizePercent = null;
            if (frame != null && settings != null) {
                applySettingsInternal(settings.getSizePercent(), settings.getOpacityPercent());
            }
        });
    }

    /**
     * 切换主题并立即刷新宠物外观（设置页实时预览直通路径）。
     * 不依赖广播链：EDT 内先更新运行时主题，再让面板立刻换装重绘。
     */
    public void applyThemePreview(@NotNull com.dsh.petwhale.resource.PetTheme theme) {
        SwingUtilities.invokeLater(() -> {
            service.setTheme(theme);
            if (panel != null) {
                panel.refreshThemeResources();
            }
        });
    }

    /** 设置窗口整体不透明度；平台不支持时静默跳过（保持完全可见）。 */
    private void applyOpacity(int opacityPercent) {
        if (frame == null) return;
        try {
            float opacity = PetSettingsState.clampOpacityPercent(opacityPercent) / 100f;
            frame.setOpacity(opacity);
        } catch (Throwable ignored) {
        }
    }

    /** 构造主桌宠窗口（在 EDT 上调用）。 */
    private void buildFrame() {
        previewSizePercent = null; // 新建窗口一律以持久化值为准
        int sizePercent = settings == null
                ? PetSettingsState.DEFAULT_SIZE_PERCENT : settings.getSizePercent();
        int opacityPercent = settings == null
                ? PetSettingsState.DEFAULT_OPACITY_PERCENT : settings.getOpacityPercent();

        if (settings != null && !themeSynced) {
            // 仅首次构建时把持久化主题同步进运行时服务；之后的重建（隐藏/召唤切换）
            // 不再覆盖运行时主题，避免设置页已预览切换的主题被旧值顶回去
            service.setTheme(settings.theme());
            themeSynced = true;
        }

        frame = new JWindow();
        frame.setAlwaysOnTop(true);
        frame.setBackground(new Color(0, 0, 0, 0));

        Dimension size = PetPanel.preferredPetSize(sizePercent);
        frame.setSize(size.width, size.height);
        Point visual = savedOrDefaultLocation(sizePercent);
        frame.setLocation(visual.x, visual.y - jumpRoom(sizePercent));

        panel = new PetPanel(service, this);
        frame.setContentPane(panel);
        applyOpacity(opacityPercent);
        frame.setVisible(!startHidden());
        visible = true;
        if (startHidden()) {
            if (summon == null) summon = buildSummon();
            summon.setVisible(true);
        } else {
            String greet = PetDialogue.random("greet");
            if (greet != null) showBubble(greet);
        }
    }

    /** 是否"启动时收起"（EDT 读取，只影响 buildFrame 一次）。 */
    private boolean startHidden() {
        return settings != null && settings.isStartHidden();
    }

    /**
     * 构造"召唤鲸鱼娘"小按钮。位置：屏幕右下角（任务栏上方）。
     */
    private JWindow buildSummon() {
        JWindow window = new JWindow();
        JButton btn = new JButton("召唤鲸鱼娘");
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            window.dispose();
            summon = null;
            if (settings != null) settings.setStartHidden(false);
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

    /**
     * 恢复上次保存的视觉位置；没有保存记录（-1）或已保存位置被拔掉的显示器甩出屏幕外时，
     * 回落到右下角默认位置。
     */
    private Point savedOrDefaultLocation(int sizePercent) {
        if (settings != null && settings.getX() >= 0 && settings.getY() >= 0) {
            Point saved = new Point(settings.getX(), settings.getY());
            if (isOnScreen(saved, sizePercent)) return saved;
        }
        return defaultLocation(sizePercent);
    }

    /** 保存点至少要有 100×40 像素落在某块屏幕内，否则视为不可用。 */
    private boolean isOnScreen(Point p, int sizePercent) {
        try {
            int w = PetSettingsState.scaledWidth(sizePercent);
            int h = PetSettingsState.scaledHeight(sizePercent);
            int probeW = Math.min(w, 100);
            int probeH = Math.min(h, 40);
            for (java.awt.GraphicsDevice device : GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle bounds = device.getDefaultConfiguration().getBounds();
                if (p.x + probeW > bounds.x && p.x < bounds.x + bounds.width
                        && p.y + probeH > bounds.y && p.y < bounds.y + bounds.height) {
                    return true;
                }
            }
        } catch (Throwable t) {
            return true;
        }
        return false;
    }

    /**
     * 默认位置：屏幕右下角（排除任务栏区域）。
     */
    private Point defaultLocation(int sizePercent) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int w = PetSettingsState.scaledWidth(sizePercent);
        int h = PetSettingsState.scaledHeight(sizePercent);
        int x = bounds.x + bounds.width - w - 32;
        int y = bounds.y + bounds.height - h - 64;
        if (x < 0) x = Math.max(0, screen.width - w - 32);
        if (y < 0) y = Math.max(0, screen.height - h - 64);
        return new Point(x, y);
    }

    /** 当前跳跃缓冲高度（像素）。 */
    private int jumpRoom() {
        return jumpRoom(currentSizePercent());
    }

    private static int jumpRoom(int sizePercent) {
        return PetPanel.scaledJumpRoom(sizePercent);
    }
}
