package com.dsh.petwhale.ui;

import com.dsh.petwhale.resource.PetTheme;
import com.dsh.petwhale.state.PetStateService;
import org.jetbrains.annotations.NotNull;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;

/**
 * 悬停面板：桌宠右键时弹出。
 *
 * <p>布局（自上而下）：
 * <pre>
 *  ┌──────────────┐
 *  │ 鲸鱼娘        │  ← 标题
 *  │ ┌────┬────┐  │
 *  │ │精致版│隐藏│  │  ← 两个按钮（主题切换 + 隐藏）
 *  │ └────┴────┘  │
 *  └──────────────┘
 * </pre>
 *
 * <p>只有"主题切换"会回到 {@link PetStateService}（切换主题 + 触发广播），
 * "隐藏"按钮是纯本地操作（调用 {@link PetFrame#hide()}）。</p>
 *
 * <p>颜色：深蓝灰半透明（{@code (28,32,38,235)}），与 dsh-pet 的 hover 风格保持一致。</p>
 */
public final class PetHoverPanel extends JPanel {

    private final PetFrame frame;
    private final PetStateService service;

    public PetHoverPanel(@NotNull PetFrame frame, @NotNull PetStateService service) {
        this.frame = frame;
        this.service = service;
        // 纵向 BoxLayout：标题一行 + 按钮一行
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        // 半透明深色背景
        setBackground(new Color(28, 32, 38, 235));
        // 内边距 6,10,6,10
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        // === 标题 ===
        JLabel title = new JLabel("鲸鱼娘");
        title.setForeground(Color.WHITE);
        add(title);

        // === 按钮区（水平 1×2）） */
        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.setOpaque(false);

        // 主题切换按钮：点击切到另一套主题，按钮文字同步更新
        JButton themeBtn = new JButton(themeLabel());
        themeBtn.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            PetTheme next = service.theme() == PetTheme.WHALE
                    ? PetTheme.WHALE_REFINED
                    : PetTheme.WHALE;
            service.setTheme(next);
            themeBtn.setText(themeLabel());
        }));

        // 隐藏按钮：调用 PetFrame.hide()
        JButton hideBtn = new JButton("隐藏");
        hideBtn.addActionListener(e -> SwingUtilities.invokeLater(frame::hide));

        buttons.add(themeBtn);
        buttons.add(hideBtn);
        add(buttons);

        setSize(new Dimension(180, 70));
    }

    /**
     * 主题切换按钮上的文案：
     * <ul>
     *   <li>当前是原版 → 按钮显示 "精致版"（点击切到精致版）</li>
     *   <li>当前是精致版 → 按钮显示 "原版"（点击切回原版）</li>
     * </ul>
     */
    private String themeLabel() {
        return service.theme() == PetTheme.WHALE
                ? "精致版"
                : "原版";
    }
}