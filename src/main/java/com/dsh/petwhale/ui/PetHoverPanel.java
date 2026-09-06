package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetDialogue;
import com.dsh.petwhale.state.PetActivityPhase;
import com.dsh.petwhale.state.PetSettingsState;
import com.dsh.petwhale.state.PetStateService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 鼠标悬停在桌宠上时浮现的交互悬浮层（参考双条式设计，拆成两个独立窗口）：
 *
 * <ul>
 *   <li><b>数值胶囊</b>（宠物上方）：亮色圆角胶囊条，显示 亲密度 · 小鱼干 · 点数 · 等级；
 *       鼠标移动到宠物上方区域（悬停宠物）时浮现。</li>
 *   <li><b>按钮条</b>（宠物下方）：亮色圆角条，一排快捷按钮（喂食 / 改名 / 设置 / 隐藏）。</li>
 * </ul>
 *
 * <p>两条与宠物本体是三个独立 {@link JWindow}，共享一套进入/离开延迟计时器：
 * 鼠标在宠物 ↔ 数值胶囊 ↔ 按钮条 之间移动时保持显示，全部离开后才延迟隐藏。</p>
 *
 * <p>所有 Swing 操作都限制在 EDT 内；调用方无需提前切线程。</p>
 */
public final class PetHoverPanel {

    /** 悬浮层与宠物边缘的间隙（像素） */
    private static final int GAP = 4;
    /** 胶囊/按钮条圆角半径 */
    private static final int CORNER = 12;
    /** 显示延迟：鼠标进入宠物后多少毫秒才显示（避免划过宠物时频繁闪现） */
    private static final int SHOW_DELAY_MS = 280;
    /** 隐藏延迟：鼠标离开所有区域后多少毫秒隐藏 */
    private static final int HIDE_DELAY_MS = 320;
    /** 数值胶囊内边距 */
    private static final Insets STATS_PADDING = new Insets(4, 12, 4, 12);
    /** 按钮条内边距 */
    private static final Insets BAR_PADDING = new Insets(4, 8, 4, 8);
    /** 按钮间距 */
    private static final int BUTTON_GAP = 2;

    // 亮色胶囊配色（浮在桌面上，与 IDE 主题无关，固定浅色系保证可读）
    private static final Color PILL_BG = new Color(255, 255, 255, 242);
    private static final Color PILL_BORDER = new Color(205, 208, 216, 255);
    private static final Color TEXT_MAIN = new Color(70, 74, 84);
    private static final Color TEXT_ACCENT = new Color(214, 106, 66);
    private static final Color TEXT_FISH = new Color(38, 110, 190);
    private static final Color TEXT_LEVEL = new Color(20, 140, 90);
    private static final Color BTN_HOVER = new Color(235, 239, 246);

    private final PetFrame frame;
    private final PetStateService service;
    /** 数值胶囊窗口（宠物上方） */
    private final JWindow statsWindow;
    /** 快捷按钮条窗口（宠物下方） */
    private final JWindow actionBarWindow;
    private final JLabel statsLabel;

    private final Timer showTimer;
    private final Timer hideTimer;

    /** 当前面板是否被显式锁定（鼠标在宠物或任一悬浮层内） */
    private boolean locked;
    /** 宠物锚点：中心 X / 顶边 Y / 底边 Y（屏幕坐标），供刷新内容时重新定位 */
    private int anchorCenterX;
    private int anchorPetTopY;
    private int anchorPetBottomY;

