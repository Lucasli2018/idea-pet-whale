package com.dsh.petwhale.state;

import com.dsh.petwhale.resource.PetResources;
import com.dsh.petwhale.resource.PetTheme;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application-scoped service that owns the {@PetStateMachine} and
 * broadcasts snapshots to all registered listeners. Listener collection
 * is a {@CopyOnWriteArrayList} so the Swing EDT can detach on dispose
 * without racing the broadcast.
 *
 * <p>UI components (the global transparent frame) register a listener
 * in their constructor and remove it on disposal; IDE event listeners
 * (editor/build/vcs/test) call {@setPhase} / {@setSessionActive} /
 * {@clearSession} to feed the machine.
 */
@Service(Service.Level.APP)
public final class PetStateService implements Disposable {

    private final PetStateMachine machine = new PetStateMachine();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private volatile PetTheme theme = PetTheme.WHALE;

    @NotNull
    public PetStateSnapshot render() {
        return machine.render();
    }

    @NotNull
    public PetResources.Theme currentTheme() {
        return PetResources.load(theme);
    }

    /** Switch the visual theme; immediately broadcasts a snapshot so the UI redraws. */
    public void setTheme(@NotNull PetTheme next) {
        if (next == theme) return;
        this.theme = next;
        broadcast("theme-switch");
    }

    @NotNull public PetTheme theme() { return theme; }

    public void setPhase(@NotNull PetActivityPhase phase) {
        machine.onActivityStatus(new PetStateInput(phase, null, null));
        broadcast(phase.name().toLowerCase());
    }

    public void setPhase(@NotNull PetActivityPhase phase, @Nullable String line, @Nullable String phrase) {
        machine.onActivityStatus(new PetStateInput(phase, line, phrase));
        broadcast(phase.name().toLowerCase());
    }

    public void setSessionActive() {
        machine.onSessionActive();
        broadcast("session-active");
    }

    public void clearSession() {
        machine.onSessionDisposed();
        broadcast("session-disposed");
    }

    public void addListener(@NotNull Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(@NotNull Listener listener) {
        listeners.remove(listener);
    }

    private void broadcast(@NotNull String reason) {
        PetStateSnapshot snapshot = machine.render();
        for (Listener listener : listeners) {
            try {
                listener.onSnapshot(snapshot, reason);
            } catch (RuntimeException ignored) {
                // One bad listener must not break the rest.
            }
        }
    }

    @Override
    public void dispose() {
        listeners.clear();
    }

    @FunctionalInterface
    public interface Listener {
        void onSnapshot(@NotNull PetStateSnapshot snapshot, @NotNull String reason);
    }
}