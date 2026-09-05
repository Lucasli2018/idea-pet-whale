package com.dsh.petwhale.state;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class PetStateMachineTest {

    @Test
    public void animationForPhase_allPhasesMapped() {
        assertEquals(PetAnimation.RUNNING, PetStateMachine.animationForPhase(PetActivityPhase.THINKING));
        assertEquals(PetAnimation.RUNNING_RIGHT, PetStateMachine.animationForPhase(PetActivityPhase.TOOL));
        assertEquals(PetAnimation.REVIEW, PetStateMachine.animationForPhase(PetActivityPhase.REVIEW));
        assertEquals(PetAnimation.WAITING, PetStateMachine.animationForPhase(PetActivityPhase.WAITING));
        assertEquals(PetAnimation.JUMPING, PetStateMachine.animationForPhase(PetActivityPhase.DONE));
        assertEquals(PetAnimation.FAILED, PetStateMachine.animationForPhase(PetActivityPhase.FAILED));
        assertEquals(PetAnimation.IDLE, PetStateMachine.animationForPhase(PetActivityPhase.IDLE));
    }

    @Test
    public void render_defaultsToIdleWhenNoActivity() {
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, () -> 0L);
        PetStateSnapshot snap = machine.render();
        assertEquals(PetAnimation.IDLE, snap.animation());
        assertNull(snap.bubble());
        assertFalse(snap.sessionActive());
    }

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

    @Test
    public void failedPhaseShowsThenSettlesToIdle() {
        AtomicLong now = new AtomicLong(0);
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, now::get);
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.FAILED, "出错啦", null));
        assertEquals(PetAnimation.FAILED, machine.render().animation());
        now.set(3000L);
        assertEquals(PetAnimation.IDLE, machine.render().animation());
    }

    @Test
    public void phaseChangeClearsTerminalTimestamp() {
        AtomicLong now = new AtomicLong(0);
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, now::get);
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.DONE, null, null));
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.THINKING, null, null));
        now.set(10_000L);
        PetStateSnapshot snap = machine.render();
        assertEquals(PetAnimation.RUNNING, snap.animation());
        assertNull(snap.bubble());
    }

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

    @Test
    public void bubblePrefersPhraseOverLine() {
        AtomicLong now = new AtomicLong(0);
        PetStateMachine machine = new PetStateMachine(PetStateConfig.DEFAULT, now::get);
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.THINKING, "loading", "thonk"));
        assertEquals("thonk", machine.render().bubble());
        machine.onActivityStatus(new PetStateInput(PetActivityPhase.THINKING, "loading", ""));
        assertEquals("loading", machine.render().bubble());
    }

    @Test
    public void phaseParseAcceptsMixedCaseAndDash() {
        assertEquals(PetActivityPhase.THINKING, PetActivityPhase.parse("thinking"));
        assertEquals(PetActivityPhase.THINKING, PetActivityPhase.parse("THINKING"));
        assertEquals(PetActivityPhase.THINKING, PetActivityPhase.parse("  Thinking  "));
    }

    @Test
    public void phaseParseReturnsIdleOnGarbage() {
        assertEquals(PetActivityPhase.IDLE, PetActivityPhase.parse("nonsense"));
        assertEquals(PetActivityPhase.IDLE, PetActivityPhase.parse(null));
    }

    @Test
    public void animationParseFallsBackGracefully() {
        assertEquals(PetAnimation.IDLE, PetAnimation.parse(null));
        assertEquals(PetAnimation.IDLE, PetAnimation.parse("not-a-real-anim"));
        assertEquals(PetAnimation.RUNNING_RIGHT, PetAnimation.parse("running-right"));
    }
}