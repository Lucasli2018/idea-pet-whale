package com.dsh.petwhale.resource;

import com.dsh.petwhale.state.PetAnimation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fail-closed parser for the dsh-pet {@pet.json} shape. Accepts both
 * v1 (no {@petManifestVersion}) and v2 ({petManifestVersion: 2})
 * manifests. On any structural error raises
 * {@IllegalStateException}; the loader treats that as fatal rather
 * than shipping a half-broken theme.
 *
 * <p>Hand-written JSON to keep zero third-party deps on the compile
 * path. The {@pet.json} files are tiny ({@code ~2KB} each) and follow
 * a stable schema, so a tiny bespoke parser is the right tool. The
 * parser is fail-closed: malformed input throws rather than returning
 * a partially-populated object.
 */
public final class PetManifestParser {

    private PetManifestParser() {
    }

    @NotNull
    public static PetManifest parse(@NotNull String classpathResource) {
        try (InputStream in = PetManifestParser.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IllegalStateException("missing pet manifest at " + classpathResource);
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            JsonValue root = new JsonReader(sb.toString()).readValue();
            if (!(root instanceof JsonObject obj)) {
                throw new IllegalStateException("pet manifest root must be an object");
            }
            return fromJson(obj);
        } catch (IOException ex) {
            throw new IllegalStateException("failed reading " + classpathResource, ex);
        }
    }

    @NotNull
    static PetManifest fromJson(@NotNull JsonObject root) {
        String id = stringField(root, "id", null);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("pet manifest missing required field 'id'");
        }
        String displayName = stringField(root, "displayName", id);
        String description = stringField(root, "description", null);

        // Sprite atlas path — for v2 manifests lives inside the renderer block.
        String spritesheetPath = "spritesheet.webp";
        if (root.containsKey("petManifestVersion")) {
            JsonObject sprite2d = objectField(root, "sprite2d");
            if (sprite2d != null) {
                String nested = stringField(sprite2d, "spritesheetPath", null);
                if (nested != null) spritesheetPath = nested;
            }
        } else {
            String legacy = stringField(root, "spritesheetPath", null);
            if (legacy != null) spritesheetPath = legacy;
        }

        int[] frames = PetManifest.DEFAULT_FRAMES.clone();
        JsonValue framesRaw = root.get("frames");
        if (framesRaw instanceof JsonArray arr) {
            if (arr.size() != PetManifest.ROWS) {
                throw new IllegalStateException(
                        "frames array length must be " + PetManifest.ROWS + ", got " + arr.size());
            }
            for (int i = 0; i < arr.size(); i++) {
                JsonValue v = arr.get(i);
                if (!(v instanceof JsonNumber n)) {
                    throw new IllegalStateException("frames[" + i + "] must be a number");
                }
                frames[i] = n.intValue();
            }
        }

        Map<PetAnimation, int[]> durations = new EnumMap<>(PetAnimation.class);
        JsonValue tracksRaw = root.get("tracks");
        if (tracksRaw instanceof JsonObject tracks) {
            for (PetAnimation animation : PetAnimation.values()) {
                JsonValue trackRaw = tracks.get(animation.name().toLowerCase());
                if (!(trackRaw instanceof JsonObject track)) continue;
                JsonValue durationsRaw = track.get("durations");
                if (!(durationsRaw instanceof JsonArray durArr)) continue;
                int[] dur = new int[durArr.size()];
                for (int i = 0; i < durArr.size(); i++) {
                    JsonValue v = durArr.get(i);
                    if (!(v instanceof JsonNumber n)) {
                        throw new IllegalStateException(
                                "tracks." + animation.name().toLowerCase() + ".durations[" + i + "] must be a number");
                    }
                    dur[i] = n.intValue();
                }
                durations.put(animation, dur);
            }
        }

