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
 * Hover panel anchored below the pet — holds the theme switcher and the
 * Hide button. Light-weight: a single rounded rect with two short
 * labels and two buttons. Listens back to {@PetStateService} only for
 * theme switching; the hide action is local.
 */
public final class PetHoverPanel extends JPanel {

    private final PetFrame frame;
    private final PetStateService service;

    public PetHoverPanel(@NotNull PetFrame frame, @NotNull PetStateService service) {
        this.frame = frame;
        this.service = service;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setBackground(new Color(28, 32, 38, 235));
        setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));

        JLabel title = new JLabel("鲸鱼娘");
        title.setForeground(Color.WHITE);
        add(title);

        JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
        buttons.setOpaque(false);

        JButton themeBtn = new JButton(themeLabel());
        themeBtn.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            PetTheme next = service.theme() == PetTheme.WHALE
                    ? PetTheme.WHALE_REFINED
                    : PetTheme.WHALE;
            service.setTheme(next);
            themeBtn.setText(themeLabel());
        }));

        JButton hideBtn = new JButton("隐藏");
        hideBtn.addActionListener(e -> SwingUtilities.invokeLater(frame::hide));

        buttons.add(themeBtn);
        buttons.add(hideBtn);
        add(buttons);

        setSize(new Dimension(180, 70));
    }

    private String themeLabel() {
        return service.theme() == PetTheme.WHALE
                ? "精致版"
                : "原版";
    }
}