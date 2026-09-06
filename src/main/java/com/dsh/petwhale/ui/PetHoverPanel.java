package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetDialogue;
import com.dsh.petwhale.state.PetActivityPhase;
import com.dsh.petwhale.state.PetSettingsState;
import com.dsh.petwhale.state.PetStateService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 鼠标悬停在桌宠上时浮现的交互面板。
 *
 * <p>参考设计：深色圆角卡片位于宠物正下方，显示名字、亲密度等级、小鱼干、点数，
 * 并提供"喂食 / 改名 / 隐藏"三个快捷按钮。面板与宠物本体是两个独立 {@link JWindow}，
 * 通过进入/离开事件配合延迟计时器实现平滑过渡：鼠标快速从宠物移向面板时不会闪烁关闭。</p>
 *
 * <p>所有 Swing 操作都限制在 EDT 内；面板创建与显示逻辑内部已自行切到 EDT，
 * 调用方无需提前切线程。</p>
 */
public final class PetHoverPanel {

    /** 面板与宠物底边的间隙（像素） */
    private static final int GAP = 3;
    /** 面板圆角半径 */
    private static final int CORNER = 10;
    /** 显示延迟：鼠标进入宠物后多少毫秒才显示（避免划过宠物时频繁闪现） */
    private static final int SHOW_DELAY_MS = 280;
    /** 隐藏延迟：鼠标离开宠物或面板后多少毫秒隐藏 */
    private static final int HIDE_DELAY_MS = 320;
    /** 面板最小宽度 */
    private static final int MIN_WIDTH = 100;
    /** 面板内边距 */
    private static final Insets PADDING = new Insets(7, 8, 7, 8);
    /** 按钮间距 */
    private static final int BUTTON_GAP = 4;

    private final PetFrame frame;
    private final PetStateService service;
    private final JWindow window;
    private final JLabel nameLabel;
    private final JLabel intimacyLabel;
    private final JLabel fishLabel;
    private final JLabel pointsLabel;
    private final HoverPanel panel;

    private final Timer showTimer;
    private final Timer hideTimer;

    /** 当前面板是否被显式锁定（鼠标在面板内） */
    private boolean locked;
    /** 当前面板锚点，供刷新内容时重新定位 */
    private int anchorCenterX;
    private int anchorPetBottomY;

