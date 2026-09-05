package com.dsh.petwhale.resource;

import com.dsh.petwhale.resource.PetManifestParser.JsonArray;
import com.dsh.petwhale.resource.PetManifestParser.JsonNumber;
import com.dsh.petwhale.resource.PetManifestParser.JsonObject;
import com.dsh.petwhale.resource.PetManifestParser.JsonReader;
import com.dsh.petwhale.state.PetAnimation;
import org.junit.Test;

import static org.junit.Assert.*;

public class PetManifestTest {

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

    @Test
    public void parsesWhaleRefinedManifest() {
        PetManifest m = PetManifestParser.parse(PetTheme.WHALE_REFINED.manifestResource());
        assertEquals("whale-girl-refined", m.id());
        assertEquals("鲸鱼娘（精致版）", m.displayName());
    }

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

    @Test
    public void defaultDurationsForRowFillsShortFrames() {
        PetManifest m = PetManifestParser.parse(PetTheme.WHALE.manifestResource());
        int[] d = m.defaultDurationsForRow(0);
        assertEquals(6, d.length);
    }

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