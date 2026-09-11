package com.dsh.petwhale.state;

/**
 * 久坐关怀顾问：跟踪"连续活跃时长"，到点提醒用户休息。
 *
 * <p>模型（全部时间由调用方注入 {@code now}，便于单元测试）：
 * <ul>
 *   <li><b>活跃会话</b>——第一次 {@link #onActivity} 开启会话（{@code sessionStart=now}）；
 *       之后每次活动刷新 {@code lastActivity}</li>
 *   <li><b>休息中断</b>——两次活动间隔超过 {@code pauseMs}（默认 5 分钟）视为离开，
 *       下次活动重新开一个会话（连续时长清零重新累计）</li>
 *   <li><b>提醒</b>——{@link #shouldRemind}：会话连续时长达到 {@code thresholdMs}
 *       （默认 60 分钟）→ 返回 true 并自动重置会话起点（每满一轮只提醒一次）</li>
 *   <li><b>离开检测</b>——{@code check} 时发现已经离开超过 pauseMs → 直接结束会话，
 *       回来后重新累计</li>
 * </ul>
 *
 * <p>线程安全：所有方法 synchronized（打字事件在写线程、关怀定时器在 EDT）。</p>
 */
public final class PetCareAdvisor {

    /** 默认提醒阈值：连续活跃 60 分钟 */
    public static final long DEFAULT_THRESHOLD_MS = 60L * 60 * 1000;
    /** 活动间隔超过 5 分钟视为"离开休息"，会话清零 */
    public static final long DEFAULT_PAUSE_MS = 5L * 60 * 1000;

    private long thresholdMs;
    private final long pauseMs;
    /** 是否有进行中的活跃会话 */
    private boolean hasSession;
    /** 当前活跃会话起点 */
    private long sessionStart;
    /** 最后一次活动时间戳 */
    private long lastActivity;

    /** 生产构造：60 分钟阈值 + 5 分钟中断判定。 */
    public PetCareAdvisor() {
        this(DEFAULT_THRESHOLD_MS, DEFAULT_PAUSE_MS);
    }

    /** 测试构造：可注入阈值与中断时长。 */
    PetCareAdvisor(long thresholdMs, long pauseMs) {
        this.thresholdMs = thresholdMs;
        this.pauseMs = pauseMs;
    }

    /**
     * 运行时调整提醒阈值（毫秒）。由设置页「关怀/喝水间隔」输入框经 PetPanel 周期调用写入，
     * 允许用户在桌宠运行期间动态改变提醒节奏而不需重启。
     * 仅更新阈值，会话累计状态（sessionStart / lastActivity）保持不变。
     */
    public synchronized void setThresholdMs(long thresholdMs) {
        if (thresholdMs > 0) {
            this.thresholdMs = thresholdMs;
        }
    }

    /** 当前提醒阈值（毫秒），测试与调试用。 */
    public synchronized long getThresholdMs() {
        return thresholdMs;
    }

    /**
     * 记录一次编辑器活动（打字）。
     *
     * @param now 当前时间戳（毫秒）
     */
    public synchronized void onActivity(long now) {
        if (!hasSession) {
            hasSession = true;
            sessionStart = now;
        } else if (now - lastActivity > pauseMs) {
            // 中间离开了太久：休息过了，重新开一个会话
            sessionStart = now;
        }
        lastActivity = now;
    }

    /**
     * 关怀检查（由定时器周期调用）。
     *
     * @param now 当前时间戳（毫秒）
     * @param enabled 用户是否开启久坐关怀（设置项；false 时永远 false 且不推进会话）
     * @return true = 应该提醒（本次调用已经消化掉这次提醒，下一轮要再等满一个阈值周期）
     */
    public synchronized boolean shouldRemind(long now, boolean enabled) {
        if (!enabled || !hasSession) return false;
        // 已经离开超过 pause：会话作废，回来后重新累计
        if (now - lastActivity > pauseMs) {
            hasSession = false;
            return false;
        }
        if (now - sessionStart >= thresholdMs) {
            // 消化本次提醒：从现在起重新累计，60 分钟后再提醒
            sessionStart = now;
            return true;
        }
        return false;
    }

    /** 当前会话已连续活跃时长（毫秒；无会话返回 0）。测试与调试用。 */
    public synchronized long activeMillis(long now) {
        if (!hasSession) return 0;
        return Math.max(0, now - sessionStart);
    }

    /** 重置全部状态（测试隔离用）。 */
    public synchronized void reset() {
        hasSession = false;
        sessionStart = 0;
        lastActivity = 0;
    }
}