    public PetHoverPanel(@NotNull PetFrame frame, @NotNull PetStateService service) {
        this.frame = frame;
        this.service = service;

        this.window = new JWindow();
        window.setAlwaysOnTop(true);
        window.setBackground(new Color(0, 0, 0, 0));

        this.nameLabel = new JLabel();
        this.intimacyLabel = new JLabel();
        this.fishLabel = new JLabel();
        this.pointsLabel = new JLabel();
        styleLabel(nameLabel, new Color(220, 225, 235), 11, Font.BOLD);
        styleLabel(intimacyLabel, new Color(160, 175, 200), 10, Font.PLAIN);
        styleLabel(fishLabel, new Color(120, 190, 255), 10, Font.BOLD);
        styleLabel(pointsLabel, new Color(255, 210, 120), 10, Font.BOLD);

        this.panel = new HoverPanel(buildContent());
        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                locked = true;
                cancelHide();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                locked = false;
                scheduleHide();
            }
        });
        window.setContentPane(panel);

        this.showTimer = new Timer(SHOW_DELAY_MS, e -> SwingUtilities.invokeLater(() -> {
            if (locked) {
                doShow();
            }
        }));
        showTimer.setRepeats(false);

        this.hideTimer = new Timer(HIDE_DELAY_MS, e -> SwingUtilities.invokeLater(() -> {
            if (!locked) {
                doHide();
            }
        }));
        hideTimer.setRepeats(false);
    }

    /**
     * 鼠标进入宠物区域时调用：启动显示延迟。
     *
     * @param centerX 宠物中心的屏幕 X
     * @param petBottomY 宠物底边的屏幕 Y
     */
    public void onPetEntered(int centerX, int petBottomY) {
        this.anchorCenterX = centerX;
        this.anchorPetBottomY = petBottomY;
        locked = true;
        cancelHide();
        refreshContent();
        if (!window.isVisible()) {
            showTimer.restart();
        } else {
            // 已经在显示时只要刷新定位即可
            doShow();
        }
    }

    /** 鼠标离开宠物区域时调用：启动隐藏延迟（若鼠标进了面板会自动取消）。 */
    public void onPetExited() {
        if (!locked) {
            scheduleHide();
        }
    }

    /**
     * 宠物窗口移动时调用：实时更新面板锚点，让悬浮框跟随宠物。
     *
     * @param centerX 宠物中心的屏幕 X
     * @param petBottomY 宠物底边的屏幕 Y
     */
    public void updateLocation(int centerX, int petBottomY) {
        this.anchorCenterX = centerX;
        this.anchorPetBottomY = petBottomY;
        if (window.isVisible()) {
            SwingUtilities.invokeLater(this::reposition);
        }
    }

    /** 外部强制隐藏（例如点击宠物触发交互时）。 */
    public void hideNow() {
        locked = false;
        cancelShow();
        doHide();
    }

    /** 释放窗口资源（PetFrame.dispose 时调用）。 */
    public void dispose() {
        cancelShow();
        cancelHide();
        SwingUtilities.invokeLater(window::dispose);
    }

    private void scheduleHide() {
        cancelShow();
        hideTimer.restart();
    }

    private void cancelHide() {
        hideTimer.stop();
    }

    private void cancelShow() {
        showTimer.stop();
    }

    private void doShow() {
        cancelShow();
        if (window.isVisible()) {
            reposition();
            return;
        }
        refreshContent();
        panel.invalidate();
        window.pack();
        reposition();
        window.setVisible(true);
    }

    private void doHide() {
        cancelHide();
        window.setVisible(false);
    }

    private void reposition() {
        Dimension size = window.getSize();
        int width = Math.max(size.width, MIN_WIDTH);
        int x = anchorCenterX - width / 2;
        int y = anchorPetBottomY + GAP;

        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        // 横向贴边保护
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - width));
        // 如果下方放不下，则改到宠物上方
        if (y + size.height > screen.y + screen.height) {
            // 这个分支理论上少见：宠物在屏幕上方时才触发
            y = anchorPetBottomY - size.height - GAP;
        }
        window.setSize(width, size.height);
        window.setLocation(x, y);
    }

    private void refreshContent() {
        PetSettingsState settings = loadSettings();
        if (settings == null) return;

        nameLabel.setText(settings.getPetName());
        intimacyLabel.setText("亲密度 " + PetSettingsState.intimacyTitle(settings.getIntimacy()));
        fishLabel.setText("小鱼干 x" + settings.getFishCount());
        pointsLabel.setText(settings.getPoints() + "点");
    }

    private JPanel buildContent() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(
                PADDING.top, PADDING.left, PADDING.bottom, PADDING.right));

        // 第一行：名字 + 亲密度
        JPanel topRow = new JPanel(new GridBagLayout());
        topRow.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.weightx = 0;
        topRow.add(nameLabel, gbc);
        gbc.insets = new Insets(0, 5, 0, 0);
        gbc.weightx = 1;
        topRow.add(intimacyLabel, gbc);
        root.add(topRow);

        // 第二行：小鱼干 + 点数
        JPanel statRow = new JPanel(new GridBagLayout());
        statRow.setOpaque(false);
        statRow.setBorder(BorderFactory.createEmptyBorder(3, 0, 5, 0));
        GridBagConstraints sg = new GridBagConstraints();
        sg.anchor = GridBagConstraints.WEST;
        sg.weightx = 0;
        statRow.add(fishLabel, sg);
        sg.insets = new Insets(0, 8, 0, 0);
        sg.weightx = 1;
        statRow.add(pointsLabel, sg);
        root.add(statRow);

        // 第三行：按钮
        JPanel buttonRow = new JPanel();
        buttonRow.setLayout(new BoxLayout(buttonRow, BoxLayout.X_AXIS));
        buttonRow.setOpaque(false);
        buttonRow.add(buildButton("喂食", this::onFeed));
        buttonRow.add(Box.createHorizontalStrut(BUTTON_GAP));
        buttonRow.add(buildButton("改名", this::onRename));
        buttonRow.add(Box.createHorizontalStrut(BUTTON_GAP));
        buttonRow.add(buildButton("隐藏", this::onHide));
        root.add(buttonRow);

        return root;
    }

    private JButton buildButton(String text, Runnable action) {
        RoundedButton btn = new RoundedButton(text);
        btn.setForeground(new Color(220, 225, 235));
        btn.setBackground(new Color(55, 75, 115));
        btn.setHoverBackground(new Color(75, 100, 150));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 10f));
        btn.addActionListener(e -> {
            action.run();
            // 按钮操作后通常要刷新面板内容
            refreshContent();
        });
        return btn;
    }

    private void onFeed() {
        PetSettingsState settings = loadSettings();
        if (settings == null) return;
        if (!settings.feedOne()) {
            frame.showBubble("小鱼干不够啦，下次再喂吧~");
            return;
        }
        service.setPhase(PetActivityPhase.DONE, null, PetDialogue.random("feed"));
        frame.showBubble(PetDialogue.random("feed"));
    }

    private void onRename() {
        PetSettingsState settings = loadSettings();
        if (settings == null) return;
        String current = settings.getPetName();
        String result = Messages.showInputDialog(
                "给鲸鱼娘起个新名字吧：",
                "改名",
                null,
                current,
                null);
        if (result != null) {
            settings.setPetName(result);
            refreshContent();
        }
    }

    private void onHide() {
        hideNow();
        frame.hide();
    }

    /**
     * 自绘圆角按钮：在透明/深色面板上保持一致的视觉风格。
     */
    private static final class RoundedButton extends JButton {
        private Color hoverBackground;
        private boolean hovering;

        RoundedButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
            setContentAreaFilled(false);
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    repaint();
                }
            });
        }

        void setHoverBackground(Color color) {
            this.hoverBackground = color;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = hovering && hoverBackground != null ? hoverBackground : getBackground();
                if (bg != null) {
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                }
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                String txt = getText();
                int tx = (getWidth() - fm.stringWidth(txt)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(txt, tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

    private static void styleLabel(JLabel label, Color color, int size, int style) {
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(style, (float) size));
    }

    private static PetSettingsState loadSettings() {
        try {
            if (ApplicationManager.getApplication() == null) return null;
            return ApplicationManager.getApplication().getService(PetSettingsState.class);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 带圆角深色背景的容器面板。每帧先用 Clear 复合模式清到透明，再画圆角矩形，
     * 避免透明窗体上的旧面板残影。
     */
    private static final class HoverPanel extends JPanel {
        HoverPanel(JPanel content) {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            add(content);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);

                // 深蓝灰圆角底板 + 细边框
                g2.setColor(new Color(28, 36, 56, 245));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);
                g2.setColor(new Color(70, 90, 135, 220));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);
            } finally {
                g2.dispose();
            }
        }
    }
}
