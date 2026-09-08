package com.dsh.petwhale.state;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * 状态机单元测试。
 *
 * <p>覆盖以下契约：
 * <ul>
 *   <li>阶段到动画的映射（{@link #animationForPhase_allPhasesMapped}）</li>
 *   <li>默认快照：未触发任何输入时回 IDLE、无气泡</li>
 *   <li>庆祝窗口：{@link PetActivityPhase#DONE} 持续 {@code celebrateMs} 毫秒后回归 IDLE</li>
 *   <li>失败窗口：{@link PetActivityPhase#FAILED} 持续 {@code failureMs} 毫秒后回归 IDLE</li>
 *   <li>阶段切换重置终态时间戳</li>
 *   <li>会话生命周期：active → disposed 完整状态机收敛</li>
 *   <li>气泡文案：phrase 优先于 line</li>
 *   <li>{@link PetActivityPhase#parse} / {@link PetAnimation#parse} 容错降级到 IDLE</li>
 * </ul>
 *
 * <p>使用 {@link AtomicLong} 作虚拟时钟，单测里手动推进时间，无需 sleep。</p>
 */
public class PetStateMachineTest {

    /** 7 个阶段都要有明确的动画映射（不能落到默认 IDLE 兜底）。 */
    @Test
    public void animationForPhase_allPhasesMapped() {
        assertEquals(PetAnimation.RUNNING_LEFT, PetStateMachine.animationForPhase(PetActivityPhase.THINKING));
        assertEquals(PetAnimation.RUNNING_RIGHT, PetStateMachine.animationForPhase(PetActivityPhase.TOOL));
        assertEquals(PetAnimation.REVIEW, PetStateMachine.animationForPhase(PetActivityPhase.REVIEW));
        assertEquals(PetAnimation.WAITING, PetStateMachine.animationForPhase(PetActivityPhase.WAITING));
        assertEquals(PetAnimation.JUMPING, PetStateMachine.animationForPhase(PetActivityPhase.DONE));
        assertEquals(PetAnimation.FAILED, PetStateMachine.animationForPhase(PetActivityPhase.FAILED));
        assertEquals(PetAnimation.IDLE, PetStateMachine.animationForPhase(PetActivityPhase.IDLE));
    }

    /** 新机器默认 IDLE、无气泡、未激活会话。 */
    @Test
    public void render_defaultsToIdleWhenNoActivity() {
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, () -> 0L);
        PetStateSnapshot snap = machine.render();
        assertEquals(PetAnimation.IDLE, snap.animation());
        assertNull(snap.bubble());
        assertFalse(snap.sessionActive());
    }

    /** DONE 阶段：先播 jumping 2.4 秒，再切回 idle，气泡同时清空。 */
    @Test
    public void donePhaseCelebrates_thenSettlesToIdle() {
        AtomicLong now = new AtomicLong(0);
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, now::get);
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.DONE, null, "完成！"));
        PetStateSnapshot early = machine.render();
        assertEquals(PetAnimation.JUMPING, early.animation());
        assertEquals("完成！", early.bubble());
        now.set(3000L);
        PetStateSnapshot settled = machine.render();
        assertEquals(PetAnimation.IDLE, settled.animation());
        assertNull(settled.bubble());
    }

    /** FAILED 阶段：先播 failed 2.4 秒，再切回 idle。 */
    @Test
    public void failedPhaseShowsThenSettlesToIdle() {
        AtomicLong now = new AtomicLong(0);
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, now::get);
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.FAILED, "出错啦", null));
        assertEquals(PetAnimation.FAILED, machine.render().animation());
        now.set(3000L);
        assertEquals(PetAnimation.IDLE, machine.render().animation());
    }

    /** 阶段切换必须重置 doneAt / failedAt，避免旧时间戳污染新状态。 */
    @Test
    public void phaseChangeClearsTerminalTimestamp() {
        AtomicLong now = new AtomicLong(0);
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, now::get);
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.DONE, null, null));
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.THINKING, null, null));
        now.set(10_000L);
        PetStateSnapshot snap = machine.render();
        assertEquals(PetAnimation.RUNNING_LEFT, snap.animation());
        assertNull(snap.bubble());
    }

    /** onSessionActive → onSessionDisposed 完整生命周期应把状态归零。 */
    @Test
    public void sessionLifecycleTogglesActiveFlag() {
        PetStateMachine machine = new PetStateMachine();
        assertFalse(machine.render().sessionActive());
        machine.onSessionActive();
        assertTrue(machine.render().sessionActive());
        machine.onSessionDisposed();
        PetStateSnapshot after = machine.render();
        assertFalse(after.sessionActive());
        assertEquals(PetAnimation.IDLE, after.animation());
    }

    /** phrase 优先于 line；line/phrase 都为空时气泡为 null。 */
    @Test
    public void bubblePrefersPhraseOverLine() {
        AtomicLong now = new AtomicLong(0);
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, now::get);
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.THINKING, "loading", "thonk"));
        assertEquals("thonk", machine.render().bubble());
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.THINKING, "loading", ""));
        assertEquals("loading", machine.render().bubble());
    }

    /** PetActivityPhase.parse 应识别混合大小写、首尾空白。 */
    @Test
    public void phaseParseAcceptsMixedCaseAndDash() {
        assertEquals(PetActivityPhase.THINKING, PetActivityPhase.parse("thinking"));
        assertEquals(PetActivityPhase.THINKING, PetActivityPhase.parse("THINKING"));
        assertEquals(PetActivityPhase.THINKING, PetActivityPhase.parse("  Thinking  "));
    }

    /** parse 对 null / 乱码应安全降级到 IDLE，绝不抛异常。 */
    @Test
    public void phaseParseReturnsIdleOnGarbage() {
        assertEquals(PetActivityPhase.IDLE, PetActivityPhase.parse("nonsense"));
        assertEquals(PetActivityPhase.IDLE, PetActivityPhase.parse(null));
    }

    /** PetAnimation.parse 把 kebab-case 识别为枚举值（适配 dsh-pet manifest）。 */
    @Test
    public void animationParseFallsBackGracefully() {
        assertEquals(PetAnimation.IDLE, PetAnimation.parse(null));
        assertEquals(PetAnimation.IDLE, PetAnimation.parse("not-a-real-anim"));
        assertEquals(PetAnimation.RUNNING_RIGHT, PetAnimation.parse("running-right"));
    }
}