package com.dsh.petwhale.ui;

import javax.swing.JWindow;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;

/**
 * 台词气泡：跟随桌宠的圆角小提示窗（带指向宠物的尾巴 + 渐变底色 + 淡入下滑动画）。
 *
 * <p>设计纪律（UI 三不原则）：
 * <ul>
 *   <li><b>不遮挡动画本体与悬浮层</b>——气泡显示在桌宠<b>左侧或右侧</b>（右侧优先，
 *       右边放不下换左边），垂直方向与宠物<b>头部平行</b>（顶部下方约 1/5 处居中）；
 *       上方是数值胶囊、下方是档案卡片，互不干扰</li>
 *   <li><b>自动消失</b>——显示 2.5 秒后淡出隐藏（单发 Timer；淡入淡出均为
 *       窗口透明度动画 + 轻微位移，Windows 上不重建窗口、无额外开销）</li>
 *   <li><b>不进任务栏</b>——和桌宠本体一样用 {@link JWindow}（无边框无图标）</li>
 * </ul>
 *
 * <p>视觉设计：白 → 浅蓝渐变底 + 天蓝描边 + 指向宠物的小三角尾巴，
 * 粗体深蓝文字，配合淡入 + 下沉回弹的出现动画，比原来一块"白板"更生动。</p>
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
    private static final int PADDING_X = 12;
    private static final int PADDING_Y = 8;
    /** 指向宠物的尾巴宽度 / 高度的一半（像素） */
    private static final int TAIL_W = 9;
    private static final int TAIL_H = 6;
    /** 主体圆角半径 */
    private static final int CORNER = 16;
    /** 气泡最大文本宽度（超长换行会撑爆屏，直接截断到约 24 个字符后加省略号） */
    static final int MAX_TEXT_CHARS = 24;

    // === 鲸鱼娘蓝白系配色 ===
    private static final Color BG_TOP = new Color(255, 255, 255, 246);
    private static final Color BG_BOTTOM = new Color(219, 240, 255, 246);
    private static final Color BORDER = new Color(112, 186, 255);
    private static final Color TEXT = new Color(35, 52, 84);

    /** 淡入/淡出每步间隔（毫秒）与每步透明度增量 */
    private static final int FADE_STEP_MS = 26;
    private static final float FADE_IN_STEP = 0.22f;
    private static final float FADE_OUT_STEP = 0.25f;
    /** 淡入时气泡从下方多少像素滑入 */
    private static final int SLIDE_IN_PX = 6;

    private final JWindow window;
    private final JLabel label;
    private final BubblePanel panel;
    /** 自动隐藏定时器（单发；每次 show 重启，到点触发淡出） */
    private final Timer autoHide;
    /** 淡入/淡出动画定时器 */
    private final Timer fade;
    /** 当前透明度（动画中间值） */
    private float opacity;
    /** true=正在淡出，false=正在淡入 */
    private boolean fadingOut;
    /** 淡入结束后的最终位置（动画期间从下方 SLIDE_IN_PX 处滑上来） */
    private int restX;
    private int restY;
    /** 透明度动画兜底：平台不支持 setOpacity 时直接跳过动画 */
    private boolean opacitySupported = true;

    public PetBubble() {
        window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setBackground(new Color(0, 0, 0, 0));
        label = new JLabel();
        label.setForeground(TEXT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        panel = new BubblePanel(label);
        window.setContentPane(panel);
        autoHide = new Timer(AUTO_HIDE_MS, e -> startFade(false));
        autoHide.setRepeats(false);
        fade = new Timer(FADE_STEP_MS, e -> stepFade());
        fade.setRepeats(false);
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
        // 尾巴默认朝左（气泡在宠物右侧）；布局后若实际贴在左侧则翻转一次重新打包
        panel.setTail(true);
        panel.invalidate();
        window.pack();
        boolean tailLeft = place(petLeftX, petRightX, petTopY, spriteHeight);
        if (!tailLeft) {
            panel.setTail(false);
            panel.invalidate();
            window.pack();
            place(petLeftX, petRightX, petTopY, spriteHeight);
        }

        // 淡入动画起点：完全透明 + 下沉几像素，随后滑到位
        autoHide.stop();
        startFade(true);
        window.setVisible(true);
        autoHide.restart();
    }

    /**
     * 实际定位：右侧优先，放不下换左侧；垂直与宠物头部平行（顶边 + 20% 高度居中）。
     * 记录最终位置到 {@code restX/restY}，并立即按当前动画进度应用。
     *
     * @return true=尾巴应画在气泡左侧（气泡在宠物右侧），false=尾巴在右侧
     */
    private boolean place(int petLeftX, int petRightX, int petTopY, int spriteHeight) {
        Dimension size = window.getSize();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        // 水平：右侧优先，放不下换左侧，再不行夹回屏幕
        int x = petRightX + GAP_X;
        boolean tailLeft = true;
        if (x + size.width > screen.x + screen.width) {
            x = petLeftX - GAP_X - size.width;
            tailLeft = false;
        }
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - size.width));

        // 垂直：与头部平行（宠物顶边 + 20% 高度处气泡垂直居中），夹回屏幕
        int headY = petTopY + Math.round(spriteHeight * HEAD_RATIO);
        int y = headY - size.height / 2;
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - size.height));

        restX = x;
        restY = y;
        applyAnimationPosition();
        return tailLeft;
    }

    /** 启动淡入（in=true）或淡出（in=false）动画。 */
    private void startFade(boolean in) {
        fade.stop();
        fadingOut = !in;
        if (in) {
            if (!opacitySupported) {
                return; // 平台不支持透明度：直接整窗显示，无动画
            }
            opacity = 0f;
            applyOpacitySafely(0f);
            applyAnimationPosition();
        }
        fade.start();
    }

    /** 淡入/淡出的单步推进。 */
    private void stepFade() {
        if (fadingOut) {
            opacity -= FADE_OUT_STEP;
            if (opacity <= 0f) {
                opacity = 0f;
                fade.stop();
                applyOpacitySafely(0f);
                window.setVisible(false);
                return;
            }
        } else {
            opacity = Math.min(1f, opacity + FADE_IN_STEP);
            if (opacity >= 1f) {
                fade.stop();
            }
        }
        applyOpacitySafely(opacity);
        applyAnimationPosition();
        if (!fade.isRunning()) {
            // 动画收尾：恢复满透明度与最终位置
            applyOpacitySafely(1f);
            window.setLocation(restX, restY);
        } else {
            fade.restart();
        }
    }

    /** 按当前透明度应用淡入下滑偏移：越透明位置越靠下，营造"浮上来"的动感。 */
    private void applyAnimationPosition() {
        if (fadingOut || opacity >= 1f) {
            window.setLocation(restX, restY);
            return;
        }
        float progress = Math.max(0f, Math.min(1f, opacity));
        window.setLocation(restX, restY + Math.round((1f - progress) * SLIDE_IN_PX));
    }

    /** 平台不支持窗口透明度时静默降级（只保留位移动画）。 */
    private void applyOpacitySafely(float value) {
        if (!opacitySupported) return;
        try {
            window.setOpacity(value);
        } catch (Throwable t) {
            opacitySupported = false;
            try {
                window.setOpacity(1f);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 立即隐藏（hide/dispose 时同步收起，避免气泡孤零零挂在屏幕上）。 */
    public void hideNow() {
        autoHide.stop();
        fade.stop();
        applyOpacitySafely(1f);
        opacity = 1f;
        fadingOut = false;
        window.setVisible(false);
    }

    /** 释放窗口资源（PetFrame.dispose 时调用）。 */
    public void dispose() {
        autoHide.stop();
        fade.stop();
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
     * 渐变圆角气泡底板（带指向宠物的三角尾巴）：每帧用 {@link AlphaComposite#Clear}
     * 清到全透明后重画，保证透明窗体上不残留上一条台词的旧圆角（重影）。
     */
    private static final class BubblePanel extends JPanel {
        private final JLabel label;
        /** 尾巴在哪一侧：true=左侧（气泡在宠物右边），false=右侧 */
        private boolean tailLeft = true;

        private BubblePanel(JLabel label) {
            this.label = label;
            setOpaque(false);
            add(label);
            updateBorder();
        }

        /** 设置尾巴朝向并同步内边距（尾巴一侧多留 TAIL_W 空间）。 */
        void setTail(boolean left) {
            this.tailLeft = left;
            updateBorder();
        }

        private void updateBorder() {
            int left = PADDING_X + (tailLeft ? TAIL_W : 0);
            int right = PADDING_X + (tailLeft ? 0 : TAIL_W);
            setBorder(javax.swing.BorderFactory.createEmptyBorder(PADDING_Y, left, PADDING_Y, right));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(AlphaComposite.SrcOver);

                int w = getWidth();
                int h = getHeight();
                int bodyX = tailLeft ? TAIL_W : 0;
                int bodyW = w - 1 - TAIL_W;

                // 主体：白 → 浅蓝垂直渐变圆角矩形
                GradientPaint gradient = new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM);
                g2.setPaint(gradient);
                g2.fillRoundRect(bodyX, 0, bodyW, h - 1, CORNER, CORNER);
                g2.setColor(BORDER);
                g2.drawRoundRect(bodyX, 0, bodyW, h - 1, CORNER, CORNER);

                // 尾巴：指向宠物的实心小三角 + 两条描边线
                int midY = h / 2;
                int baseX = tailLeft ? bodyX : bodyX + bodyW;
                int tipX = tailLeft ? 0 : w - 1;
                g2.setPaint(gradient);
                g2.fillPolygon(new Polygon(
                        new int[]{baseX, baseX, tipX},
                        new int[]{midY - TAIL_H, midY + TAIL_H, midY}, 3));
                g2.setColor(BORDER);
                g2.drawLine(tipX, midY, baseX, midY - TAIL_H);
                g2.drawLine(tipX, midY, baseX, midY + TAIL_H);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension labelSize = label.getPreferredSize();
            java.awt.Insets insets = getInsets();
            return new Dimension(
                    labelSize.width + insets.left + insets.right,
                    labelSize.height + insets.top + insets.bottom);
        }
    }
}
