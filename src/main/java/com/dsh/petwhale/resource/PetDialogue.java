package com.dsh.petwhale.resource;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 台词库：从 {@code /dialogue/whale.json} 加载分组台词，供气泡随机取用。
 *
 * <p>JSON 结构（分组名 → 台词数组）：
 * <pre>
 * {
 *   "click":  ["嘿嘿，干嘛戳我~", "怎么啦？"],
 *   "rapid":  ["别、别再戳啦！要生气了哦！"],
 *   "greet":  ["今天也要加油写代码哦~"]
 * }
 * </pre>
 *
 * <p><b>解析用平台捆绑的 Gson</b>（IntelliJ Platform 自带 {@code com.google.gson}，
 * 不新增第三方依赖）。解析策略全部"失败即静默降级"：
 * <ul>
 *   <li>资源缺失 / JSON 非法 / 结构不对 → 得到空库，{@link #random} 返回 {@code null}，
 *       调用方跳过气泡，桌宠行为不受影响</li>
 *   <li>台词库是懒加载单例，首次 {@link #random} 时才做一次 IO</li>
 * </ul>
 */
public final class PetDialogue {

    /** 台词资源 classpath 路径 */
    static final String DIALOGUE_RESOURCE = "/dialogue/whale.json";

    /** 懒加载缓存；null = 尚未加载，empty map = 加载过但内容不可用 */
    private static volatile Map<String, List<String>> lines;

    /** 私有构造，禁止实例化。 */
    private PetDialogue() {
    }

    /**
     * 从指定分组随机取一条台词。
     *
     * @param group 分组名（如 {@code click} / {@code rapid} / {@code greet}）
     * @return 随机台词；分组不存在、台词库不可用、分组为空时返回 {@code null}
     */
    @Nullable
    public static String random(@NotNull String group) {
        List<String> pool = group(group);
        if (pool.isEmpty()) return null;
        int idx = ThreadLocalRandom.current().nextInt(pool.size());
        String line = pool.get(idx);
        return line == null || line.isBlank() ? null : line.trim();
    }

    /**
     * 取指定分组的台词池（副本语义的只读视图；不可用时返回空列表）。
     * 包级可见供单元测试断言。
     */
    @NotNull
    static List<String> group(@NotNull String name) {
        Map<String, List<String>> data = load();
        List<String> pool = data.get(name);
        return pool == null ? List.of() : pool;
    }

    /** 懒加载台词库；任何失败都降级为空库，绝不抛出。 */
    @NotNull
    private static Map<String, List<String>> load() {
        Map<String, List<String>> snapshot = lines;
        if (snapshot != null) return snapshot;
        synchronized (PetDialogue.class) {
            if (lines == null) {
                lines = parse(readResource());
            }
            return lines;
        }
    }

    /** 读 classpath 资源为字符串；缺失返回 {@code null}。 */
    @Nullable
    private static String readResource() {
        try (InputStream in = PetDialogue.class.getResourceAsStream(DIALOGUE_RESOURCE)) {
            if (in == null) return null;
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder(1024);
            char[] buf = new char[512];
            int n;
            while ((n = reader.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gson 解析。仅接受「字符串数组 → 字符串列表」的扁平结构；
     * 深层嵌套 / 类型不符 / null 输入一律返回空库。
     * 包级可见供单元测试直连。
     */
    @NotNull
    static Map<String, List<String>> parse(@Nullable String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Type type = new TypeToken<Map<String, List<String>>>() { }.getType();
            Map<String, List<String>> parsed = new Gson().fromJson(json, type);
            if (parsed == null) return Map.of();
            // 清洗：丢掉 null 分组和 null/空白台词项，产出不可变副本
            Map<String, List<String>> cleaned = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : parsed.entrySet()) {
                String group = entry.getKey();
                List<String> src = entry.getValue();
                if (group == null || src == null) continue;
                List<String> items = new java.util.ArrayList<>(src.size());
                for (String item : src) {
                    if (item != null && !item.isBlank()) items.add(item.trim());
                }
                if (!items.isEmpty()) cleaned.put(group, List.copyOf(items));
            }
            return Map.copyOf(cleaned);
        } catch (Exception e) {
            return Map.of();
        }
    }
}