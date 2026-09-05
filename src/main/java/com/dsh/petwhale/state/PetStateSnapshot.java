package com.dsh.petwhale.state;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 状态机在每个渲染时刻的快照（不可变 record）。
 * 由 {@link PetStateMachine#render()} 生成；Swing 端读取后据此重绘，不修改本对象。
 *
 * <p>本快照设计成纯数据（POJO），不持有任何回调或资源引用，方便跨线程传递。
 * UI 线程只需关心 {@link #animation} 来选精灵帧，调试器关注 {@link #phase} 和
 * {@link #bubble} 来还原语义。</p>
 *
 * @param animation 当前应该播放的动画（例如 {@link PetAnimation#RUNNING}）
 * @param bubble 状态气泡里要显示的文字（可空；为空表示该动画阶段无气泡）
 * @param animationStartedAt 动画起始时间戳（毫秒，用于同步客户端循环）
 * @param phase 当前活动阶段（用于调试和 UI 决策）
 * @param sessionActive 当前是否有活动会话（无活动会话时桌宠通常挂起）
 */
public record PetStateSnapshot(
        @NotNull PetAnimation animation,
        @Nullable String bubble,
        long animationStartedAt,
        @NotNull PetActivityPhase phase,
        boolean sessionActive
) {
}