package com.dsh.petwhale.state;

import org.jetbrains.annotations.NotNull;

import java.util.function.LongSupplier;

/**
 * Pure state machine. Holds only the latest input phase and the terminal
 * celebration/failure timestamps; no storage, no side effects. The same
 * shape as {@code dsh-pet/src/state.ts PetStateMachine}, translated to Java
 * with a {@LongSupplier} in place of {@Date.now}.
 *
 * The machine is deliberately dumb — UI side does the rendering and
 * animation timing; here we only decide which track to play right now.
 */
public final class PetStateMachine {

    private PetActivityPhase phase = PetActivityPhase.IDLE;
    private String line;
    private String phrase;
    private boolean sessionActive;
    private long doneAt = Long.MIN_VALUE;
    private long failedAt = Long.MIN_VALUE;
    private final PetStateConfig config;
    private final LongSupplier clock;

    public PetStateMachine() {
        this(PetStateConfig.DEFAULT, System::currentTimeMillis);
    }

    public PetStateMachine(@NotNull PetStateConfig config, @NotNull LongSupplier clock) {
        this.config = config;
        this.clock = clock;
    }

    /** Consume one projected activity update. */
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

    /** A session became active (project opened, test run started, etc.). */
    public void onSessionActive() {
        this.sessionActive = true;
    }

    /** No active session left. */
    public void onSessionDisposed() {
        this.sessionActive = false;
        this.phase = PetActivityPhase.IDLE;
        this.line = null;
        this.phrase = null;
        this.doneAt = Long.MIN_VALUE;
        this.failedAt = Long.MIN_VALUE;
    }

    /** Render the current animation decision. */
    @NotNull
    public PetStateSnapshot render() {
        long now = clock.getAsLong();
        PetAnimation animation = animationForPhase(phase);

        boolean doneSettled = phase == PetActivityPhase.DONE
                && doneAt != Long.MIN_VALUE
                && now - doneAt >= config.celebrateMs();
        boolean failedSettled = phase == PetActivityPhase.FAILED
                && failedAt != Long.MIN_VALUE
                && now - failedAt >= config.failureMs();

        if (doneSettled || failedSettled) {
            animation = PetAnimation.IDLE;
        }

        // Settled sessions never bubble: idle (e.g. an aborted turn),
        // completed celebration expiry, and failed display expiry all fall silent.
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

    /** Map one activity phase onto the animation contract. */
    @NotNull
    public static PetAnimation animationForPhase(@NotNull PetActivityPhase phase) {
        switch (phase) {
            case THINKING: return PetAnimation.RUNNING;
            case TOOL: return PetAnimation.RUNNING_RIGHT;
            case REVIEW: return PetAnimation.REVIEW;
            case WAITING: return PetAnimation.WAITING;
            case DONE: return PetAnimation.JUMPING;
            case FAILED: return PetAnimation.FAILED;
            case IDLE: return PetAnimation.IDLE;
            default: return PetAnimation.IDLE;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}