package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetManifest;
import com.dsh.petwhale.state.PetStateService;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
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
 * 全局透明桌宠窗口。
 *
 * <p>关键设计：
 * <ul>
 *   <li>{@link JFrame} 设置 {@code undecorated=true} + {@code always-on-top=true}
 *       —— 没有任何窗口装饰，永远置顶，浮在所有编辑器、工具窗口、弹窗之上</li>
 *   <li>背景色用 {@code (0,0,0,0)} 完全透明 —— 桌面背景可以透出来</li>
 *   <li>初始位置在屏幕右下角（用 {@code getMaximumWindowBounds} 排除任务栏）</li>
 *   <li>右键唤起 {@link PetHoverPanel}（主题切换 + 隐藏）</li>
 *   <li>鼠标拖动由 {@link PetPanel} 的 MouseAdapter 处理，本类只暴露位置读写</li>
 *   <li>"隐藏"动作销毁桌宠 + 显示一个小的"召唤鲸鱼娘"召唤按钮</li>
 * </ul>
 *
 * <p>所有 Swing 操作都通过 {@link SwingUtilities#invokeLater} 切到 EDT。
 * 本类是被 {@link com.dsh.petwhale.startup.PetStartupActivity} 在任意线程上调用的，
 * 不假设调用方在 EDT。</p>
 */
public final class PetFrame {

    private final PetStateService service;
    /** 主桌宠窗口 */
    private JFrame frame;
    /** 悬停面板（懒加载） */
    private JWindow hover;
    /** "召唤鲸鱼娘"按钮（懒加载，仅在 hide 后存在） */
    private JWindow summon;
    /** 桌宠当前是否可见（可见包括 summon 状态——召唤按钮也算"用户能看到"） */
    private boolean visible = true;

    /**
     * 构造时仅持有 service 引用，不创建任何 Swing 组件。
     * 调用方应在合适时机（EDT 上）调 {@link #show()} 完成构建。
     */
    public PetFrame(@NotNull PetStateService service) {
        this.service = service;
    }

    /** 显示桌宠窗口（EDT 异步）。 */
    public void show() {
        SwingUtilities.invokeLater(this::buildFrame);
    }

    /** 完全销毁所有窗口（EDT 异步）。不可恢复，需要重新再 {@link #show}。 */
    public void dispose() {
        SwingUtilities.invokeLater(() -> {
            if (hover != null) hover.dispose();
            if (frame != null) frame.dispose();
            if (summon != null) summon.dispose();
            visible = false;
        });
    }

    /**
     * 隐藏桌宠（不销毁）：桌宠窗口和悬停面板消失，右下角留下"召唤鲸鱼娘"按钮。
     */
    public void hide() {
        SwingUtilities.invokeLater(() -> {
            if (frame != null) frame.setVisible(false);
            if (hover != null) hover.setVisible(false);
            if (summon == null) summon = buildSummon();
            summon.setVisible(true);
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
        frame = new JFrame("Idea Pet Whale");
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        // 全透明背景：让桌面背景透出来
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setSize(PetManifest.CELL_WIDTH, PetManifest.CELL_HEIGHT);
        frame.setLocation(defaultLocation());
        frame.setContentPane(new PetPanel(service, this));
        frame.setIconImage(makeIcon());
        // 监听鼠标右键唤起悬停面板
        frame.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showHoverPanel(e.getX(), e.getY());
            }
        });
        frame.setVisible(true);
        visible = true;
    }

    /**
     * 构造"召唤鲸鱼娘"小按钮。位置：屏幕右下角（任务栏上方）。
     * 点击后销毁按钮、复原 PetFrame。
     */
    private JWindow buildSummon() {
        JWindow window = new JWindow();
        JButton btn = new JButton("召唤鲸鱼娘");
        btn.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        btn.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            // 销毁召唤按钮
            window.dispose();
            summon = null;
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
     * 默认位置：屏幕右下角（排除任务栏区域）。
     * 使用 {@code getMaximumWindowBounds} 而不是 {@code getScreenSize} 可以在多显示器
     * + 任务栏场景下保证桌宠不躲在任务栏后面。
     */
    private Point defaultLocation() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        int x = bounds.x + bounds.width - PetManifest.CELL_WIDTH - 32;
        int y = bounds.y + bounds.height - PetManifest.CELL_HEIGHT - 64;
        // 兜底：若 bounds 计算出错（极小屏幕），保证不越界
        if (x < 0) x = Math.max(0, screen.width - PetManifest.CELL_WIDTH - 32);
        if (y < 0) y = Math.max(0, screen.height - PetManifest.CELL_HEIGHT - 64);
        return new Point(x, y);
    }

    /**
     * 占位图标（16×16 全透明）—— 任务栏看不出区别，但避免某些环境下 NullPointerException。
     */
    private java.awt.Image makeIcon() {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        return img;
    }
}