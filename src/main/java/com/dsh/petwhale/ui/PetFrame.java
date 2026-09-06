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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
 *   <li>右键唤起 {@link PetHoverPanel}（主题切换 + 隐藏）；单击/连点触发交互
 *       （由 {@link PetPanel} 处理）；台词气泡由 {@link PetBubble} 承载</li>
 *   <li>鼠标拖动由 {@link PetPanel} 的 MouseAdapter 处理，本类只暴露位置读写；
 *       拖拽结束 / 隐藏 / 销毁时把当前位置写回设置持久化</li>
 *   <li>"隐藏"动作收起桌宠 + 显示一个小的"召唤鲸鱼娘"召唤按钮；隐藏状态跨重启记忆；
 *       {@link #unhide()} 反向恢复（设置页"显示"按钮调用）</li>
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
    /** 悬停面板（懒加载） */
    private JWindow hover;
    /** 台词气泡（懒加载，单实例复用） */
    private PetBubble bubble;
    /** "召唤鲸鱼娘"按钮（懒加载，仅在 hide 后存在） */
    private JWindow summon;
    /** 桌宠当前是否可见（可见包括 summon 状态——召唤按钮也算"用户能看到"） */
    private boolean visible = true;
    /** 用户设置（应用级持久化）；构造时解析，null 仅出现在平台测试环境 */
    private final PetSettingsState settings;

    /**
     * 构造时仅持有 service 引用，不创建任何 Swing 组件。
     * 调用方应在合适时机（EDT 上）调 {@link #show()} 完成构建。
     */
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
            if (hover != null) hover.dispose();
            if (bubble != null) bubble.dispose();
            if (frame != null) frame.dispose();
            if (summon != null) summon.dispose();
            visible = false;
        });
    }

    /**
     * 隐藏桌宠（不销毁）：桌宠窗口和悬停面板消失，右下角留下"召唤鲸鱼娘"按钮。
     * 隐藏状态写入设置持久化，下次启动直接收起。
     */
    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (settings != null) settings.setStartHidden(true);
            if (frame != null) frame.setVisible(false);
            if (hover != null) hover.setVisible(false);
            if (bubble != null) bubble.hideNow();
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
     * 取桌宠左上角坐标（屏幕坐标系）。PetPanel 拖拽时用此值算偏移。
     * 若桌宠未构建，返回 (0,0)。
     */
    public java.awt.Point getLocation() {
        return frame == null ? new java.awt.Point() : frame.getLocation();
    }

    /**
     * 移动桌宠。PetPanel 拖拽时调用。
     * 若桌宠未构建，本调用为 no-op。
     */
    public void setLocation(int x, int y) {
        if (frame != null) frame.setLocation(x, y);
    }

    /**
     * 拖拽结束时由 PetPanel 调用：把当前位置持久化。
     * 线程安全：内部直接写设置对象（POJO 字段写入），无需切 EDT。
     */
    public void savePosition() {
        if (frame == null || settings == null) return;
        Point p = frame.getLocation();
        settings.setWindowLocation(p.x, p.y);
    }

    /**
     * 实时应用新的大小与不透明度（设置页 Apply 时调用）。
     * EDT 异步；桌宠未构建时先记住，等 buildFrame 再生效。
     *
     * @param sizePercent 缩放百分比（未经 clamp 的原始值，内部统一走 PetSettingsState 的 clamp）
     * @param opacityPercent 不透明度百分比
     */
    public void applySettings(int sizePercent, int opacityPercent) {
        SwingUtilities.invokeLater(() -> {
            if (frame == null) return;
            int w = PetSettingsState.scaledWidth(sizePercent);
            int h = PetSettingsState.scaledHeight(sizePercent);
            frame.setSize(w, h);
            applyOpacity(opacityPercent);
        });
    }

    /**
     * 显示台词气泡（EDT 异步）。桌宠未构建或文本为空时 no-op。
     * 气泡定位在桌宠正上方（不遮挡本体），2.5 秒自动消失。
     */
    public void showBubble(@NotNull String text) {
        SwingUtilities.invokeLater(() -> {
            if (frame == null || text.isBlank()) return;
            if (bubble == null) bubble = new PetBubble();
            Dimension pet = frame.getSize();
            Point anchor = frame.getLocation();
            bubble.showAbove(anchor.x + pet.width / 2, anchor.y, text);
        });
    }

    /** 设置窗口整体不透明度；平台不支持时静默跳过（保持完全可见）。 */
    private void applyOpacity(int opacityPercent) {
        if (frame == null) return;
        try {
            float opacity = PetSettingsState.clampOpacityPercent(opacityPercent) / 100f;
            frame.setOpacity(opacity);
        } catch (Throwable ignored) {
            // 某些窗口系统不支持 uniform translucency，忽略即可
        }
    }

    /**
     * 显示悬停面板（懒加载：首次调用时构造）。
     * 位置：桌宠窗口内部坐标 + 偏移（避免遮挡桌宠本身）。
     */
    public void showHoverPanel(int localX, int localY) {
        if (frame == null) return;
        if (hover == null) {
            hover = new JWindow(frame);
            hover.setContentPane(new PetHoverPanel(this, service));
        }
        Point origin = frame.getLocation();
        // 屏幕坐标 = 窗口左上角 + 局部鼠标坐标 - 居中偏移
        hover.setLocation(origin.x + localX - 30, origin.y + localY + 12);
        hover.pack();
        hover.setVisible(true);
    }

    /** 构造主桌宠窗口（在 EDT 上调用）。 */
    private void buildFrame() {
        int sizePercent = settings == null
                ? PetSettingsState.DEFAULT_SIZE_PERCENT : settings.getSizePercent();
        int opacityPercent = settings == null
                ? PetSettingsState.DEFAULT_OPACITY_PERCENT : settings.getOpacityPercent();

        // 主题先于面板构建恢复，PetPanel 构造时就能拿到正确的精灵图
        if (settings != null) service.setTheme(settings.theme());

        frame = new JWindow();
        frame.setAlwaysOnTop(true);
        // 全透明背景：让桌面背景透出来
        frame.setBackground(new Color(0, 0, 0, 0));
        int w = PetSettingsState.scaledWidth(sizePercent);
        int h = PetSettingsState.scaledHeight(sizePercent);
        frame.setSize(w, h);
        frame.setLocation(savedOrDefaultLocation(w, h));
        frame.setContentPane(new PetPanel(service, this));
        applyOpacity(opacityPercent);
        // 监听鼠标右键唤起悬停面板
        frame.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showHoverPanel(e.getX(), e.getY());
            }
        });
        frame.setVisible(!startHidden());
        visible = true;
        if (startHidden()) {
            // 启动即收起：只显示召唤按钮
            if (summon == null) summon = buildSummon();
            summon.setVisible(true);
        } else {
            // 开机问候（气泡；台词库缺失时静默跳过）
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
     * 点击后销毁按钮、复原 PetFrame，并把"启动时收起"重置为 false。
     */
    private JWindow buildSummon() {
        JWindow window = new JWindow();
        JButton btn = new JButton("召唤鲸鱼娘");
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            // 销毁召唤按钮
            window.dispose();
            summon = null;
            // 用户主动召唤 = 不再需要"启动时收起"
            if (settings != null) settings.setStartHidden(false);
            // 复原桌宠本体
            if (frame != null) {
                frame.setVisible(true);
                return;
            }
            // 万一之前 dispose 过，重新构造
            buildFrame();
        }));
        window.getContentPane().add(btn);
        window.pack();
        // 任务栏上方 24px 边距
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        window.setLocation(
                screen.x + screen.width - window.getWidth() - 24,
                screen.y + screen.height - window.getHeight() - 24);
        return window;
    }

    /**
     * 恢复上次保存的位置；没有保存记录（-1）或已保存位置被拔掉的显示器甩出屏幕外时，
     * 回落到右下角默认位置。
     */
    private Point savedOrDefaultLocation(int width, int height) {
        if (settings != null && settings.getX() >= 0 && settings.getY() >= 0) {
            Point saved = new Point(settings.getX(), settings.getY());
            if (isOnScreen(saved, width, height)) return saved;
        }
        return defaultLocation(width, height);
    }

    /** 保存点至少要有 100×40 像素落在某块屏幕内，否则视为不可用。 */
    private static boolean isOnScreen(Point p, int width, int height) {
        try {
            for (java.awt.GraphicsDevice device : GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getScreenDevices()) {
                Rectangle bounds = device.getDefaultConfiguration().getBounds();
                int probeW = Math.min(width, 100);
                int probeH = Math.min(height, 40);
                if (p.x + probeW > bounds.x && p.x < bounds.x + bounds.width
                        && p.y + probeH > bounds.y && p.y < bounds.y + bounds.height) {
                    return true;
                }
            }
        } catch (Throwable t) {
            // 屏幕枚举失败时宁可信其有，避免误回默认位置
            return true;
        }
        return false;
    }

    /**
     * 默认位置：屏幕右下角（排除任务栏区域）。
     * 使用 {@code getMaximumWindowBounds} 而不是 {@code getScreenSize} 可以在多显示器
     * + 任务栏场景下保证桌宠不躲在任务栏后面。
     */
    private Point defaultLocation(int width, int height) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int x = bounds.x + bounds.width - width - 32;
        int y = bounds.y + bounds.height - height - 64;
        // 兜底：若 bounds 计算出错（极小屏幕），保证不越界
        if (x < 0) x = Math.max(0, screen.width - width - 32);
        if (y < 0) y = Math.max(0, screen.height - height - 64);
        return new Point(x, y);
    }
}