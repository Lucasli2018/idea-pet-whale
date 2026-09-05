package com.dsh.petwhale.state;

/**
 * 状态机的可配置参数。默认值与 DSH 桌宠主机完全一致，确保鲸鱼娘在两端表现一致。
 *
 * <p>当前版本只暴露两个时间窗。未来若需支持不同主题（胖版/瘦版/快捷版）的不同节奏，
 * 可在此 record 上扩展字段并相应调整 {@link PetStateMachine} 的渲染逻辑。</p>
 *
 * @param celebrateMs {@link PetActivityPhase#DONE} 庆祝动画（jumping）的播放窗口（毫秒）。
 *                    超时后切回 {@link PetAnimation#IDLE}，气泡清空。
 * @param failureMs   {@link PetActivityPhase#FAILED} 沮丧动画（failed）的播放窗口（毫秒）。
 *                    超时后切回 {@link PetAnimation#IDLE}，气泡清空。
 */
public record PetStateConfig(long celebrateMs, long failureMs) {
    /** 默认配置：庆祝 2.4 秒、失败 2.4 秒（与 DSH Web 端一致）。 */
    public static final PetStateConfig DEFAULT = new PetStateConfig(2400L, 2400L);
}