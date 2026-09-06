package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetTheme;
import com.dsh.petwhale.state.PetSettingsState;
import com.dsh.petwhale.state.PetStateService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * 设置页：Settings → Appearance &amp; Behavior → Pet Whale 鲸鱼娘。
 *
 * <p>暴露三项核心配置：
 * <ul>
 *   <li>大小（缩放百分比滑条，{@value PetSettingsState#MIN_SIZE_PERCENT}~{@value PetSettingsState#MAX_SIZE_PERCENT}）</li>
 *   <li>不透明度（滑条，{@value PetSettingsState#MIN_OPACITY_PERCENT}~{@value PetSettingsState#MAX_OPACITY_PERCENT}）</li>
 *   <li>默认主题（下拉框：原版 / 精致版）</li>
 *   <li>启动时收起（复选框）</li>
 * </ul>
 *
 * <p>点击 Apply / OK 即写回 {@link PetSettingsState}，并立刻对当前桌宠窗口生效
 * （若窗口已创建）——不需要重启 IDE。</p>
 *
 * <p>布局纪律（UI 三不原则）：纵向 BoxLayout 自然堆叠 + 行间 gap，控件行内用
 * FlowLayout 左对齐，任何元素互不重叠。</p>
 */
public final class PetSettingsConfigurable implements Configurable {

    private PetSettingsState state;

    private JSlider sizeSlider;
    private JLabel sizeValue;
    private JSlider opacitySlider;
    private JLabel opacityValue;
    private ComboBox<PetTheme> themeCombo;
    private JCheckBox startHiddenCheck;

    @Override
    public @NlsContexts.ConfigurableName String getDisplayName() {
        return "Pet Whale 鲸鱼娘";
    }

    @Override
    public @Nullable JComponent createComponent() {
        state = ApplicationManager.getApplication().getService(PetSettingsState.class);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // === 大小 ===
        sizeSlider = new JSlider(
                PetSettingsState.MIN_SIZE_PERCENT,
                PetSettingsState.MAX_SIZE_PERCENT,
                state.getSizePercent());
        sizeSlider.setMajorTickSpacing(50);
        sizeSlider.setPaintTicks(true);
        sizeValue = new JLabel();
        sizeSlider.addChangeListener(e -> sizeValue.setText(sizeSlider.getValue() + "%"));
        sizeValue.setText(sizeSlider.getValue() + "%");
        panel.add(row("大小", sizeSlider, sizeValue));

        // === 不透明度 ===
        opacitySlider = new JSlider(
                PetSettingsState.MIN_OPACITY_PERCENT,
                PetSettingsState.MAX_OPACITY_PERCENT,
                state.getOpacityPercent());
        opacitySlider.setMajorTickSpacing(10);
        opacitySlider.setPaintTicks(true);
        opacityValue = new JLabel();
        opacitySlider.addChangeListener(e -> opacityValue.setText(opacitySlider.getValue() + "%"));
        opacityValue.setText(opacitySlider.getValue() + "%");
        panel.add(row("不透明度", opacitySlider, opacityValue));

        // === 主题 ===
        themeCombo = new ComboBox<>(PetTheme.values());
        themeCombo.setSelectedItem(state.theme());
        panel.add(row("主题", themeCombo));

        // === 启动时收起 ===
        startHiddenCheck = new JCheckBox("启动 IDE 时只显示\"召唤鲸鱼娘\"按钮");
        startHiddenCheck.setSelected(state.isStartHidden());
        panel.add(startHiddenCheck);

        return panel;
    }

    /** 一行控件：左对齐 FlowLayout，天然不重叠。 */
    private JPanel row(String label, JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel lbl = new JLabel(label);
        lbl.setPreferredSize(new Dimension(70, lbl.getPreferredSize().height));
        row.add(lbl);
        for (Component component : components) {
            row.add(component);
        }
        return row;
    }

    @Override
    public boolean isModified() {
        if (state == null || sizeSlider == null) return false;
        return sizeSlider.getValue() != state.getSizePercent()
                || opacitySlider.getValue() != state.getOpacityPercent()
                || themeCombo.getSelectedItem() != state.theme()
                || startHiddenCheck.isSelected() != state.isStartHidden();
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
        state.setStartHidden(startHiddenCheck.isSelected());

        // 实时生效：主题走状态服务广播；窗口几何走 PetFrame（可能尚未创建）
        PetStateService service = ApplicationManager.getApplication().getService(PetStateService.class);
        service.setTheme(state.theme());
        PetFrame frame = PetStartupFrameHolder.current();
        if (frame != null) {
            frame.applySettings(state.getSizePercent(), state.getOpacityPercent());
        }
    }

    @Override
    public void reset() {
        if (state == null || sizeSlider == null) return;
        sizeSlider.setValue(state.getSizePercent());
        opacitySlider.setValue(state.getOpacityPercent());
        themeCombo.setSelectedItem(state.theme());
        startHiddenCheck.setSelected(state.isStartHidden());
    }

    @Override
    public void disposeUIResources() {
        state = null;
        sizeSlider = null;
        opacitySlider = null;
        themeCombo = null;
        startHiddenCheck = null;
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
