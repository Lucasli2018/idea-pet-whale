package com.dsh.petwhale.state;

import org.jetbrains.annotations.NotNull;

import java.util.function.LongSupplier;

/**
 * 纯函数式状态机。
 *
 * <p>只持有：最近一次输入的活动阶段 + 终态时间戳（doneAt / failedAt）。
 * 没有存储、没有副作用、没有 Swing 依赖。
 *
 * <p>与 {@code dsh-pet/src/state.ts} 中的 {@code PetStateMachine} 形态一致，
 * 只是用 {@link LongSupplier} 替代了 TypeScript 里的 {@code Date.now}，方便测试时注入
 * 虚拟时钟。</p>
 *
 * <p><b>设计哲学：</b>本类刻意保持"笨"——只回答"现在应该播哪一帧动画"，
 * 不负责渲染、不负责节拍、不负责气泡窗口定位。UI 层（{@code PetPanel} /
 * {@code PetFrame}）拿 {@link PetStateSnapshot} 后自行处理。</p>
 */
public final class PetStateMachine {

    /** 当前活动阶段，初始为空闲 */
    private PetActivityPhase phase = PetActivityPhase.IDLE;
    /** 状态文案（次选来源），由 IDE 事件提供 */
    private String line;
    /** 俏皮语（最优先来源），由桌宠插件生成 */
    private String phrase;
    /** 当前是否有活动会话 */
    private boolean sessionActive;
    /** DONE 进入的时间戳（用于 celebrateMs 窗口），未触发时为 {@link Long#MIN_VALUE} */
    private long doneAt = Long.MIN_VALUE;
    /** FAILED 进入的时间戳（用于 failureMs 窗口），未触发时为 {@link Long#MIN_VALUE} */
    private long failedAt = Long.MIN_VALUE;
    /** 时间窗配置（庆祝/失败各自多长） */
    private final PetStateConfig config;
    /** 虚拟时钟，便于测试时手动推进时间 */
    private final LongSupplier clock;

    /** 默认构造：使用默认配置 + 系统时钟。 */
    public PetStateMachine() {
        this(PetStateConfig.DEFAULT, System::currentTimeMillis);
    }

    /**
     * 全参数构造。
     *
     * @param config 时间窗配置（{@code null} 会抛 NPE）
     * @param clock 时钟源（{@code null} 会抛 NPE）；传入 {@code () -> 0L} 即可冻结时间
     */
    public PetStateMachine(@NotNull PetStateConfig config, @NotNull LongSupplier clock) {
        this.config = config;
        this.clock = clock;
    }

    /**
     * 消费一次活动状态更新。
     *
     * <p>行为：
     * <ul>
     *   <li>总是覆盖 {@code phase}、{@code line}、{@code phrase}</li>
     *   <li>如果新阶段是 {@link PetActivityPhase#DONE}，记录当前时间为 {@code doneAt}</li>
     *   <li>如果新阶段不是 {@code DONE}，把 {@code doneAt} 重置为 {@link Long#MIN_VALUE}
     *       （意味着新一轮 done 会重新开始计时）</li>
     *   <li>{@code failedAt} 同理</li>
     * </ul>
     */
    public void onActivityStatus(@NotNull PetStateInput input) {
        this.phase = input.phase();
        this.line = input.line();
        this.phrase = input.phrase();
        if (input.phase() == PetActivityPhase.DONE) {
            this.doneAt = clock.getAsLong();
        } else {
            this.doneAt = Long.MIN_VALUE;
        }
        if (input.phase() == PetActivityPhase.FAILED) {
            this.failedAt = clock.getAsLong();
        } else {
            this.failedAt = Long.MIN_VALUE;
        }
    }

    /** 标记活动会话开始（项目打开、测试启动等场景）。 */
    public void onSessionActive() {
        this.sessionActive = true;
    }

    /** 标记活动会话结束：清空所有状态，强制回到 IDLE。 */
    public void onSessionDisposed() {
        this.sessionActive = false;
        this.phase = PetActivityPhase.IDLE;
        this.line = null;
        this.phrase = null;
        this.doneAt = Long.MIN_VALUE;
        this.failedAt = Long.MIN_VALUE;
    }

    /**
     * 渲染当前时刻的动画决策。
     *
     * <p>决策流程：
     * <ol>
     *   <li>根据 phase 选默认动画</li>
     *   <li>如果 done / failed 的播放窗口已过期，把动画降级为 IDLE</li>
     *   <li>如果处于 settled 状态（idle、过期、刚 disposed），气泡文案清空</li>
     *   <li>优先 phrase（俏皮语），其次 line（IDE 文案），都没有则气泡为空</li>
     * </ol>
     */
    @NotNull
    public PetStateSnapshot render() {
        long now = clock.getAsLong();
        PetAnimation animation = animationForPhase(phase);

        // 庆祝/失败窗口已过期？
        boolean doneSettled = phase == PetActivityPhase.DONE
                && doneAt != Long.MIN_VALUE
                && now - doneAt >= config.celebrateMs();
        boolean failedSettled = phase == PetActivityPhase.FAILED
                && failedAt != Long.MIN_VALUE
                && now - failedAt >= config.failureMs();

        if (doneSettled || failedSettled) {
            // 时间窗过后降级为 idle，避免桌宠一直卡在 jumping/failed 上
            animation = PetAnimation.IDLE;
        }

        // settled 时不显示气泡：idle / 庆祝过期 / 失败过期都属于"安静"状态
        boolean settled = phase == PetActivityPhase.IDLE || doneSettled || failedSettled;
        String bubble = settled ? null : firstNonBlank(phrase, line);

        return new PetStateSnapshot(
                animation,
                bubble,
                now,
                phase,
                sessionActive
        );
    }

    /**
     * 把一个活动阶段映射到对应的动画（核心映射表）。
     * 这里是契约的中心：调整本表就等于调整桌宠"怎么表达什么"。
     *
     * <pre>
     *   THINKING → RUNNING_LEFT    （向左跑，提示思考中）
     *   TOOL     → RUNNING_RIGHT   （向右跑，提示工具调用）
     *   REVIEW   → REVIEW          （来回张望）
     *   WAITING  → WAITING         （期待姿势）
     *   DONE     → JUMPING         （跳跃庆祝）
     *   FAILED   → FAILED          （沮丧）
     *   IDLE     → IDLE            （空闲呼吸）
     * </pre>
     */
    @NotNull
    public static PetAnimation animationForPhase(@NotNull PetActivityPhase phase) {
        switch (phase) {
            case THINKING: return PetAnimation.RUNNING_LEFT;
            case TOOL: return PetAnimation.RUNNING_RIGHT;
            case REVIEW: return PetAnimation.REVIEW;
            case WAITING: return PetAnimation.WAITING;
            case DONE: return PetAnimation.JUMPING;
            case FAILED: return PetAnimation.FAILED;
            case IDLE: return PetAnimation.IDLE;
            default: return PetAnimation.IDLE;
        }
    }

    /**
     * 取两个字符串中第一个非空白的；都为空白则返回 {@code null}。
     * 用于短语优先于文案行的场景。
     */
    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}