package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetTheme;
import com.dsh.petwhale.state.PetSettingsState;
import com.dsh.petwhale.state.PetStateService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.event.FocusAdapter;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.FocusEvent;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * 设置页：Settings → <b>Tools（工具）</b> → Pet Whale 鲸鱼娘。
 *
 * <p>采用卡片式布局：每个设置项位于圆角浅色卡片内，左侧为标题与描述，
 * 右侧为控件与"恢复默认"链接。所有视觉调整（大小、不透明度、主题、显示/隐藏、
 * 状态装饰、溜达速度）都会<b>实时预览</b>到当前桌宠窗口；只有点击 Apply / OK 时才会把值
 * 持久化到 {@link PetSettingsState}。点击 Cancel / 关闭设置页时通过 {@link #reset()}
 * 把预览回滚到持久化值。</p>
 *
 * <p>设置页自身配色<b>跟随 IDEA 主题</b>：全部颜色使用 {@link JBColor} 双值，
 * IDE 亮色 / 暗色主题下自动切换且字体保持清晰。切换"原版 / 精致版"宠物皮肤时，
 * 设置页统一使用海洋蓝强调色，仅宠物形象随主题变化。</p>
 *
 * <p>布局纪律（UI 三不原则）：纵向自然堆叠 + 行间 gap，控件互不重叠。</p>
 */
public final class PetSettingsConfigurable implements Configurable {

    private PetSettingsState state;
    private PetStateService service;

    private JSlider sizeSlider;
    private JLabel sizeValue;
    private JSlider opacitySlider;
    private JLabel opacityValue;
    private ComboBox<PetTheme> themeCombo;
    private ComboBox<BooleanOption> visibleCombo;
    private ComboBox<BooleanOption> decorationsCombo;
    private JCheckBox careCheck;
    private JSlider roamSlider;
    private JLabel roamValue;
    private JCheckBox roamCheck;
    private JTextField intervalField;
    private JTextField careIntervalField;
    private JCheckBox waterCheck;
    private JTextField waterIntervalField;

    /** 当前主题配色方案（设置页卡片/强调色/链接） */
    private ThemePalette palette;
    /** 受主题配色影响的卡片（换主题时刷新） */
    private final List<CardPanel> cards = new ArrayList<>();
    /** 受主题配色影响的链接按钮（换主题时刷新） */
    private final List<JButton> themeLinks = new ArrayList<>();
    /** 设置页根容器，换主题时整体重绘 */
    private JComponent root;

    /** 打开设置页时捕获的持久化值，用于 Cancel 时回滚预览。 */
    private int initialSizePercent;
    private int initialOpacityPercent;
    private PetTheme initialTheme;
    private boolean initialStartHidden;
    private boolean initialShowDecorations;
    private boolean initialCareEnabled;
    private int initialRoamSpeed;
    private boolean initialRoamEnabled;
    private int initialRoamInterval;
    private int initialCareInterval;
    private boolean initialWaterEnabled;
    private int initialWaterInterval;

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "Pet Whale 鲸鱼娘";
    }

    @Override
    public @Nullable JComponent createComponent() {
        state = ApplicationManager.getApplication().getService(PetSettingsState.class);
        service = ApplicationManager.getApplication().getService(PetStateService.class);
        captureInitials();

        palette = paletteOf(initialTheme);
        cards.clear();
        themeLinks.clear();

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(JBUI.Borders.empty(16));
        content.setOpaque(false);

        // === 宠物 ===
        JPanel petCard = new CardPanel("宠物", "选择宠物并调整它的显示布局。");
        petCard.add(buildItem("主题", "",
                buildThemeControl(), () -> setTheme(PetTheme.WHALE)));
        petCard.add(buildItem("状态装饰", "",
                buildDecorationsControl(), () -> setDecorations(true)));
        content.add(petCard);

        content.add(Box.createVerticalStrut(12));

        // === 显示 ===
        JPanel displayCard = new CardPanel("显示", "调整桌宠的大小、透明度与可见性。");
        displayCard.add(buildItem("大小", "",
                buildSizeControl(), () -> setSize(PetSettingsState.DEFAULT_SIZE_PERCENT)));
        displayCard.add(buildItem("不透明度", "",
                buildOpacityControl(), () -> setOpacity(PetSettingsState.DEFAULT_OPACITY_PERCENT)));
        displayCard.add(buildItem("显示宠物", "",
                buildVisibleControl(), () -> setVisible(false)));
        displayCard.add(buildItem("回到原位", "",
                buildReturnHomeControl(), this::returnHome));
        content.add(displayCard);

        content.add(Box.createVerticalStrut(12));

        // === 行为 ===
        JPanel behaviorCard = new CardPanel("行为", "控制鲸鱼娘的自动溜达与节奏。");
        behaviorCard.add(buildItem("自动溜达", "开启后空闲时左右跑动（默认关闭）",
                buildRoamEnabledControl(), () -> setRoamEnabled(PetSettingsState.DEFAULT_ROAM_ENABLED)));
        behaviorCard.add(buildItem("溜达速度", "",
                buildRoamControl(), () -> setRoamSpeed(PetSettingsState.DEFAULT_ROAM_SPEED)));
        behaviorCard.add(buildItem("出发间隔", "两次跑动之间的休息秒数",
                buildIntervalControl(), () -> setInterval(PetSettingsState.DEFAULT_ROAM_INTERVAL_SEC)));
        content.add(behaviorCard);

        content.add(Box.createVerticalStrut(12));

        // === 关怀 ===
        JPanel careCard = new CardPanel("关怀", "久坐提醒与休息建议。");
        careCard.add(buildItem("久坐关怀", "",
                buildCareControl(), () -> setCareEnabled(true)));
        careCard.add(buildItem("关怀间隔", "连续编码多少分钟提醒一次",
                buildCareIntervalControl(), () -> setCareInterval(PetSettingsState.DEFAULT_CARE_INTERVAL_MIN)));
        careCard.add(buildItem("喝水提醒", "与久坐关怀独立的喝水提示",
                buildWaterControl(), () -> setWaterEnabled(PetSettingsState.DEFAULT_WATER_ENABLED)));
        careCard.add(buildItem("喝水间隔", "连续编码多少分钟提醒一次",
                buildWaterIntervalControl(), () -> setWaterInterval(PetSettingsState.DEFAULT_WATER_INTERVAL_MIN)));
        content.add(careCard);

        content.add(Box.createVerticalGlue());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(content, BorderLayout.NORTH);
        this.root = wrapper;
        return wrapper;
    }

    private void captureInitials() {
        initialSizePercent = state.getSizePercent();
        initialOpacityPercent = state.getOpacityPercent();
        initialTheme = state.theme();
        initialStartHidden = state.isStartHidden();
        initialShowDecorations = state.isShowDecorations();
        initialCareEnabled = state.isCareEnabled();
        initialRoamSpeed = state.getRoamSpeed();
        initialRoamEnabled = state.isRoamEnabled();
        initialRoamInterval = state.getRoamIntervalSec();
        initialCareInterval = state.getCareIntervalMin();
        initialWaterEnabled = state.isWaterEnabled();
        initialWaterInterval = state.getWaterIntervalMin();
        // 如果持久化值与服务运行时不一致，以持久化值为准
        service.setShowDecorations(initialShowDecorations);
    }

    // === 控件构造 ===

    private JComponent buildSizeControl() {
        sizeSlider = new JSlider(
                PetSettingsState.MIN_SIZE_PERCENT,
                PetSettingsState.MAX_SIZE_PERCENT,
                initialSizePercent);
        sizeSlider.setMajorTickSpacing(50);
        sizeSlider.setPaintTicks(true);
        sizeValue = new JLabel(initialSizePercent + "%");
        sizeValue.setPreferredSize(new Dimension(40, sizeValue.getPreferredSize().height));
        sizeSlider.addChangeListener(e -> {
            sizeValue.setText(sizeSlider.getValue() + "%");
            applySettingsPreview();
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(sizeSlider);
        row.add(sizeValue);
        return row;
    }

    private JComponent buildOpacityControl() {
        opacitySlider = new JSlider(
                PetSettingsState.MIN_OPACITY_PERCENT,
                PetSettingsState.MAX_OPACITY_PERCENT,
                initialOpacityPercent);
        opacitySlider.setMajorTickSpacing(10);
        opacitySlider.setPaintTicks(true);
        opacityValue = new JLabel(initialOpacityPercent + "%");
        opacityValue.setPreferredSize(new Dimension(40, opacityValue.getPreferredSize().height));
        opacitySlider.addChangeListener(e -> {
            opacityValue.setText(opacitySlider.getValue() + "%");
            applySettingsPreview();
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(opacitySlider);
        row.add(opacityValue);
        return row;
    }

    private JComponent buildThemeControl() {
        themeCombo = new ComboBox<>(PetTheme.values());
        themeCombo.setSelectedItem(initialTheme);
        // ItemListener：选中项一变化立即触发（比 ActionListener 更可靠，预览零延迟）
        themeCombo.addItemListener(e -> {
            PetTheme selected = (PetTheme) themeCombo.getSelectedItem();
            if (selected != null) {
                applyThemePreview(selected);
            }
        });
        return themeCombo;
    }

    private JComponent buildVisibleControl() {
        visibleCombo = new ComboBox<>(BooleanOption.values());
        visibleCombo.setSelectedItem(initialStartHidden ? BooleanOption.OFF : BooleanOption.ON);
        visibleCombo.addActionListener(e -> applyVisiblePreview());
        return visibleCombo;
    }

    private JComponent buildDecorationsControl() {
        decorationsCombo = new ComboBox<>(BooleanOption.values());
        decorationsCombo.setSelectedItem(initialShowDecorations ? BooleanOption.ON : BooleanOption.OFF);
        decorationsCombo.addActionListener(e -> {
            BooleanOption selected = (BooleanOption) decorationsCombo.getSelectedItem();
            service.setShowDecorations(selected == BooleanOption.ON);
        });
        return decorationsCombo;
    }

    private JComponent buildCareControl() {
        careCheck = new JCheckBox("开启久坐关怀提醒");
        careCheck.setSelected(initialCareEnabled);
        careCheck.setOpaque(false);
        return careCheck;
    }

    /** "自动溜达"控件：开关（默认关闭），开启后空闲时鲸鱼娘才会左右跑动。 */
    private JComponent buildRoamEnabledControl() {
        roamCheck = new JCheckBox("开启自动溜达");
        roamCheck.setSelected(initialRoamEnabled);
        roamCheck.setOpaque(false);
        return roamCheck;
    }

    /** "溜达速度"控件：滑块 1~8，拖动实时预览，配右侧数值标签。 */
    private JComponent buildRoamControl() {
        roamSlider = new JSlider(
                PetSettingsState.MIN_ROAM_SPEED,
                PetSettingsState.MAX_ROAM_SPEED,
                initialRoamSpeed);
        roamSlider.setMajorTickSpacing(1);
        roamSlider.setMinorTickSpacing(1);
        roamSlider.setPaintTicks(true);
        roamSlider.setSnapToTicks(true);
        roamValue = new JLabel(String.valueOf(initialRoamSpeed));
        roamValue.setPreferredSize(new Dimension(40, roamValue.getPreferredSize().height));
        roamSlider.addChangeListener(e -> {
            roamValue.setText(String.valueOf(roamSlider.getValue()));
            applyRoamPreview();
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.add(roamSlider);
        row.add(roamValue);
        return row;
    }

    /** "出发间隔"控件：数字输入框（秒，1~30），回车/失焦实时预览溜达节奏。 */
    private JComponent buildIntervalControl() {
        intervalField = new JTextField(String.valueOf(initialRoamInterval), 5);
        intervalField.setHorizontalAlignment(JTextField.RIGHT);
        Runnable commit = this::applyIntervalPreview;
        intervalField.addActionListener(e -> commit.run());
        intervalField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commit.run();
            }
        });
        JLabel unit = new JLabel("秒");
        unit.setForeground(DESC_TEXT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.add(intervalField);
        row.add(unit);
        return row;
    }

    /** "关怀间隔"控件：数字输入框（分钟，1~600）。 */
    private JComponent buildCareIntervalControl() {
        careIntervalField = new JTextField(String.valueOf(initialCareInterval), 5);
        careIntervalField.setHorizontalAlignment(JTextField.RIGHT);
        careIntervalField.addActionListener(e -> { /* 关怀间隔按时生效，无需额外预览 */ });
        careIntervalField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // 仅在失焦时规整显示，避免输入过程中跳变
                int v = parseField(careIntervalField,
                        PetSettingsState.MIN_CARE_INTERVAL_MIN, PetSettingsState.MAX_CARE_INTERVAL_MIN,
                        PetSettingsState.DEFAULT_CARE_INTERVAL_MIN);
                careIntervalField.setText(String.valueOf(v));
            }
        });
        JLabel unit = new JLabel("分钟");
        unit.setForeground(DESC_TEXT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.add(careIntervalField);
        row.add(unit);
        return row;
    }

    /** "喝水提醒"控件：开关（默认关闭）。 */
    private JComponent buildWaterControl() {
        waterCheck = new JCheckBox("开启喝水提醒");
        waterCheck.setSelected(initialWaterEnabled);
        waterCheck.setOpaque(false);
        return waterCheck;
    }

    /** "喝水间隔"控件：数字输入框（分钟，1~600）。 */
    private JComponent buildWaterIntervalControl() {
        waterIntervalField = new JTextField(String.valueOf(initialWaterInterval), 5);
        waterIntervalField.setHorizontalAlignment(JTextField.RIGHT);
        waterIntervalField.addActionListener(e -> { /* 喝水间隔按时生效，无需额外预览 */ });
        waterIntervalField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                int v = parseField(waterIntervalField,
                        PetSettingsState.MIN_WATER_INTERVAL_MIN, PetSettingsState.MAX_WATER_INTERVAL_MIN,
                        PetSettingsState.DEFAULT_WATER_INTERVAL_MIN);
                waterIntervalField.setText(String.valueOf(v));
            }
        });
        JLabel unit = new JLabel("分钟");
        unit.setForeground(DESC_TEXT);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.setOpaque(false);
        row.add(waterIntervalField);
        row.add(unit);
        return row;
    }

    /** 从数字输入框解析并 clamp 为整数（非法输入回退 fallback）。 */
    private static int parseField(JTextField field, int min, int max, int fallback) {
        try {
            int v = Integer.parseInt(field.getText().trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** "回到原位"控件：一个「一键归位」按钮，点击让鲸鱼娘回右下角老家。 */
    private JComponent buildReturnHomeControl() {
        JButton btn = new JButton("一键归位");
        btn.setOpaque(false);
        btn.addActionListener(e -> returnHome());
        return btn;
    }

    /** 把鲸鱼娘送回默认位置（同步把「显示宠物」开关置为开）。 */
    private void returnHome() {
        visibleCombo.setSelectedItem(BooleanOption.ON);
        PetFrame frame = currentFrame();
        if (frame != null) frame.returnToHome();
    }

    // === 实时预览动作 ===

    /**
     * 切换宠物主题的实时预览：直通 PetFrame 换肤（不走广播链），同时刷新设置页配色。
     * frame 未创建时仅更新运行时服务，等 buildFrame 时自然生效。
     */
    private void applyThemePreview(PetTheme selected) {
        service.setTheme(selected);
        applyPalette(paletteOf(selected));
        PetFrame frame = currentFrame();
        if (frame != null) {
            frame.applyThemePreview(selected);
        }
    }

    private void applySettingsPreview() {
        PetFrame frame = currentFrame();
        if (frame != null) {
            frame.applySettings(sizeSlider.getValue(), opacitySlider.getValue());
        }
    }

    /** 溜达速度实时预览：拖动滑块把速度透传给桌宠，立即生效、不落盘。 */
    private void applyRoamPreview() {
        PetFrame frame = currentFrame();
        if (frame != null) {
            frame.setRoamSpeedPreview(roamSlider.getValue());
        }
    }

    /** 出发间隔实时预览：输入框回车/失焦时把间隔透传给桌宠，立即生效、不落盘。 */
    private void applyIntervalPreview() {
        int v = parseField(intervalField,
                PetSettingsState.MIN_ROAM_INTERVAL_SEC, PetSettingsState.MAX_ROAM_INTERVAL_SEC,
                PetSettingsState.DEFAULT_ROAM_INTERVAL_SEC);
        intervalField.setText(String.valueOf(v)); // 规整非法输入
        PetFrame frame = currentFrame();
        if (frame != null) {
            frame.setRoamIntervalPreview(v);
        }
    }

    private void applyVisiblePreview() {
        PetFrame frame = currentFrame();
        if (frame == null) return;
        if (visibleCombo.getSelectedItem() == BooleanOption.ON) {
            frame.unhide();
        } else {
            frame.hide();
        }
    }

    // === 恢复默认 ===

    private void setSize(int value) {
        sizeSlider.setValue(value);
        sizeValue.setText(value + "%");
        applySettingsPreview();
    }

    private void setOpacity(int value) {
        opacitySlider.setValue(value);
        opacityValue.setText(value + "%");
        applySettingsPreview();
    }

    private void setTheme(PetTheme theme) {
        themeCombo.setSelectedItem(theme); // 触发 ItemListener → applyThemePreview
        applyThemePreview(theme);
    }

    private void setVisible(boolean shown) {
        visibleCombo.setSelectedItem(shown ? BooleanOption.ON : BooleanOption.OFF);
        applyVisiblePreview();
    }

    private void setDecorations(boolean enabled) {
        decorationsCombo.setSelectedItem(enabled ? BooleanOption.ON : BooleanOption.OFF);
        service.setShowDecorations(enabled);
    }

    private void setCareEnabled(boolean enabled) {
        careCheck.setSelected(enabled);
    }

    private void setRoamEnabled(boolean enabled) {
        roamCheck.setSelected(enabled);
    }

    /** 把溜达速度恢复为默认值（"恢复默认"链接回调），并实时预览。 */
    private void setRoamSpeed(int value) {
        roamSlider.setValue(value);
        roamValue.setText(String.valueOf(value));
        applyRoamPreview();
    }

    /** 把出发间隔恢复为默认值（"恢复默认"链接回调），并实时预览。 */
    private void setInterval(int value) {
        intervalField.setText(String.valueOf(value));
        applyIntervalPreview();
    }

    /** 把关怀间隔恢复为默认值。 */
    private void setCareInterval(int value) {
        careIntervalField.setText(String.valueOf(value));
    }

    /** 把喝水提醒间隔恢复为默认值。 */
    private void setWaterInterval(int value) {
        waterIntervalField.setText(String.valueOf(value));
    }

    private void setWaterEnabled(boolean enabled) {
        waterCheck.setSelected(enabled);
    }

    @Override
    public boolean isModified() {
        if (state == null || sizeSlider == null) return false;
        int interval = parseField(intervalField,
                PetSettingsState.MIN_ROAM_INTERVAL_SEC, PetSettingsState.MAX_ROAM_INTERVAL_SEC,
                PetSettingsState.DEFAULT_ROAM_INTERVAL_SEC);
        int careInterval = parseField(careIntervalField,
                PetSettingsState.MIN_CARE_INTERVAL_MIN, PetSettingsState.MAX_CARE_INTERVAL_MIN,
                PetSettingsState.DEFAULT_CARE_INTERVAL_MIN);
        int waterInterval = parseField(waterIntervalField,
                PetSettingsState.MIN_WATER_INTERVAL_MIN, PetSettingsState.MAX_WATER_INTERVAL_MIN,
                PetSettingsState.DEFAULT_WATER_INTERVAL_MIN);
        return sizeSlider.getValue() != state.getSizePercent()
                || opacitySlider.getValue() != state.getOpacityPercent()
                || themeCombo.getSelectedItem() != state.theme()
                || (visibleCombo.getSelectedItem() == BooleanOption.ON) == state.isStartHidden()
                || (decorationsCombo.getSelectedItem() == BooleanOption.ON) != state.isShowDecorations()
                || careCheck.isSelected() != state.isCareEnabled()
                || roamCheck.isSelected() != state.isRoamEnabled()
                || roamSlider.getValue() != state.getRoamSpeed()
                || interval != state.getRoamIntervalSec()
                || careInterval != state.getCareIntervalMin()
                || waterCheck.isSelected() != state.isWaterEnabled()
                || waterInterval != state.getWaterIntervalMin();
    }

    @Override
    public void apply() {
        if (state == null || sizeSlider == null) return;
        state.setSizePercent(sizeSlider.getValue());
        state.setOpacityPercent(opacitySlider.getValue());
        Object selectedTheme = themeCombo.getSelectedItem();
        if (selectedTheme instanceof PetTheme theme) {
            state.setThemeName(theme.name());
        }
        state.setStartHidden(visibleCombo.getSelectedItem() != BooleanOption.ON);
        state.setShowDecorations(decorationsCombo.getSelectedItem() == BooleanOption.ON);
        state.setCareEnabled(careCheck.isSelected());
        state.setRoamEnabled(roamCheck.isSelected());
        state.setRoamSpeed(roamSlider.getValue());
        state.setRoamIntervalSec(parseField(intervalField,
                PetSettingsState.MIN_ROAM_INTERVAL_SEC, PetSettingsState.MAX_ROAM_INTERVAL_SEC,
                PetSettingsState.DEFAULT_ROAM_INTERVAL_SEC));
        state.setCareIntervalMin(parseField(careIntervalField,
                PetSettingsState.MIN_CARE_INTERVAL_MIN, PetSettingsState.MAX_CARE_INTERVAL_MIN,
                PetSettingsState.DEFAULT_CARE_INTERVAL_MIN));
        state.setWaterEnabled(waterCheck.isSelected());
        state.setWaterIntervalMin(parseField(waterIntervalField,
                PetSettingsState.MIN_WATER_INTERVAL_MIN, PetSettingsState.MAX_WATER_INTERVAL_MIN,
                PetSettingsState.DEFAULT_WATER_INTERVAL_MIN));

        service.setTheme(state.theme());
        service.setShowDecorations(state.isShowDecorations());
        PetFrame frame = currentFrame();
        if (frame != null) {
            frame.applyThemePreview(state.theme()); // 直通刷新，不依赖广播链
            frame.applySettings(state.getSizePercent(), state.getOpacityPercent());
            frame.clearPreviewOverride(); // 持久化值已与预览一致，回到"以持久化值为准"
        }
        captureInitials(); // 应用成功后，新的持久化值作为回滚基线
    }

    @Override
    public void reset() {
        if (state == null || sizeSlider == null) return;
        // 回滚预览到持久化值
        sizeSlider.setValue(state.getSizePercent());
        sizeValue.setText(state.getSizePercent() + "%");
        opacitySlider.setValue(state.getOpacityPercent());
        opacityValue.setText(state.getOpacityPercent() + "%");
        themeCombo.setSelectedItem(state.theme());
        visibleCombo.setSelectedItem(state.isStartHidden() ? BooleanOption.OFF : BooleanOption.ON);
        decorationsCombo.setSelectedItem(state.isShowDecorations() ? BooleanOption.ON : BooleanOption.OFF);
        careCheck.setSelected(state.isCareEnabled());
        roamCheck.setSelected(state.isRoamEnabled());
        roamSlider.setValue(state.getRoamSpeed());
        roamValue.setText(String.valueOf(state.getRoamSpeed()));
        intervalField.setText(String.valueOf(state.getRoamIntervalSec()));
        careIntervalField.setText(String.valueOf(state.getCareIntervalMin()));
        waterCheck.setSelected(state.isWaterEnabled());
        waterIntervalField.setText(String.valueOf(state.getWaterIntervalMin()));

        service.setTheme(state.theme());
        service.setShowDecorations(state.isShowDecorations());
        applyPalette(paletteOf(state.theme()));
        PetFrame frame = currentFrame();
        if (frame != null) {
            frame.applyThemePreview(state.theme()); // 直通刷新
            frame.applySettings(state.getSizePercent(), state.getOpacityPercent());
            frame.clearPreviewOverride();
            if (state.isStartHidden()) frame.hide(); else frame.unhide();
        }
        captureInitials();
    }

    @Override
    public void disposeUIResources() {
        // Cancel / 直接关闭设置页：把所有预览回滚到持久化值
        try {
            if (isModified()) reset();
        } catch (Throwable ignored) {
        }
        PetFrame frame = currentFrame();
        if (frame != null) frame.clearPreviewOverride();

        state = null;
        service = null;
        sizeSlider = null;
        opacitySlider = null;
        themeCombo = null;
        visibleCombo = null;
        decorationsCombo = null;
        careCheck = null;
        roamSlider = null;
        roamValue = null;
        roamCheck = null;
        intervalField = null;
        careIntervalField = null;
        waterCheck = null;
        waterIntervalField = null;
        cards.clear();
        themeLinks.clear();
        root = null;
        palette = null;
    }

    /** 取当前桌宠窗口；未创建时返回 null。 */
    private static @Nullable PetFrame currentFrame() {
        return PetStartupFrameHolder.current();
    }

    // === 主题配色 ===

    /** 每个主题在设置页里使用的配色方案（全部 JBColor：亮 / 暗 IDE 主题自动切换）。 */
    private record ThemePalette(Color accent, Color cardBg, Color cardBorder, Color link) {
    }

    /** 描述文字：主题自适应次级前景色，保证暗色主题下清晰可读。 */
    private static final JBColor DESC_TEXT = new JBColor(
            new Color(120, 120, 120), new Color(150, 157, 170));

    /** 原版 / 精致版共用海洋蓝配色（各带亮 / 暗两套值），避免「精致版」切换后设置页变成珊瑚橙。 */
    private static ThemePalette paletteOf(PetTheme theme) {
        return new ThemePalette(
                new JBColor(new Color(42, 100, 180), new Color(114, 166, 232)),      // 强调：海洋蓝
                new JBColor(new Color(239, 246, 253), new Color(52, 58, 66)),        // 卡片底
                new JBColor(new Color(197, 219, 242), new Color(76, 84, 96)),        // 卡片边框
                new JBColor(new Color(42, 100, 180), new Color(126, 176, 240)));     // 链接
    }

    /** 把整套配色刷到所有受影响的卡片与链接按钮上。 */
    private void applyPalette(ThemePalette next) {
        palette = next;
        for (CardPanel card : cards) card.applyPalette(next);
        for (JButton btn : themeLinks) btn.setForeground(next.link());
        if (root != null) root.repaint();
    }

    // === 卡片式 UI 辅助 ===

    private JPanel buildItem(String title, String description, JComponent control, Runnable restoreDefault) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setBorder(JBUI.Borders.empty(10, 14));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(JBColor.foreground());

        JPanel textPanel = new JPanel(new BorderLayout(0, 4));
        textPanel.setOpaque(false);
        textPanel.add(titleLabel, BorderLayout.NORTH);
        if (description != null && !description.isEmpty()) {
            JLabel descLabel = new JLabel(description);
            descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 12f));
            descLabel.setForeground(DESC_TEXT);
            textPanel.add(descLabel, BorderLayout.CENTER);
        }

        JButton restore = linkButton("恢复默认");
        restore.addActionListener(e -> {
            if (SwingUtilities.isEventDispatchThread()) {
                restoreDefault.run();
            } else {
                SwingUtilities.invokeLater(restoreDefault);
            }
        });

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 0, 12);
        row.add(textPanel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(0, 0, 0, 8);
        row.add(control, gbc);

        gbc.gridx = 2;
        gbc.insets = new Insets(0, 0, 0, 0);
        row.add(restore, gbc);

        return row;
    }

    private JButton linkButton(String text) {
        JButton btn = new JButton(text);
        btn.setUI(new BasicButtonUI());
        btn.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setForeground(palette != null ? palette.link()
                : new JBColor(new Color(42, 100, 180), new Color(126, 176, 240)));
        btn.setFont(btn.getFont().deriveFont(Font.PLAIN, 12f));
        btn.setFocusPainted(false);
        themeLinks.add(btn);
        return btn;
    }

    /** 圆角浅色卡片容器，配色随主题刷新。 */
    private final class CardPanel extends JPanel {
        private final JLabel titleLabel;
        private Color cardBg;
        private Color cardBorder;

        CardPanel(String title, String subtitle) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
            setBorder(JBUI.Borders.empty(16, 18));

            titleLabel = new JLabel(title);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
            titleLabel.setForeground(palette != null ? palette.accent() : JBColor.foreground());
            JLabel subLabel = new JLabel(subtitle);
            subLabel.setFont(subLabel.getFont().deriveFont(Font.PLAIN, 12f));
            subLabel.setForeground(DESC_TEXT);

            JPanel header = new JPanel(new BorderLayout(0, 4));
            header.setOpaque(false);
            header.add(titleLabel, BorderLayout.NORTH);
            header.add(subLabel, BorderLayout.CENTER);
            header.setBorder(JBUI.Borders.emptyBottom(12));
            add(header);

            cardBg = palette != null ? palette.cardBg() : new JBColor(
                    new Color(245, 246, 248), new Color(58, 62, 70));
            cardBorder = palette != null ? palette.cardBorder() : new JBColor(
                    new Color(220, 223, 228), new Color(78, 84, 94));
            cards.add(this);
        }

        void applyPalette(ThemePalette p) {
            this.cardBg = p.cardBg();
            this.cardBorder = p.cardBorder();
            this.titleLabel.setForeground(p.accent());
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(cardBg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(cardBorder);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            } finally {
                g2.dispose();
            }
        }
    }

    /** 开关下拉选项。 */
    private enum BooleanOption {
        ON("开"),
        OFF("关");

        private final String label;

        BooleanOption(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    /**
     * 对 {@link com.dsh.petwhale.startup.PetStartupActivity} 持有的全局窗口的间接访问。
     * 拆出独立小类是为了让本类不直接依赖 startup 包（保持 ui → state/resource 的依赖方向干净）。
     */
    private static final class PetStartupFrameHolder {
        private static PetFrame current() {
            return com.dsh.petwhale.startup.PetStartupActivity.currentFrame();
        }
    }
}
