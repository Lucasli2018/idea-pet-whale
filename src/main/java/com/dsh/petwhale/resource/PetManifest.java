package com.dsh.petwhale.resource;

import com.dsh.petwhale.state.PetAnimation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 解析后的桌宠清单。镜像 dsh-pet 的 {@code pet.json} 结构（精灵图几何 + 每行帧数 + 每轨节奏）。
 * 两套内置主题（{@link PetTheme#WHALE} 原版、{@link PetTheme#WHALE_REFINED} 精致版）随
 * 资源目录一起分发。
 *
 * <p>本类刻意保持"瘦"——只持有不可变数据，没有任何 IO 或 Swing 引用。
 * 解析（fail-closed）由 {@link PetManifestParser} 负责；本类只是解析产物的载体。</p>
 */
public final class PetManifest {

    /** 单格宽度（像素），与原版鲸鱼娘精灵图一致 */
    public static final int CELL_WIDTH = 192;
    /** 单格高度（像素） */
    public static final int CELL_HEIGHT = 208;
    /** 每行帧数（精灵图横向最多容纳 8 帧） */
    public static final int COLUMNS = 8;
    /** 精灵图总行数（对应 9 个动画） */
    public static final int ROWS = 9;

    /** 默认每行帧数（来自 dsh-pet hatch-pet 契约）。
     *  顺序与 {@link PetAnimation} 一致：idle=6 / running-right=8 / running-left=8 /
     *  waving=4 / jumping=5 / failed=8 / waiting=6 / running=6 / review=6。 */
    public static final int[] DEFAULT_FRAMES = { 6, 8, 8, 4, 5, 8, 6, 6, 6 };

    /** 默认每轨帧时长（毫秒），与精致版 dsh-pet 资源一致。
     *  若 {@code pet.json} 中没显式定义某轨的 durations，则使用此表。 */
    public static final Map<PetAnimation, int[]> DEFAULT_DURATIONS;
    static {
        TreeMap<PetAnimation, int[]> map = new TreeMap<>();
        // 空闲呼吸：6 帧，每帧约 500-600ms，整体循环约 3 秒
        map.put(PetAnimation.IDLE,          new int[] { 500, 500, 600, 500, 500, 600 });
        // 左右跑动：8 帧，每帧约 300-400ms，节奏感更强
        map.put(PetAnimation.RUNNING_RIGHT, new int[] { 300, 300, 300, 300, 300, 300, 300, 400 });
        map.put(PetAnimation.RUNNING_LEFT,  new int[] { 300, 300, 300, 300, 300, 300, 300, 400 });
        // 挥手：4 帧，约 1.8 秒一个循环
        map.put(PetAnimation.WAVING,        new int[] { 450, 450, 450, 450 });
        // 跳跃：5 帧
        map.put(PetAnimation.JUMPING,       new int[] { 400, 400, 400, 450, 450 });
        // 失败：8 帧，前段快后段慢，体现"逐渐沮丧"
        map.put(PetAnimation.FAILED,        new int[] { 550, 550, 550, 600, 650, 700, 550, 550 });
        // 等待：6 帧
        map.put(PetAnimation.WAITING,       new int[] { 550, 550, 600, 550, 550, 600 });
        // 原地跑：6 帧，节奏比左右跑稍慢
        map.put(PetAnimation.RUNNING,       new int[] { 330, 330, 330, 330, 330, 400 });
        // 审视：6 帧，最慢，节奏平稳
        map.put(PetAnimation.REVIEW,        new int[] { 650, 650, 650, 650, 650, 650 });
        DEFAULT_DURATIONS = Collections.unmodifiableMap(map);
    }

    private final String id;
    private final String displayName;
    private final String description;
    private final String spritesheetPath;
    private final int[] frames;
    private final Map<PetAnimation, int[]> durations;

    /**
     * 构造一个不可变清单。
     *
     * @param id 主题 ID（来自 {@code pet.json.id}，如 "whale-girl"）
     * @param displayName 显示名（来自 {@code pet.json.displayName}，如 "鲸鱼娘（原版）"）
     * @param description 可选描述
     * @param spritesheetPath classpath 路径（来自 {@code pet.json.sprite2d.spritesheetPath}）
     * @param frames 每行帧数（长度必须是 {@link #ROWS}）
     * @param durations 每轨帧时长（可空；缺失时回退到 {@link #DEFAULT_DURATIONS}）
     */
    public PetManifest(
            @NotNull String id,
            @NotNull String displayName,
            @Nullable String description,
            @NotNull String spritesheetPath,
            @NotNull int[] frames,
            @NotNull Map<PetAnimation, int[]> durations
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.spritesheetPath = spritesheetPath;
        // 深拷贝，让外部修改传入数组不影响内部状态
        this.frames = frames.clone();
        this.durations = new HashMap<>();
        for (Map.Entry<PetAnimation, int[]> entry : durations.entrySet()) {
            this.durations.put(entry.getKey(), entry.getValue().clone());
        }
    }

    /** 主题 ID。 */
    @NotNull public String id() { return id; }
    /** 主题显示名。 */
    @NotNull public String displayName() { return displayName; }
    /** 主题描述（可空）。 */
    @Nullable public String description() { return description; }
    /** 精灵图 classpath 路径。 */
    @NotNull public String spritesheetPath() { return spritesheetPath; }

    /** 返回帧数数组的副本（每行多少帧）。 */
    @NotNull public int[] frames() { return frames.clone(); }

    /**
     * 取指定行（动画）的帧数。
     *
     * @param row 行号（{@code 0} 到 {@link #ROWS}-1）
     * @return 该行的帧数
     * @throws IndexOutOfBoundsException 当行号越界
     */
    public int frameCount(int row) {
        if (row < 0 || row >= ROWS) {
            throw new IndexOutOfBoundsException("row " + row + " out of [0," + ROWS + ")");
        }
        return frames[row];
    }

    /**
     * 取指定动画的帧时长序列（毫秒）。
     * 若清单中没有定义，回退到 {@link #DEFAULT_DURATIONS}。
     * 返回的数组是内部数组的副本，调用方可自由修改。
     */
    @NotNull public int[] durations(@NotNull PetAnimation animation) {
        int[] stored = durations.get(animation);
        if (stored != null) return stored.clone();
        int[] fallback = DEFAULT_DURATIONS.get(animation);
        return fallback == null ? new int[0] : fallback.clone();
    }

    /**
     * 给定一个行号，返回一个长度等于该行帧数、循环复用 idle 默认节奏的时长数组。
     * 用于"该行没有 per-track 覆盖"时的兜底渲染。
     */
    @NotNull public int[] defaultDurationsForRow(int row) {
        int count = frameCount(row);
        int[] pattern = DEFAULT_DURATIONS.get(PetAnimation.IDLE);
        if (pattern == null || pattern.length == 0) return new int[count];
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = pattern[i % pattern.length];
        }
        return out;
    }
}