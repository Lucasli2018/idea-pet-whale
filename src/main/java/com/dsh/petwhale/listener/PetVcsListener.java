package com.dsh.petwhale.listener;

import com.dsh.petwhale.state.PetActivityPhase;
import com.dsh.petwhale.state.PetStateService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileCopyEvent;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.openapi.vfs.VirtualFilePropertyEvent;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Listens to VFS changes under any {@.git} directory and toggles
 * REVIEW for ~2 seconds on commit-ish activity. Cheap heuristic: any
 * property-change / content-change under {@.git/} fires the pet. The
 * scheduler settles back to IDLE afterwards so a busy repo doesn't
 * keep the pet stuck on REVIEW forever.
 *
 * <p>Uses the 2023.1 {@VirtualFileListener} callback shape — every
 * method is a {@default} on the interface, so we only override the
 * events we care about.
 */
public final class PetVcsListener implements VirtualFileListener {

    private static final Logger LOG = Logger.getInstance(PetVcsListener.class);
    private static final long REVIEW_WINDOW_MS = 2000L;

    private final Project project;
    private final PetStateService service;
    private final ScheduledExecutorService scheduler =
            new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r, "dsh-pet-whale-vcs-settler");
                t.setDaemon(true);
                return t;
            });
    private final AtomicBoolean pending = new AtomicBoolean(false);

    public PetVcsListener(@NotNull Project project, @NotNull PetStateService service) {
        this.project = project;
        this.service = service;
    }

    public void install() {
        VirtualFileManager.getInstance().addVirtualFileListener(this, project);
    }

    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (isGitPath(event.getFile())) touch();
    }

    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        if (isGitPath(event.getFile())) touch();
    }

    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
        if (isGitPath(event.getFile())) touch();
    }

    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        if (isGitPath(event.getFile())) touch();
    }

    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        if (isGitPath(event.getFile()) || isGitPath(event.getNewParent())) touch();
    }

    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        if (isGitPath(event.getFile()) || isGitPath(event.getOriginalFile())) touch();
    }

    private boolean isGitPath(VirtualFile file) {
        if (file == null) return false;
        String path = file.getPath();
        return path != null && path.replace('\\', '/').contains("/.git/");
    }

    private void touch() {
        service.setPhase(PetActivityPhase.REVIEW);
        if (pending.compareAndSet(false, true)) {
            scheduler.schedule(() -> {
                pending.set(false);
                service.setPhase(PetActivityPhase.IDLE);
            }, REVIEW_WINDOW_MS, TimeUnit.MILLISECONDS);
        }
    }
}