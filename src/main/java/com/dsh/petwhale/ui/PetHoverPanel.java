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
import javax.swing.SwingConstants;
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
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 悬浮交互层（深色系，配色参考用户设计图），按宠物身上的停留区域<b>分区触发</b>：
 *
 * <ul>
 *   <li><b>数值胶囊</b>：贴宠物<b>头顶上方</b>。鼠标停留在宠物<b>头部区域</b>
 *       （顶边下方 25% 高度以上）时显示。内容：名字（亮青蓝）+ Lv（绿）+ 亲密度称号（粉），
 *       以及 亲密度 / 小鱼干 / 点数 三条彩色渐变进度条 + 彩色数值。</li>
 *   <li><b>按钮卡片</b>：贴宠物<b>脚底下方</b>。鼠标停留在宠物<b>脚部区域</b>
 *       （底边上方 25% 高度以下）时显示。内容：一排紧凑按钮（喂食 / 改名 / 设置 / 隐藏），
 *       按钮大小与字体适配。</li>
 * </ul>
 *
 * <p>两层都不压宠物本体；鼠标在宠物触发区 ↔ 悬浮层之间移动保持显示，
 * <b>完全离开</b>后约 320ms 消失（1 秒内，且给层间小间隙留穿越宽限）。</p>
 *
 * <p>所有 Swing 操作都限制在 EDT 内；调用方无需提前切线程。</p>
 */
public final class PetHoverPanel {

    /** 悬浮层与宠物边缘的间隙（像素） */
    private static final int GAP = 6;
    /** 胶囊/卡片圆角半径 */
    private static final int CORNER = 12;
    /** 显示延迟：鼠标进入触发区域后多少毫秒才显示（避免划过时频繁闪现） */
    private static final int SHOW_DELAY_MS = 260;
    /**
     * 隐藏延迟：鼠标完全离开宠物和悬浮层后多少毫秒隐藏。
     * 320ms：给"脚部区域 ↔ 卡片"之间的小间隙留出穿越宽限（仍在 1 秒内消失）。
     */
    private static final int HIDE_DELAY_MS = 320;
    /** 胶囊内边距 */
    private static final Insets STATS_PADDING = new Insets(8, 12, 9, 12);
    /** 卡片内边距（按钮已紧贴文字，卡片留白同步收紧） */
    private static final Insets CARD_PADDING = new Insets(5, 8, 6, 8);
    /** 按钮间距 */
    private static final int BUTTON_GAP = 4;
    /** 按钮文字两侧的固定留白（紧紧包裹文字即可） */
    private static final int BTN_PAD_X = 6;
    /** 按钮文字上下的固定留白 */
    private static final int BTN_PAD_Y = 2;
    /** 进度条尺寸 */
    private static final Dimension BAR_SIZE = new Dimension(110, 7);

    // === 深色系配色（参考用户截图） ===
    private static final Color CARD_BG = new Color(28, 30, 46, 246);
    private static final Color CARD_BORDER = new Color(76, 82, 118, 255);
    private static final Color TEXT_MAIN = new Color(226, 230, 242);
    private static final Color TEXT_SUB = new Color(168, 175, 196);
    private static final Color NAME_COLOR = new Color(126, 196, 255);
    private static final Color TITLE_COLOR = new Color(255, 155, 192);
    private static final Color BTN_BG = new Color(47, 58, 120);
    private static final Color BTN_HOVER = new Color(70, 86, 172);
    private static final Color BTN_TEXT = new Color(238, 241, 250);
    private static final Color BAR_TRACK = new Color(58, 62, 88);
    private static final Color BAR_BORDER = new Color(96, 102, 138);
    /** 三条进度条各自的渐变色（亲密度粉紫 / 小鱼干青蓝 / 点数橙） */
    private static final Color INTIMACY_FROM = new Color(255, 107, 157);
    private static final Color INTIMACY_TO = new Color(186, 120, 255);
    private static final Color FISH_FROM = new Color(86, 164, 255);
    private static final Color FISH_TO = new Color(96, 225, 230);
    private static final Color POINTS_FROM = new Color(255, 196, 120);
    private static final Color POINTS_TO = new Color(255, 140, 90);

