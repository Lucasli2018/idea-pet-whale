package com.dsh.petwhale.resource;

import com.dsh.petwhale.resource.PetManifestParser.JsonArray;
import com.dsh.petwhale.resource.PetManifestParser.JsonNumber;
import com.dsh.petwhale.resource.PetManifestParser.JsonObject;
import com.dsh.petwhale.resource.PetManifestParser.JsonReader;
import com.dsh.petwhale.state.PetAnimation;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Manifest 解析 + JSON reader 单元测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>解析内置两个主题（{@link PetTheme#WHALE} 原版 / {@link PetTheme#WHALE_REFINED} 精致版）</li>
 *   <li>{@link PetManifest#frameCount} 越界抛 {@link IndexOutOfBoundsException}</li>
 *   <li>{@link PetManifest#defaultDurationsForRow} 用 idle 节奏兜底</li>
 *   <li>解析器拒绝缺失的必填字段（fail-closed）</li>
 *   <li>v1 兼容（无 {@code petManifestVersion} 字段）能正确解析</li>
 *   <li>{@link JsonReader} 解析嵌套对象、拒绝畸形输入</li>
 * </ul>
 */
public class PetManifestTest {

    /** 原版主题字段正确性。 */
    @Test
    public void parsesWhaleOriginalManifest() {
        PetManifest m = PetManifestParser.parse(PetTheme.WHALE.manifestResource());
        assertEquals("whale-girl", m.id());
        assertEquals("鲸鱼娘（原版）", m.displayName());
        assertEquals("spritesheet.webp", m.spritesheetPath());
        assertEquals(6, m.frameCount(0));
        assertEquals(8, m.frameCount(1));
        assertEquals(6, m.frameCount(7));
        int[] idle = m.durations(PetAnimation.IDLE);
        assertEquals(6, idle.length);
        assertEquals(500, idle[0]);
    }

    /** 精致版主题字段正确性。 */
    @Test
    public void parsesWhaleRefinedManifest() {
        PetManifest m = PetManifestParser.parse(PetTheme.WHALE_REFINED.manifestResource());
        assertEquals("whale-girl-refined", m.id());
        assertEquals("鲸鱼娘（精致版）", m.displayName());
    }

    /** 行号越界抛异常（保护内存安全）。 */
    @Test
    public void frameCountThrowsForOutOfRangeRow() {
        PetManifest m = PetManifestParser.parse(PetTheme.WHALE.manifestResource());
        try {
            m.frameCount(99);
            fail("expected IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException expected) {
            // pass
        }
    }

    /** 兜底时长表应等于行帧数。 */
    @Test
    public void defaultDurationsForRowFillsShortFrames() {
        PetManifest m = PetManifestParser.parse(PetTheme.WHALE.manifestResource());
        int[] d = m.defaultDurationsForRow(0);
        assertEquals(6, d.length);
    }

    /** 解析器拒绝缺 id 的清单（fail-closed）。 */
    @Test
    public void parserRejectsMissingId() {
        JsonObject root = new JsonObject();
        root.put("displayName", new com.dsh.petwhale.resource.PetManifestParser.JsonString("no id"));
        try {
            PetManifestParser.fromJson(root);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("id"));
        }
    }

    /** v1 兼容：清单无 petManifestVersion 字段也应能解析。 */
    @Test
    public void parserAcceptsLegacyV1FlatFrames() {
        JsonObject root = new JsonObject();
        root.put("id", new com.dsh.petwhale.resource.PetManifestParser.JsonString("legacy-pet"));
        root.put("displayName", new com.dsh.petwhale.resource.PetManifestParser.JsonString("Legacy"));

        JsonArray frames = new JsonArray();
        for (int i = 0; i < PetManifest.ROWS; i++) frames.add(new JsonNumber(8));
        root.put("frames", frames);

        JsonObject tracks = new JsonObject();
        JsonObject idle = new JsonObject();
        JsonArray idleDur = new JsonArray();
        idleDur.add(new JsonNumber(300));
        idleDur.add(new JsonNumber(300));
        idle.put("durations", idleDur);
        tracks.put("idle", idle);
        root.put("tracks", tracks);

        PetManifest m = PetManifestParser.fromJson(root);
        assertEquals("legacy-pet", m.id());
        int[] d = m.durations(PetAnimation.IDLE);
        assertEquals(2, d.length);
        assertEquals(300, d[0]);
    }

    /** JsonReader 解析嵌套对象 + 数字。 */
    @Test
    public void jsonReaderParsesSampleString() {
        JsonObject root = (JsonObject) new JsonReader(
                "{\"id\":\"abc\",\"frames\":[1,2,3],\"nested\":{\"k\":\"v\"}}"
        ).readValue();
        assertEquals("abc", ((com.dsh.petwhale.resource.PetManifestParser.JsonString) root.get("id")).value());
        JsonArray frames = (JsonArray) root.get("frames");
        assertEquals(3, frames.size());
        assertEquals(1, ((JsonNumber) frames.get(0)).intValue());
        JsonObject nested = (JsonObject) root.get("nested");
        assertEquals("v", ((com.dsh.petwhale.resource.PetManifestParser.JsonString) nested.get("k")).value());
    }

    /** JsonReader 拒绝畸形输入（无引号的字符串）。 */
    @Test
    public void jsonReaderRejectsMalformedInput() {
        try {
            new JsonReader("{ \"id\": abc }").readValue();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // pass
        }
    }
}