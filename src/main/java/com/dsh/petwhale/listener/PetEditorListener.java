package com.dsh.petwhale.listener;

import com.dsh.petwhale.state.PetActivityPhase;
import com.dsh.petwhale.state.PetStateService;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Mirrors the dsh-pet editor-driven thinking/idle mapping. An editor
 * gaining focus (selection) signals active work → RUNNING. When the
 * last editor closes we let the idle timer settle to IDLE rather than
 * forcing it here, so a transient toggle doesn't flicker the pet.
 */
public final class PetEditorListener implements EditorFactoryListener {

    private final PetStateService service;

    public PetEditorListener() {
        this.service = null;
    }

    public PetEditorListener(@NotNull PetStateService service) {
        this.service = service;
    }

    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        PetStateService svc = serviceOrNull(event);
        if (svc == null) return;
        svc.setSessionActive();
        svc.setPhase(PetActivityPhase.THINKING);
    }

    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        // No-op — an idle timer (PetIdleWatcher) flips to IDLE after 30s
        // with no editor activity. Forcing it here would flicker on every
        // tab swap.
    }

    private PetStateService serviceOrNull(EditorFactoryEvent event) {
        if (service != null) return service;
        Project project = event.getEditor().getProject();
        return project == null ? null : project.getService(PetStateService.class);
    }
}