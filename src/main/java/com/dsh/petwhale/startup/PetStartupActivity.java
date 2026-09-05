package com.dsh.petwhale.startup;

import com.dsh.petwhale.listener.PetVcsListener;
import com.dsh.petwhale.state.PetStateService;
import com.dsh.petwhale.ui.PetFrame;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * Wires the plugin together once the IDE is ready.
 *
 * <ul>
 *   <li>Brings the global transparent {@PetFrame} onto the screen.</li>
 *   <li>Per-project: installs a {@VirtualFileListener} on the VFS so
 *       any activity under {@.git/} nudges the pet into a brief REVIEW
 *       animation (commit/pull hint).</li>
 * </ul>
 *
 * <p>Build/test listeners are deliberately absent in the MVP —
 * IntelliJ Platform splits {@BuildProgressListener} and
 * {@SMTRunnerEventsListener} into product-bundled plugin jars
 * ({@intellij.platform.smRunner}, the Java plugin) that aren't on
 * the provided classpath of an external plugin. A future revision
 * can wire those via {@BuildContentManager} or
 * {@SMTestRunnerConnectionUtil} once the platform artifacts are
 * exposed.
 */
public final class PetStartupActivity implements StartupActivity, StartupActivity.DumbAware {

    private static final Logger LOG = Logger.getInstance(PetStartupActivity.class);
    private static volatile PetFrame petFrame;

    @Override
    public void runActivity(@NotNull Project project) {
        PetStateService service = project.getService(PetStateService.class);

        // PetFrame is host-global — created once, reused across projects.
        if (petFrame == null) {
            synchronized (PetStartupActivity.class) {
                if (petFrame == null) {
                    petFrame = new PetFrame(service);
                    petFrame.show();
                }
            }
        }

        try {
            PetVcsListener vcs = new PetVcsListener(project, service);
            vcs.install();
        } catch (Throwable t) {
            LOG.warn("PetVcsListener install failed", t);
        }
    }

    public static void disposeFrame() {
        synchronized (PetStartupActivity.class) {
            if (petFrame != null) {
                petFrame.dispose();
                petFrame = null;
            }
        }
    }
}