    /** 帮助弹窗：悬停/点击"?"后延迟显示，移出后延迟隐藏 */
    private static final int HELP_SHOW_DELAY_MS = 300;
    private static final int HELP_HIDE_DELAY_MS = 250;
    /** 帮助弹窗内容：亲密度 / 称号 / 小鱼干 / 点数的玩法说明 */
    private static final String HELP_HTML = "<html><body style='width:230px'>"
            + "<b><font color='#7EC4FF'>亲密度</font></b>：每次喂食 +10，Lv 每 200 点升 1 级<br>"
            + "<b><font color='#FF9BC0'>称号</font></b>：素昧平生 &lt;100 / 一见如故 100+ / "
            + "心意相通 300+ / 心有灵犀 600+ / 灵魂伴侣 1000+<br>"
            + "<b><font color='#56A4FF'>小鱼干</font></b>：喂食消耗，初始 20 条，进度按 99 条满格<br>"
            + "<b><font color='#FFA050'>点数</font></b>：每次喂食 +5 累计，进度按 500 满格"
            + "</body></html>";

    private final PetFrame frame;
    private final PetStateService service;
    /** 数值胶囊窗口（宠物头顶上方，头部区域触发） */
    private final JWindow statsWindow;
    /** 按钮卡片窗口（宠物脚底下方，脚部区域触发） */
    private final JWindow cardWindow;
    private final JLabel nameLabel;
    private final JLabel levelLabel;
    private final JLabel titleLabel;
    private final StatBar intimacyBar;
    private final StatBar fishBar;
    private final StatBar pointsBar;
    private final JLabel intimacyValueLabel;
    private final JLabel fishValueLabel;
    private final JLabel pointsValueLabel;

    private final Timer showTimer;
    private final Timer hideTimer;
    /** 帮助说明弹窗（"?"按钮触发，悬停/点击显示） */
    private final JWindow helpWindow;
    private final Timer helpShowTimer;
    private final Timer helpHideTimer;
    /** 鼠标当前是否在帮助弹窗内（移入弹窗时保持显示） */
    private boolean mouseInHelp;

    /** 当前面板是否被显式锁定（鼠标在宠物触发区或悬浮层内） */
    private boolean locked;
    /** 鼠标当前是否在胶囊/卡片内（区分"移入悬浮层"与"彻底离开"） */
    private boolean mouseInOverlay;
    /** 待显示的悬浮层：true=胶囊（头部触发），false=卡片（脚部触发） */
    private boolean pendingStatsZone;
    /** 宠物锚点：中心 X / 顶边 Y / 底边 Y（屏幕坐标），供刷新内容时重新定位 */
    private int anchorCenterX;
    private int anchorPetTopY;
    private int anchorPetBottomY;

