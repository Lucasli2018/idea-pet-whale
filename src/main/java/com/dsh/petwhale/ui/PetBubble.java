package com.dsh.petwhale.ui;

import javax.swing.JWindow;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;

/**
 * 台词气泡：跟随桌宠的圆角小提示窗。
 *
 * <p>设计纪律（UI 三不原则）：
 * <ul>
 *   <li><b>不遮挡动画本体</b>——气泡永远定位在桌宠窗口正上方 8px 处，横向居中；
 *       顶部越界时回落到桌宠下方</li>
 *   <li><b>自动消失</b>——显示 2.5 秒后自动隐藏（单发 Timer，反复触发只重排一次）</li>
 *   <li><b>不进任务栏</b>——和桌宠本体一样用 {@link JWindow}（无边框无图标）</li>
 * </ul>
 *
 * <p>单实例复用：桌宠同时只需要一个气泡，反复 {@link #showAbove} 只是改文本 + 重新定位，
 * 避免频繁建窗（Windows 上每次建透明窗都有肉眼可见的开销）。</p>
 */
public final class PetBubble {

    /** 气泡显示时长（毫秒） */
    static final int AUTO_HIDE_MS = 2500;
    /** 气泡距桌宠窗口的垂直间隙（像素） */
    private static final int GAP = 8;
    /** 文本内边距（像素） */
    private static final int PADDING_X = 10;
    private static final int PADDING_Y = 6;
    /** 气泡最大文本宽度（超长换行会撑爆屏，直接截断到约 24 个字符后加省略号） */
    static final int MAX_TEXT_CHARS = 24;

    private final JWindow window;
    private final JLabel label;
    private final BubblePanel panel;
    /** 自动隐藏定时器（单发；每次 show 重启） */
    private final Timer autoHide;
    /** 最近一次显示时的中心 X（悬浮层出现/消失时重新定位用） */
    private int lastCenterX = 0;

    public PetBubble() {
        window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setBackground(new Color(0, 0, 0, 0));
        label = new JLabel();
        label.setForeground(new Color(38, 42, 50));
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        panel = new BubblePanel(label);
        window.setContentPane(panel);
        autoHide = new Timer(AUTO_HIDE_MS, e -> window.setVisible(false));
        autoHide.setRepeats(false);
    }

    /**
     * 在锚点上方显示气泡（必须在 EDT 上调用）。
     *
     * @param centerX 桌宠窗口中心的屏幕 X
     * @param topY 锚点顶边的屏幕 Y（悬浮层可见时传悬浮层顶边，保证不被遮挡）
     * @param text 台词文本
     */
    public void showAbove(int centerX, int topY, String text) {
        label.setText(clip(text));
        panel.invalidate();
        window.pack();
        lastCenterX = centerX;
        place(centerX, topY);
        window.setVisible(true);
        autoHide.restart();
    }

    /**
     * 把已显示的气泡重新定位到给定锚点上方（悬浮层出现/消失时调用，双向避免遮挡）。
     * 气泡未显示时 no-op。
     *
     * @param topY 新的锚点顶边屏幕 Y
     */
    public void repositionAbove(int topY) {
        if (!window.isVisible()) return;
        place(lastCenterX, topY);
    }

    /** 实际定位：气泡底边在 topY - GAP 之上，横向以 centerX 居中并夹回屏幕内。 */
    private void place(int centerX, int topY) {
        Dimension size = window.getSize();
        int x = centerX - size.width / 2;
        int y = topY - size.height - GAP;
        // 顶部放不下 → 回落到锚点下方（仍不遮挡：锚点顶边 + 间隙）
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        if (y < screen.y) {
            y = topY + GAP;
        }
        // 横向夹在屏幕内
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - size.width));
        window.setLocation(x, y);
    }

    /** 立即隐藏（hide/dispose 时同步收起，避免气泡孤零零挂在屏幕上）。 */
    public void hideNow() {
        autoHide.stop();
        window.setVisible(false);
    }

    /** 释放窗口资源（PetFrame.dispose 时调用）。 */
    public void dispose() {
        autoHide.stop();
        window.dispose();
    }

    /** 超长文本截断（中文按字符数粗略控制，超出补省略号）。 */
    static String clip(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        return trimmed.length() <= MAX_TEXT_CHARS
                ? trimmed
                : trimmed.substring(0, MAX_TEXT_CHARS) + "…";
    }

    /**
     * 圆角气泡底板：每帧用 {@link AlphaComposite#Clear} 清到全透明后画圆角，
     * 保证透明窗体上不残留上一条台词的旧圆角（重影）。
     */
    private static final class BubblePanel extends JPanel {
        private final JLabel label;

        private BubblePanel(JLabel label) {
            this.label = label;
            setOpaque(false);
            add(label);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(
                    PADDING_Y, PADDING_X, PADDING_Y, PADDING_X));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setComposite(AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(AlphaComposite.SrcOver);
                g2.setColor(new Color(250, 250, 252, 242));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(new Color(15, 110, 86));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension labelSize = label.getPreferredSize();
            return new Dimension(
                    labelSize.width + PADDING_X * 2,
                    labelSize.height + PADDING_Y * 2);
        }
    }
}