        return new PetManifest(id, displayName, description, spritesheetPath, frames, durations);
    }

    @Nullable
    private static String stringField(JsonObject obj, String key, @Nullable String fallback) {
        JsonValue v = obj.get(key);
        if (v == null) return fallback;
        if (v instanceof JsonString s) return s.value();
        return v.toString();
    }

    @Nullable
    private static JsonObject objectField(JsonObject obj, String key) {
        JsonValue v = obj.get(key);
        return v instanceof JsonObject o ? o : null;
    }

    // -----------------------------------------------------------------
    // Tiny JSON reader — handles the subset we use: objects, arrays,
    // strings (with \" \n \t \\\\ escapes), numbers, true/false/null.
    // -----------------------------------------------------------------

    /** Discriminated union of every JSON value our parser can produce. */
    abstract static class JsonValue {
    }

    static final class JsonObject extends JsonValue {
        private final java.util.LinkedHashMap<String, JsonValue> entries = new java.util.LinkedHashMap<>();
        void put(String key, JsonValue value) { entries.put(key, value); }
        boolean containsKey(String key) { return entries.containsKey(key); }
        JsonValue get(String key) { return entries.get(key); }
        @Override public String toString() { return entries.toString(); }
    }

    static final class JsonArray extends JsonValue {
        private final List<JsonValue> items = new ArrayList<>();
        void add(JsonValue v) { items.add(v); }
        int size() { return items.size(); }
        JsonValue get(int i) { return items.get(i); }
        @Override public String toString() { return items.toString(); }
    }

    static final class JsonString extends JsonValue {
        private final String value;
        JsonString(String value) { this.value = value; }
        String value() { return value; }
        @Override public String toString() { return "\"" + value + "\""; }
    }

    static final class JsonNumber extends JsonValue {
        private final double value;
        JsonNumber(double value) { this.value = value; }
        int intValue() { return (int) value; }
        @Override public String toString() { return Double.toString(value); }
    }

    static final class JsonBoolean extends JsonValue {
        private final boolean value;
        JsonBoolean(boolean value) { this.value = value; }
        @Override public String toString() { return Boolean.toString(value); }
    }

    static final class JsonNull extends JsonValue {
        @Override public String toString() { return "null"; }
    }

    /** Recursive-descent JSON reader. Throws {@IllegalStateException} on malformed input. */
    static final class JsonReader {
        private final String src;
        private int pos;

        JsonReader(String src) {
            this.src = src;
            this.pos = 0;
        }

        JsonValue readValue() {
            skipWhitespace();
            if (pos >= src.length()) throw new IllegalStateException("unexpected end of JSON input");
            char c = src.charAt(pos);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return new JsonString(readString());
            if (c == '-' || (c >= '0' && c <= '9')) return readNumber();
            if (startsWith("true")) { pos += 4; return new JsonBoolean(true); }
            if (startsWith("false")) { pos += 5; return new JsonBoolean(false); }
            if (startsWith("null")) { pos += 4; return new JsonNull(); }
            throw new IllegalStateException("unexpected character '" + c + "' at position " + pos);
        }

        private JsonObject readObject() {
            expect('{');
            JsonObject obj = new JsonObject();
            skipWhitespace();
            if (peek() == '}') { pos++; return obj; }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                JsonValue value = readValue();
                obj.put(key, value);
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return obj; }
                throw new IllegalStateException("expected ',' or '}' at position " + pos);
            }
        }

        private JsonArray readArray() {
            expect('[');
            JsonArray arr = new JsonArray();
            skipWhitespace();
            if (peek() == ']') { pos++; return arr; }
            while (true) {
                JsonValue v = readValue();
                arr.add(v);
                skipWhitespace();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return arr; }
                throw new IllegalStateException("expected ',' or ']' at position " + pos);
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == '"') { pos++; return sb.toString(); }
                if (c == '\\') {
                    if (pos + 1 >= src.length()) throw new IllegalStateException("dangling escape");
                    char esc = src.charAt(pos + 1);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        default: throw new IllegalStateException("unknown escape \\" + esc);
                    }
                    pos += 2;
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            throw new IllegalStateException("unterminated string");
        }

        private JsonNumber readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    pos++;
                } else {
                    break;
                }
            }
            String token = src.substring(start, pos);
            try {
                return new JsonNumber(Double.parseDouble(token));
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("invalid number '" + token + "'");
            }
        }

        private boolean startsWith(String literal) {
            return src.regionMatches(pos, literal, 0, literal.length());
        }

        private char peek() {
            if (pos >= src.length()) throw new IllegalStateException("unexpected end at " + pos);
            return src.charAt(pos);
        }

        private void expect(char c) {
            if (peek() != c) throw new IllegalStateException("expected '" + c + "' at position " + pos);
            pos++;
        }

        private void skipWhitespace() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') pos++;
                else break;
            }
        }
    }
}