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
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
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
 * 鼠标悬停在桌宠上时浮现的交互悬浮层（配色与布局参考用户提供的深色卡片设计图）。
 *
 * <p>拆成两个独立 {@link JWindow}，均<b>不遮挡宠物本体</b>：
 * <ul>
 *   <li><b>数值胶囊</b>（宠物上方）：深色圆角条，带彩色进度条样式——
 *       亲密度进度条（粉紫渐变）+ 彩色数字行（小鱼干 / 点数 / 等级）。</li>
 *   <li><b>档案卡片</b>（宠物下方）：深色圆角卡片——第一行名字（亮青蓝）+ 右侧
 *       亲密度称号（粉紫），第二行一排深蓝底白字圆角按钮（喂食 / 改名 / 设置 / 隐藏）。</li>
 * </ul>
 *
 * <p>显示纪律：鼠标在宠物 ↔ 胶囊 ↔ 卡片任一区域内都保持显示，
 * <b>完全离开宠物和悬浮层</b>之后才延迟隐藏（200ms，保证 1 秒内消失且不闪烁）。</p>
 *
 * <p>所有 Swing 操作都限制在 EDT 内；调用方无需提前切线程。</p>
 */
public final class PetHoverPanel {

    /** 悬浮层与宠物边缘的间隙（像素） */
    private static final int GAP = 4;
    /** 胶囊/卡片圆角半径 */
    private static final int CORNER = 12;
    /** 显示延迟：鼠标进入宠物后多少毫秒才显示（避免划过宠物时频繁闪现） */
    private static final int SHOW_DELAY_MS = 280;
    /** 隐藏延迟：鼠标完全离开宠物和悬浮层后多少毫秒隐藏 */
    private static final int HIDE_DELAY_MS = 200;
    /** 胶囊内边距 */
    private static final Insets STATS_PADDING = new Insets(7, 12, 8, 12);
    /** 卡片内边距 */
    private static final Insets CARD_PADDING = new Insets(8, 12, 8, 12);
    /** 按钮间距 */
    private static final int BUTTON_GAP = 4;
    /** 亲密度进度条尺寸 */
    private static final Dimension BAR_SIZE = new Dimension(150, 9);

    // === 深色系配色（参考用户截图：深蓝黑底 + 彩色数字） ===
    private static final Color CARD_BG = new Color(28, 30, 46, 246);
    private static final Color CARD_BORDER = new Color(76, 82, 118, 255);
    private static final Color TEXT_MAIN = new Color(226, 230, 242);
    private static final Color TEXT_SUB = new Color(168, 175, 196);
    private static final Color NAME_COLOR = new Color(126, 196, 255);
    private static final Color BTN_BG = new Color(47, 58, 120);
    private static final Color BTN_HOVER = new Color(70, 86, 172);
    private static final Color BTN_TEXT = new Color(238, 241, 250);
    private static final Color BAR_TRACK = new Color(58, 62, 88);
    private static final Color BAR_FILL_FROM = new Color(255, 107, 157);
    private static final Color BAR_FILL_TO = new Color(186, 120, 255);
    private static final Color BAR_BORDER = new Color(96, 102, 138);

    private final PetFrame frame;
    private final PetStateService service;
    /** 数值胶囊窗口（宠物上方，进度条样式） */
    private final JWindow statsWindow;
    /** 档案卡片窗口（宠物下方） */
    private final JWindow cardWindow;
    private final IntimacyBar intimacyBar;
    private final JLabel intimacyValueLabel;
    private final JLabel statsLineLabel;
    private final JLabel nameLabel;
    private final JLabel titleLabel;

    private final Timer showTimer;
    private final Timer hideTimer;

    /** 当前面板是否被显式锁定（鼠标在宠物或任一悬浮层内） */
    private boolean locked;
    /** 鼠标当前是否在胶囊/卡片内（区分"移入悬浮层"与"彻底离开"） */
    private boolean mouseInOverlay;
    /** 宠物锚点：中心 X / 顶边 Y / 底边 Y（屏幕坐标），供刷新内容时重新定位 */
    private int anchorCenterX;
    private int anchorPetTopY;
    private int anchorPetBottomY;

