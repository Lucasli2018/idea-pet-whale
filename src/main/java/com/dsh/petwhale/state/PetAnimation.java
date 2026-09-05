package com.dsh.petwhale.state;

/**
 * 桌宠支持的 9 种动画状态（与精灵图中的 9 行一一对应）。
 *
 * <p><b>行号契约：</b>枚举的声明顺序就是精灵图（spritesheet）中的行索引：
 * <pre>
 *   0 IDLE          空闲呼吸
 *   1 RUNNING_RIGHT 向右奔跑
 *   2 RUNNING_LEFT  向左奔跑
 *   3 WAVING        挥手
 *   4 JUMPING       跳跃
 *   5 FAILED        失败（沮丧）
 *   6 WAITING       等待（期待姿势）
 *   7 RUNNING       原地奔跑
 *   8 REVIEW        审视（来回张望）
 * </pre>
 *
 * <p>绝对不要在枚举值之间插入新成员——{@link com.dsh.petwhale.resource.PetResources#rowOf}
 * 依赖这个固定顺序来切帧。</p>
 */
public enum PetAnimation {
    /** 第 0 行：空闲呼吸 */
    IDLE,            // 0
    /** 第 1 行：向右跑（用于 TOOL 阶段，提示工具调用中） */
    RUNNING_RIGHT,  // 1
    /** 第 2 行：向左跑（备用） */
    RUNNING_LEFT,   // 2
    /** 第 3 行：挥手（用于 FAILED 后的轻互动） */
    WAVING,         // 3
    /** 第 4 行：跳跃（用于 DONE 庆祝） */
    JUMPING,        // 4
    /** 第 5 行：失败（用于 FAILED 阶段） */
    FAILED,         // 5
    /** 第 6 行：等待（用于 WAITING 阶段） */
    WAITING,        // 6
    /** 第 7 行：原地跑（用于 THINKING 阶段） */
    RUNNING,        // 7
    /** 第 8 行：审视（用于 REVIEW 阶段和 VCS 提交/拉取反馈） */
    REVIEW;         // 8

    /**
     * 容错解析：把任意字符串规范化为枚举值。识别规则：
     * <ol>
     *   <li>{@code null} → 返回 {@link #IDLE}</li>
     *   <li>去除首尾空白、转大写、把 {@code -} 替换为 {@code _}（适配 dsh-pet
     *       manifest 中常见的 kebab-case 命名）</li>
     *   <li>解析失败 → 返回 {@link #IDLE}（不抛异常）</li>
     * </ol>
     *
     * @param raw 原始字符串（可为空、可带空白、可大小写混用、可带连字符）
     * @return 解析得到的枚举值；任何失败都安全降级到 {@link #IDLE}
     */
    public static PetAnimation parse(String raw) {
        if (raw == null) return IDLE;
        String normalized = raw.trim().toUpperCase().replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return IDLE;
        }
    }
}