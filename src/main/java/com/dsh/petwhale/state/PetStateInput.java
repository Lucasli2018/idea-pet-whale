package com.dsh.petwhale.state;

import org.jetbrains.annotations.Nullable;

/**
 * 状态机的输入快照：某一时刻的活动阶段 + 可选的状态文案。
 * 与 DSH 桌宠主机的 projected event payload 一致：{@code phase + line + phrase}。
 *
 * <p>典型来源：
 * <ul>
 *   <li>监听器（编辑器/VCS）调用 {@link PetStateService#setPhase} 时</li>
 *   <li>状态机从持久化文件恢复历史状态时</li>
 *   <li>单元测试模拟时间推进时</li>
 * </ul>
 *
 * @param phase 当前活动阶段（必填）
 * @param line 状态文案（次选；由 IDE 提供，如「正在加载索引…」）
 * @param phrase 简短俏皮语（最优先；由桌宠插件生成，如「摸摸头～」）
 */
public record PetStateInput(
        PetActivityPhase phase,
        @Nullable String line,
        @Nullable String phrase
) {
    /** 快速构造：只有阶段，无气泡文案。 */
    public static PetStateInput of(PetActivityPhase phase) {
        return new PetStateInput(phase, null, null);
    }

    /** 完整构造：阶段 + 状态行 + 俏皮语。 */
    public static PetStateInput of(PetActivityPhase phase, String line, String phrase) {
        return new PetStateInput(phase, line, phrase);
    }
}