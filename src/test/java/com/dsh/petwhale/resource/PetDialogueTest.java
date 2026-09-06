package com.dsh.petwhale.resource;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * {@link PetDialogue} 单元测试。
 *
 * <p>覆盖契约：
 * <ul>
 *   <li>JSON 解析：合法结构 → 分组映射；非法/空/null 输入 → 空库（静默降级）</li>
 *   <li>资源完整性：内置 dialogue/whale.json 至少含 greet/click/rapid 三个非空分组</li>
 *   <li>{@link PetDialogue#random}：有词返回非空、无词返回 null</li>
 * </ul>
 */
public class PetDialogueTest {

    // === parse: 合法输入 ===

    @Test
    public void parse_validJson_mapsGroups() {
        Map<String, List<String>> parsed = PetDialogue.parse(
                "{\"click\":[\"a\",\"b\"],\"rapid\":[\"c\"]}");
        assertEquals(List.of("a", "b"), parsed.get("click"));
        assertEquals(List.of("c"), parsed.get("rapid"));
        assertEquals(2, parsed.size());
    }

    @Test
    public void parse_nullArrayItems_filteredOut() {
        Map<String, List<String>> parsed = PetDialogue.parse(
                "{\"click\":[\"a\",null]}");
        assertEquals(List.of("a"), parsed.get("click"));
    }

    // === parse: 非法输入全部静默降级为空库 ===

    @Test
    public void parse_malformedJson_returnsEmpty() {
        assertTrue(PetDialogue.parse("{not valid json").isEmpty());
    }

    @Test
    public void parse_nullOrBlank_returnsEmpty() {
        assertTrue(PetDialogue.parse(null).isEmpty());
        assertTrue(PetDialogue.parse("").isEmpty());
        assertTrue(PetDialogue.parse("   ").isEmpty());
    }

    @Test
    public void parse_wrongShape_returnsEmpty() {
        // 数字不是「分组 → 字符串数组」结构
        assertTrue(PetDialogue.parse("123").isEmpty());
        // 深层嵌套结构也不接受
        assertTrue(PetDialogue.parse("{\"a\":{\"b\":[\"c\"]}}").isEmpty());
    }

    // === 内置资源完整性 ===

    @Test
    public void bundledResource_hasNonEmptyCoreGroups() {
        List<String> greet = PetDialogue.group("greet");
        List<String> click = PetDialogue.group("click");
        List<String> rapid = PetDialogue.group("rapid");
        assertFalse("greet 台词组不能为空", greet.isEmpty());
        assertFalse("click 台词组不能为空", click.isEmpty());
        assertFalse("rapid 台词组不能为空", rapid.isEmpty());
        for (String line : greet) assertFalse(line.isBlank());
        for (String line : click) assertFalse(line.isBlank());
        for (String line : rapid) assertFalse(line.isBlank());
    }

    // === random ===

    @Test
    public void random_knownGroup_returnsNonBlank() {
        String line = PetDialogue.random("click");
        assertNotNull(line);
        assertFalse(line.isBlank());
    }

    @Test
    public void random_unknownGroup_returnsNull() {
        assertNull(PetDialogue.random("no-such-group"));
        assertNull(PetDialogue.random(""));
    }
}
