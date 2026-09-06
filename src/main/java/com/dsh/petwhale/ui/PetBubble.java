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
 *   <li><b>不遮挡动画本体与悬浮层</b>——气泡显示在桌宠<b>左侧或右侧</b>（右侧优先，
 *       右边放不下换左边），垂直方向与宠物<b>头部平行</b>（顶部下方约 1/5 处居中）；
 *       上方是数值胶囊、下方是档案卡片，互不干扰</li>
 *   <li><b>自动消失</b>——显示 2.5 秒后自动隐藏（单发 Timer，反复触发只重排一次）</li>
 *   <li><b>不进任务栏</b>——和桌宠本体一样用 {@link JWindow}（无边框无图标）</li>
 * </ul>
 *
 * <p>单实例复用：桌宠同时只需要一个气泡，反复 {@link #showBeside} 只是改文本 + 重新定位，
 * 避免频繁建窗（Windows 上每次建透明窗都有肉眼可见的开销）。</p>
 */
public final class PetBubble {

    /** 气泡显示时长（毫秒） */
    static final int AUTO_HIDE_MS = 2500;
    /** 气泡与宠物侧边的水平间隙（像素） */
    private static final int GAP_X = 8;
    /** 气泡垂直位置相对宠物高度的比例（头部平行） */
    private static final float HEAD_RATIO = 0.20f;
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
     * 在宠物侧面显示气泡（必须在 EDT 上调用）。
     *
     * @param petLeftX 宠物视觉左边缘的屏幕 X
     * @param petRightX 宠物视觉右边缘的屏幕 X
     * @param petTopY 宠物视觉顶边的屏幕 Y
     * @param spriteHeight 宠物视觉高度（像素），用于计算头部位置
     * @param text 台词文本
     */
    public void showBeside(int petLeftX, int petRightX, int petTopY, int spriteHeight, String text) {
        label.setText(clip(text));
        panel.invalidate();
        window.pack();
        place(petLeftX, petRightX, petTopY, spriteHeight);
        window.setVisible(true);
        autoHide.restart();
    }

    /** 实际定位：右侧优先，放不下换左侧；垂直与宠物头部平行（顶边 + 20% 高度居中）。 */
    private void place(int petLeftX, int petRightX, int petTopY, int spriteHeight) {
        Dimension size = window.getSize();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        // 水平：右侧优先，放不下换左侧，再不行夹回屏幕
        int x = petRightX + GAP_X;
        if (x + size.width > screen.x + screen.width) {
            x = petLeftX - GAP_X - size.width;
        }
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - size.width));

        // 垂直：与头部平行（宠物顶边 + 20% 高度处气泡垂直居中），夹回屏幕
        int headY = petTopY + Math.round(spriteHeight * HEAD_RATIO);
        int y = headY - size.height / 2;
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - size.height));

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