    public PetHoverPanel(@NotNull PetFrame frame, @NotNull PetStateService service) {
        this.frame = frame;
        this.service = service;

        // === 数值胶囊（宠物上方，彩色进度条样式） ===
        JPanel statsPanel = new PillPanel(STATS_PADDING);
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));

        JPanel barRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        barRow.setOpaque(false);
        JLabel barCaption = new JLabel("亲密度");
        barCaption.setForeground(TEXT_SUB);
        barCaption.setFont(barCaption.getFont().deriveFont(Font.PLAIN, 10f));
        intimacyBar = new IntimacyBar();
        intimacyValueLabel = new JLabel("0");
        intimacyValueLabel.setForeground(BAR_FILL_FROM);
        intimacyValueLabel.setFont(intimacyValueLabel.getFont().deriveFont(Font.BOLD, 12f));
        barRow.add(barCaption);
        barRow.add(intimacyBar);
        barRow.add(intimacyValueLabel);
        statsPanel.add(barRow);
        statsPanel.add(Box.createVerticalStrut(5));

        statsLineLabel = new JLabel();
        statsLineLabel.setForeground(TEXT_MAIN);
        statsLineLabel.setFont(statsLineLabel.getFont().deriveFont(Font.PLAIN, 11f));
        statsPanel.add(statsLineLabel);

        this.statsWindow = new JWindow();
        statsWindow.setAlwaysOnTop(true);
        statsWindow.setBackground(new Color(0, 0, 0, 0));
        statsWindow.setContentPane(statsPanel);
        statsPanel.addMouseListener(pillHoverListener());

        // === 档案卡片（宠物下方） ===
        JPanel cardPanel = new PillPanel(CARD_PADDING);
        cardPanel.setLayout(new BoxLayout(cardPanel, BoxLayout.Y_AXIS));

        JPanel profileRow = new JPanel(new GridBagLayout());
        profileRow.setOpaque(false);
        nameLabel = new JLabel();
        nameLabel.setForeground(NAME_COLOR);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel = new JLabel();
        titleLabel.setForeground(TEXT_SUB);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 10f));
        GridBagConstraints pc = new GridBagConstraints();
        pc.gridx = 0;
        pc.gridy = 0;
        pc.weightx = 0;
        pc.anchor = GridBagConstraints.WEST;
        profileRow.add(nameLabel, pc);
        pc.gridx = 1;
        pc.weightx = 1;
        pc.anchor = GridBagConstraints.EAST;
        profileRow.add(titleLabel, pc);
        cardPanel.add(profileRow);
        cardPanel.add(Box.createVerticalStrut(6));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, BUTTON_GAP, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(buildButton("喂食", this::onFeed));
        buttonRow.add(buildButton("改名", this::onRename));
        buttonRow.add(buildButton("设置", this::onOpenSettings));
        buttonRow.add(buildButton("隐藏", this::onHide));
        cardPanel.add(buttonRow);

        this.cardWindow = new JWindow();
        cardWindow.setAlwaysOnTop(true);
        cardWindow.setBackground(new Color(0, 0, 0, 0));
        cardWindow.setContentPane(cardPanel);
        cardPanel.addMouseListener(pillHoverListener());

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
                mouseInOverlay = true;
                locked = true;
                cancelHide();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseInOverlay = false;
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

    /**
     * 鼠标离开宠物区域时调用：解锁并调度隐藏（200ms 内消失）。
     * 若鼠标只是移入胶囊/卡片，它们的 mouseEntered 已重新锁定/或即将锁定，
     * 配合 {@code mouseInOverlay} 标记两个方向的事件顺序都不会误隐藏。
     */
    public void onPetExited() {
        locked = false;
        if (!mouseInOverlay) {
            scheduleHide();
        }
    }

    /**
     * 宠物窗口移动时调用：实时更新悬浮层锚点，让胶囊与卡片跟随宠物。
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
        mouseInOverlay = false;
        cancelShow();
        doHide();
    }

    /** 释放窗口资源（PetFrame.dispose 时调用）。 */
    public void dispose() {
        cancelShow();
        cancelHide();
        SwingUtilities.invokeLater(() -> {
            statsWindow.dispose();
            cardWindow.dispose();
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
        cardWindow.pack();
        reposition();
        statsWindow.setVisible(true);
        cardWindow.setVisible(true);
    }

    private void doHide() {
        cancelHide();
        statsWindow.setVisible(false);
        cardWindow.setVisible(false);
    }

    private void reposition() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();

        // 数值胶囊：宠物上方居中
        Dimension statsSize = statsWindow.getSize();
        int sx = anchorCenterX - statsSize.width / 2;
        int sy = anchorPetTopY - statsSize.height - GAP;
        sx = Math.max(screen.x, Math.min(sx, screen.x + screen.width - statsSize.width));
        sy = Math.max(screen.y, sy); // 上方放不下时贴屏幕顶
        statsWindow.setLocation(sx, sy);

        // 档案卡片：宠物下方居中
        Dimension cardSize = cardWindow.getSize();
        int cx = anchorCenterX - cardSize.width / 2;
        int cy = anchorPetBottomY + GAP;
        cx = Math.max(screen.x, Math.min(cx, screen.x + screen.width - cardSize.width));
        // 下方放不下（贴任务栏）时改到宠物上方
        if (cy + cardSize.height > screen.y + screen.height) {
            cy = anchorPetTopY - cardSize.height - GAP;
        }
        cardWindow.setLocation(cx, Math.max(screen.y, cy));
    }

    private void refreshContent() {
        PetSettingsState settings = loadSettings();
        if (settings == null) return;
        int intimacy = settings.getIntimacy();
        intimacyBar.setPercent(PetSettingsState.intimacyProgress(intimacy));
        intimacyValueLabel.setText(String.valueOf(intimacy));
        statsLineLabel.setText("<html>小鱼干 <font color='#6CB6FF'><b>\u00d7" + settings.getFishCount()
                + "</b></font>&nbsp;&nbsp;<font color='#FFB86C'><b>" + settings.getPoints()
                + "</b></font> 点&nbsp;&nbsp;<font color='#4ADE80'><b>Lv."
                + PetSettingsState.intimacyLevel(intimacy) + "</b></font></html>");
        nameLabel.setText(settings.getPetName());
        titleLabel.setText("<html><font color='#C6CCDC'>亲密度</font>&nbsp;<font color='#FF9BC0'><b>"
                + PetSettingsState.intimacyTitle(intimacy) + "</b></font></html>");
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
                (Component) cardWindow,
                new PetSettingsConfigurable());
    }

    private void onHide() {
        hideNow();
        frame.hide();
    }

    /**
     * 亲密度进度条：圆角深色槽 + 粉紫渐变填充，纯自绘组件。
     */
    private static final class IntimacyBar extends JComponent {
        private int percent;

        IntimacyBar() {
            setOpaque(false);
            setPreferredSize(BAR_SIZE);
        }

        void setPercent(int value) {
            this.percent = Math.max(0, Math.min(100, value));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);

                int h = getHeight();
                // 深色圆角槽
                g2.setColor(BAR_TRACK);
                g2.fillRoundRect(0, 0, getWidth() - 1, h - 1, h, h);
                // 粉紫渐变填充
                if (percent > 0) {
                    int fillW = Math.max(h, (getWidth() - 2) * percent / 100);
                    g2.setPaint(new GradientPaint(0, 0, BAR_FILL_FROM, getWidth(), 0, BAR_FILL_TO));
                    g2.fillRoundRect(1, 1, fillW - 2, h - 3, h - 2, h - 2);
                }
                // 细描边
                g2.setColor(BAR_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, h - 1, h, h);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 深色圆角底板容器：数值胶囊与档案卡片共用。每帧先 Clear 清到透明再画圆角，
     * 避免透明窗体残影。
     */
    private static final class PillPanel extends JPanel {
        PillPanel(Insets padding) {
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
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

                g2.setColor(CARD_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);
                g2.setColor(CARD_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 深蓝底白字圆角按钮（参考设计图配色），悬停提亮。
     */
    private static final class FlatButton extends JButton {
        private boolean hovering;

        FlatButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(BTN_TEXT);
            setFont(getFont().deriveFont(Font.BOLD, 11f));
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
                g2.setColor(hovering ? BTN_HOVER : BTN_BG);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
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