    public PetHoverPanel(@NotNull PetFrame frame, @NotNull PetStateService service) {
        this.frame = frame;
        this.service = service;

        // === 数值胶囊（头顶上方，头部区域触发） ===
        JPanel statsPanel = new PillPanel(STATS_PADDING);
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));

        // 行1：名字 + Lv + 称号
        JPanel profileRow = new JPanel(new GridBagLayout());
        profileRow.setOpaque(false);
        nameLabel = new JLabel();
        nameLabel.setForeground(NAME_COLOR);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 11f));
        levelLabel = new JLabel();
        levelLabel.setForeground(new Color(74, 222, 128));
        levelLabel.setFont(levelLabel.getFont().deriveFont(Font.BOLD, 10f));
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
        pc.insets = new Insets(0, 5, 0, 0);
        profileRow.add(levelLabel, pc);
        pc.gridx = 2;
        pc.weightx = 1;
        pc.anchor = GridBagConstraints.EAST;
        pc.insets = new Insets(0, 0, 0, 0);
        profileRow.add(titleLabel, pc);
        pc.gridx = 3;
        pc.weightx = 0;
        pc.insets = new Insets(0, 4, 0, 0);
        JComponent helpButton = new HelpButton();
        profileRow.add(helpButton, pc);
        profileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statsPanel.add(profileRow);
        statsPanel.add(Box.createVerticalStrut(5));

        // 行2-4：亲密度 / 小鱼干 / 点数 彩色进度条
        intimacyBar = new StatBar(INTIMACY_FROM, INTIMACY_TO);
        intimacyValueLabel = valueLabel(INTIMACY_FROM);
        statsPanel.add(statRow("亲密度", intimacyBar, intimacyValueLabel));
        statsPanel.add(Box.createVerticalStrut(4));
        fishBar = new StatBar(FISH_FROM, FISH_TO);
        fishValueLabel = valueLabel(FISH_FROM);
        statsPanel.add(statRow("小鱼干", fishBar, fishValueLabel));
        statsPanel.add(Box.createVerticalStrut(4));
        pointsBar = new StatBar(POINTS_FROM, POINTS_TO);
        pointsValueLabel = valueLabel(POINTS_FROM);
        statsPanel.add(statRow("点数", pointsBar, pointsValueLabel));

        this.statsWindow = new JWindow();
        statsWindow.setAlwaysOnTop(true);
        statsWindow.setBackground(new Color(0, 0, 0, 0));
        statsWindow.setContentPane(statsPanel);

        // === 按钮卡片（脚底下方，脚部区域触发） ===
        JPanel cardPanel = new PillPanel(CARD_PADDING);
        // FlowLayout：按钮按文字实际大小排列（BoxLayout 会把按钮拉伸均分卡片宽度）
        cardPanel.setLayout(new FlowLayout(FlowLayout.CENTER, BUTTON_GAP, 0));
        cardPanel.add(buildButton("喂食", this::onFeed));
        cardPanel.add(buildButton("改名", this::onRename));
        cardPanel.add(buildButton("设置", this::onOpenSettings));
        cardPanel.add(buildButton("归位", this::onReturnHome));
        cardPanel.add(buildButton("隐藏", this::onHide));

        this.cardWindow = new JWindow();
        cardWindow.setAlwaysOnTop(true);
        cardWindow.setBackground(new Color(0, 0, 0, 0));
        cardWindow.setContentPane(cardPanel);

        // === 帮助说明弹窗（"?"按钮触发，悬停/点击显示） ===
        JPanel helpPanel = new PillPanel(new Insets(8, 11, 9, 11));
        JLabel helpLabel = new JLabel(HELP_HTML);
        helpLabel.setForeground(TEXT_MAIN);
        helpLabel.setFont(helpLabel.getFont().deriveFont(Font.PLAIN, 11f));
        helpPanel.add(helpLabel);
        this.helpWindow = new JWindow();
        helpWindow.setAlwaysOnTop(true);
        helpWindow.setBackground(new Color(0, 0, 0, 0));
        helpWindow.setContentPane(helpPanel);

        this.helpShowTimer = new Timer(HELP_SHOW_DELAY_MS, e -> SwingUtilities.invokeLater(this::showHelp));
        helpShowTimer.setRepeats(false);
        this.helpHideTimer = new Timer(HELP_HIDE_DELAY_MS, e -> SwingUtilities.invokeLater(() -> {
            if (!mouseInHelp) {
                hideHelp();
            }
        }));
        helpHideTimer.setRepeats(false);

        // "?"触发器：悬停延迟弹出、移出延迟收起、点击立即开/关
        helpButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                helpHideTimer.stop();
                helpShowTimer.start();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                helpShowTimer.stop();
                helpHideTimer.restart();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                helpShowTimer.stop();
                helpHideTimer.stop();
                if (helpWindow.isVisible()) {
                    hideHelp();
                } else {
                    showHelp();
                }
            }
        });

        // 鼠标移入帮助弹窗本身时保持显示（与悬浮层同款守卫思路）
        MouseAdapter helpGuard = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                mouseInHelp = true;
                helpHideTimer.stop();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                mouseInHelp = false;
                helpHideTimer.restart();
            }
        };
        helpPanel.addMouseListener(helpGuard);
        helpLabel.addMouseListener(helpGuard);

        // === 悬停守卫：面板与其所有子组件（按钮/标签/进度条）都挂同一监听 ===
        // Swing 中鼠标从面板移到子组件上也会触发面板的 mouseExited，
        // 只挂面板会导致"移到按钮上就被判定离开、悬浮层消失"。
        attachHoverGuard(statsPanel);
        attachHoverGuard(cardPanel);

        this.showTimer = new Timer(SHOW_DELAY_MS, e -> SwingUtilities.invokeLater(() -> {
            if (locked) {
                doShowZone();
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

    /** 共享的悬停监听：进入锁定并取消隐藏，离开解锁并调度隐藏。 */
    private final MouseAdapter overlayHoverListener = new MouseAdapter() {
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

    /**
     * 递归给组件树挂上悬停守卫：鼠标在悬浮层窗口内部任意跨组件移动
     * （面板 ↔ 按钮 ↔ 标签 ↔ 进度条）都视为"仍在悬浮层内"，不会误调度隐藏。
     * 事件时序上 exit(prev) 先于 enter(next)，最后一个事件总是 enter，状态收敛正确。
     */
    private void attachHoverGuard(JComponent comp) {
        comp.addMouseListener(overlayHoverListener);
        for (Component child : comp.getComponents()) {
            if (child instanceof JComponent jc) {
                attachHoverGuard(jc);
            }
        }
    }

    /**
     * 鼠标停留在宠物触发区域时调用（头部 → 胶囊，脚部 → 卡片）。
     * 同一时刻只显示当前区域对应的悬浮层；区域切换时立即切换。
     *
     * @param statsZone true=头部触发（显示胶囊），false=脚部触发（显示卡片）
     * @param centerX 宠物中心的屏幕 X
     * @param petTopY 宠物顶边的屏幕 Y
     * @param petBottomY 宠物底边的屏幕 Y
     */
    public void showZone(boolean statsZone, int centerX, int petTopY, int petBottomY) {
        this.anchorCenterX = centerX;
        this.anchorPetTopY = petTopY;
        this.anchorPetBottomY = petBottomY;
        refreshContent();
        locked = true;
        cancelHide();

        JWindow target = statsZone ? statsWindow : cardWindow;
        if (target.isVisible()) {
            reposition(); // 已在显示，刷新定位即可
            return;
        }
        pendingStatsZone = statsZone;
        if (statsWindow.isVisible() || cardWindow.isVisible()) {
            doShowZone(); // 区域切换：立即换层
        } else {
            showTimer.restart(); // 首次唤出：防划过闪现
        }
    }

    /**
     * 鼠标离开宠物区域（或移动到中部无触发区）时调用：解锁并调度隐藏（200ms 内消失）。
     * 若鼠标只是移入悬浮层，配合 {@code mouseInOverlay} 标记不会误隐藏。
     */
    public void onPetExited() {
        locked = false;
        if (!mouseInOverlay) {
            scheduleHide();
        }
    }

    /**
     * 宠物窗口移动时调用：实时更新悬浮层锚点。
     */
    public void updateLocation(int centerX, int petTopY, int petBottomY) {
        this.anchorCenterX = centerX;
        this.anchorPetTopY = petTopY;
        this.anchorPetBottomY = petBottomY;
        if (statsWindow.isVisible() || cardWindow.isVisible()) {
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
        helpShowTimer.stop();
        helpHideTimer.stop();
        SwingUtilities.invokeLater(() -> {
            statsWindow.dispose();
            cardWindow.dispose();
            helpWindow.dispose();
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

    /** 显示待定区域的悬浮层，并收起另一个（同一时刻只显示一层）。 */
    private void doShowZone() {
        cancelShow();
        refreshContent();
        JWindow target = pendingStatsZone ? statsWindow : cardWindow;
        JWindow other = pendingStatsZone ? cardWindow : statsWindow;
        other.setVisible(false);
        if (!target.isVisible()) {
            target.pack();
        }
        reposition();
        target.setVisible(true);
    }

    private void doHide() {
        cancelHide();
        statsWindow.setVisible(false);
        cardWindow.setVisible(false);
        hideHelp();
    }

    /**
     * 显示帮助弹窗（仅胶囊可见时）：贴在胶囊下方、与胶囊右对齐，越界夹回屏幕。
     */
    private void showHelp() {
        if (!statsWindow.isVisible()) {
            return;
        }
        helpWindow.pack();
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        Dimension hs = helpWindow.getSize();
        Dimension ss = statsWindow.getSize();
        Point base = statsWindow.getLocation();
        int x = base.x + ss.width - hs.width;
        int y = base.y + ss.height + 4;
        x = Math.max(screen.x, Math.min(x, screen.x + screen.width - hs.width));
        y = Math.max(screen.y, Math.min(y, screen.y + screen.height - hs.height));
        helpWindow.setLocation(x, y);
        helpWindow.setVisible(true);
    }

    /** 立即收起帮助弹窗并取消显示定时器。 */
    private void hideHelp() {
        helpShowTimer.stop();
        helpWindow.setVisible(false);
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

        // 按钮卡片：宠物下方居中
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

        nameLabel.setText(settings.getPetName());
        levelLabel.setText("Lv." + PetSettingsState.intimacyLevel(intimacy));
        titleLabel.setText("<html><font color='#C6CCDC'>亲密度</font>&nbsp;<font color='#FF9BC0'><b>"
                + PetSettingsState.intimacyTitle(intimacy) + "</b></font></html>");

        intimacyBar.setPercent(PetSettingsState.intimacyProgress(intimacy));
        intimacyValueLabel.setText(String.valueOf(intimacy));
        fishBar.setPercent(PetSettingsState.fishProgress(settings.getFishCount()));
        fishValueLabel.setText("\u00d7" + settings.getFishCount());
        pointsBar.setPercent(PetSettingsState.pointsProgress(settings.getPoints()));
        pointsValueLabel.setText(String.valueOf(settings.getPoints()));
    }

    /**
     * 彩色数值标签（粗体 11）。固定宽度 + 右对齐：三条数值宽度不同（如 ×0 与 200），
     * 不锁宽会让行宽参差、进度条跟着错位。
     */
    private static JLabel valueLabel(Color color) {
        JLabel label = new JLabel();
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setHorizontalAlignment(SwingConstants.RIGHT);
        label.setPreferredSize(new Dimension(36, label.getPreferredSize().height));
        return label;
    }

    /** 一行进度条：灰色标签 + 彩色条 + 彩色数值。行组件 LEFT_ALIGNMENT 对齐（BoxLayout 默认居中会让行左右错位）。 */
    private static JComponent statRow(String caption, JComponent bar, JComponent value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel cap = new JLabel(caption);
        cap.setForeground(TEXT_SUB);
        cap.setFont(cap.getFont().deriveFont(Font.PLAIN, 10f));
        row.add(cap);
        row.add(bar);
        row.add(value);
        return row;
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

    /** 一键回到屏幕右下角老家（设置页同款能力，卡片内也能用）。 */
    private void onReturnHome() {
        frame.returnToHome();
        refreshContent();
    }

    /**
     * 通用彩色进度条：圆角深色槽 + 指定色渐变填充 + 细描边，纯自绘组件。
     */
    private static final class StatBar extends JComponent {
        private final Color fillFrom;
        private final Color fillTo;
        private int percent;

        StatBar(Color fillFrom, Color fillTo) {
            this.fillFrom = fillFrom;
            this.fillTo = fillTo;
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
                // 渐变填充
                if (percent > 0) {
                    int fillW = Math.max(h, (getWidth() - 2) * percent / 100);
                    g2.setPaint(new GradientPaint(0, 0, fillFrom, getWidth(), 0, fillTo));
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
     * 深色圆角底板容器：数值胶囊与按钮卡片共用。每帧先 Clear 清到透明再画圆角，
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
     * 圆形"？"帮助按钮：深蓝底白字，悬停提亮。悬停/点击弹出数值说明（见 {@code HELP_HTML}）。
     */
    private static final class HelpButton extends JComponent {
        private boolean hovering;

        HelpButton() {
            setOpaque(false);
            setPreferredSize(new Dimension(15, 15));
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
                g2.setComposite(java.awt.AlphaComposite.Clear);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setComposite(java.awt.AlphaComposite.SrcOver);
                g2.setColor(hovering ? BTN_HOVER : CARD_BORDER);
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.setColor(BTN_TEXT);
                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 9.5f));
                FontMetrics fm = g2.getFontMetrics();
                String q = "?";
                int tx = (getWidth() - fm.stringWidth(q)) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(q, tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * 深蓝渐变底白字圆角按钮，尺寸<b>严格按文字宽高计算</b>（覆盖
     * {@link #getPreferredSize()}，只留固定小留白，紧紧包裹文字），
     * 悬停提亮、按下压暗。
     */
    private static final class FlatButton extends JButton {
        private boolean hovering;
        private boolean pressed;

        FlatButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(BTN_PAD_Y, BTN_PAD_X, BTN_PAD_Y, BTN_PAD_X));
            setMargin(new Insets(0, 0, 0, 0));
            setContentAreaFilled(false);
            setOpaque(false);
            setForeground(BTN_TEXT);
            setFont(getFont().deriveFont(Font.BOLD, 10f));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    pressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    repaint();
                }
            });
        }

        /** 尺寸 = 文本实际宽高 + 固定小留白，不受平台默认边距影响。 */
        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(
                    fm.stringWidth(getText()) + BTN_PAD_X * 2,
                    fm.getHeight() + BTN_PAD_Y * 2);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int h = getHeight();
                Color top = hovering ? BTN_HOVER : BTN_BG;
                Color bottom = pressed ? BTN_BG : hovering ? new Color(86, 104, 204) : new Color(58, 72, 148);
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fillRoundRect(0, 0, getWidth() - 1, h - 1, 9, 9);
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                String txt = getText();
                int tx = (getWidth() - fm.stringWidth(txt)) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
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
