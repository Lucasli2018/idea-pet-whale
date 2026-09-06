package com.dsh.petwhale.ui;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link PetBubble#clip(String)} 单元测试（纯静态函数，不触碰 Swing 窗体）。
 *
 * <p>覆盖：短文本只清洗空白、超长截断补省略号、null 兜底空串。</p>
 */
public class PetBubbleClipTest {

    @Test
    public void clip_shortText_trimmedOnly() {
        assertEquals("你好", PetBubble.clip("  你好  "));
    }

    @Test
    public void clip_exactLimit_unchanged() {
        String text = "一".repeat(PetBubble.MAX_TEXT_CHARS);
        assertEquals(text, PetBubble.clip(text));
    }

    @Test
    public void clip_longText_truncatedWithEllipsis() {
        String longText = "一".repeat(40);
        String clipped = PetBubble.clip(longText);
        assertEquals(PetBubble.MAX_TEXT_CHARS + 1, clipped.length());
        assertTrue(clipped.endsWith("…"));
    }

    @Test
    public void clip_nullOrEmpty_returnsEmpty() {
        assertEquals("", PetBubble.clip(null));
        assertEquals("", PetBubble.clip("   "));
    }
}