    public PetHoverPanel(@NotNull PetFrame frame, @NotNull PetStateService service) {
        this.frame = frame;
        this.service = service;

        // === 数值胶囊（宠物上方） ===
        this.statsLabel = new JLabel();
        statsLabel.setForeground(TEXT_MAIN);
        statsLabel.setFont(statsLabel.getFont().deriveFont(Font.BOLD, 11f));
        JPanel statsPanel = new PillPanel(STATS_PADDING);
        statsPanel.add(statsLabel);
        this.statsWindow = new JWindow();
        statsWindow.setAlwaysOnTop(true);
        statsWindow.setBackground(new Color(0, 0, 0, 0));
        statsWindow.setContentPane(statsPanel);
        statsPanel.addMouseListener(pillHoverListener());

        // === 快捷按钮条（宠物下方） ===
        JPanel barPanel = new PillPanel(BAR_PADDING);
        barPanel.setLayout(new BoxLayout(barPanel, BoxLayout.X_AXIS));
        barPanel.add(buildButton("🍖 喂食", this::onFeed));
        barPanel.add(Box.createHorizontalStrut(BUTTON_GAP));
        barPanel.add(buildButton("✎ 改名", this::onRename));
        barPanel.add(Box.createHorizontalStrut(BUTTON_GAP));
        barPanel.add(buildButton("⚙ 设置", this::onOpenSettings));
        barPanel.add(Box.createHorizontalStrut(BUTTON_GAP));
        barPanel.add(buildButton("✕ 隐藏", this::onHide));
        this.actionBarWindow = new JWindow();
        actionBarWindow.setAlwaysOnTop(true);
        actionBarWindow.setBackground(new Color(0, 0, 0, 0));
        actionBarWindow.setContentPane(barPanel);
        barPanel.addMouseListener(pillHoverListener());

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

    /** 悬浮层通用 hover 监听：进入锁定，离开解锁并调度隐藏。 */
    private MouseAdapter pillHoverListener() {
        return new MouseAdapter() {
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
        };
    }

    /**
     * 鼠标进入宠物区域时调用：启动显示延迟。
     *
     * @param centerX 宠物中心的屏幕 X
     * @param petTopY 宠物顶边的屏幕 Y
     * @param petBottomY 宠物底边的屏幕 Y
     */
    public void onPetEntered(int centerX, int petTopY, int petBottomY) {
        this.anchorCenterX = centerX;
        this.anchorPetTopY = petTopY;
        this.anchorPetBottomY = petBottomY;
        locked = true;
        cancelHide();
        refreshContent();
        if (!statsWindow.isVisible()) {
            showTimer.restart();
        } else {
            // 已经在显示时只要刷新定位即可
            doShow();
        }
    }

    /** 鼠标离开宠物区域时调用：启动隐藏延迟（若鼠标进了悬浮层会自动取消）。 */
    public void onPetExited() {
        if (!locked) {
            scheduleHide();
        }
    }

    /**
     * 宠物窗口移动时调用：实时更新悬浮层锚点，让数值胶囊与按钮条跟随宠物。
     */
    public void updateLocation(int centerX, int petTopY, int petBottomY) {
        this.anchorCenterX = centerX;
        this.anchorPetTopY = petTopY;
        this.anchorPetBottomY = petBottomY;
        if (statsWindow.isVisible()) {
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
        SwingUtilities.invokeLater(() -> {
            statsWindow.dispose();
            actionBarWindow.dispose();
        });
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
        if (statsWindow.isVisible()) {
            reposition();
            return;
        }
        refreshContent();
        statsWindow.pack();
        actionBarWindow.pack();
        reposition();
        statsWindow.setVisible(true);
        actionBarWindow.setVisible(true);
    }

    private void doHide() {
        cancelHide();
        statsWindow.setVisible(false);
        actionBarWindow.setVisible(false);
    }

    private void reposition() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        // 数值胶囊：宠物上方居中
        Dimension statsSize = statsWindow.getSize();
        int sx = anchorCenterX - statsSize.width / 2;
        int sy = anchorPetTopY - statsSize.height - GAP;
        sx = Math.max(screen.x, Math.min(sx, screen.x + screen.width - statsSize.width));
        // 上方放不下时贴屏幕顶
        sy = Math.max(screen.y, sy);
        statsWindow.setLocation(sx, sy);

        // 按钮条：宠物下方居中
        Dimension barSize = actionBarWindow.getSize();
        int bx = anchorCenterX - barSize.width / 2;
        int by = anchorPetBottomY + GAP;
        bx = Math.max(screen.x, Math.min(bx, screen.x + screen.width - barSize.width));
        // 下方放不下（贴任务栏）时改到宠物上方
        if (by + barSize.height > screen.y + screen.height) {
            by = anchorPetTopY - barSize.height - GAP;
        }
        actionBarWindow.setLocation(bx, Math.max(screen.y, by));
    }

    private void refreshContent() {
        PetSettingsState settings = loadSettings();
        if (settings == null) return;
        int intimacy = settings.getIntimacy();
        statsLabel.setText("亲密 " + intimacy
                + " · 小鱼干 " + settings.getFishCount()
                + " · 点数 " + settings.getPoints()
                + " · Lv." + PetSettingsState.intimacyLevel(intimacy));
    }

    private JButton buildButton(String text, Runnable action) {
        FlatButton btn = new FlatButton(text);
        btn.addActionListener(e -> {
            action.run();
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

    /** 打开设置页（Tools → Pet Whale 鲸鱼娘），打开前先收起悬浮层。 */
    private void onOpenSettings() {
        hideNow();
        ShowSettingsUtil.getInstance().editConfigurable(
                (Component) actionBarWindow,
                new PetSettingsConfigurable());
    }

    private void onHide() {
        hideNow();
        frame.hide();
    }

    /**
     * 亮色圆角胶囊容器：数值胶囊与按钮条共用。每帧先 Clear 清到透明再画圆角，
     * 避免透明窗体残影。
     */
    private static final class PillPanel extends JPanel {
        private final Insets padding;

        PillPanel(Insets padding) {
            this.padding = padding;
            setOpaque(false);
            setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
            setBorder(BorderFactory.createEmptyBorder(
                    padding.top, padding.left, padding.bottom, padding.right));
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

                g2.setColor(PILL_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);
                g2.setColor(PILL_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 亮色扁平按钮：白底胶囊上的文字按钮，悬停浅蓝高亮。
     */
    private static final class FlatButton extends JButton {
        private boolean hovering;

        FlatButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(TEXT_MAIN);
            setFont(getFont().deriveFont(Font.PLAIN, 11f));
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

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (hovering) {
                    g2.setColor(BTN_HOVER);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
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

    private static PetSettingsState loadSettings() {
        try {
            if (ApplicationManager.getApplication() == null) return null;
            return ApplicationManager.getApplication().getService(PetSettingsState.class);
        } catch (Throwable t) {
            return null;
        }
    }
}
