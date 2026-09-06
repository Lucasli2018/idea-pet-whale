package com.dsh.petwhale.startup;

import com.dsh.petwhale.listener.PetVcsListener;
import com.dsh.petwhale.state.PetStateService;
import com.dsh.petwhale.ui.PetFrame;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

/**
 * IDE 启动入口：把插件所有零件拼起来。
 *
 * <p>职责：
 * <ol>
 *   <li>在第一个项目 ready 时创建全局透明桌宠窗口（{@link PetFrame}）；之后所有项目共享这一个窗口。</li>
 *   <li>为每个项目安装 VFS 监听器（{@link PetVcsListener}），捕捉 {@code .git/} 下的提交/拉取活动。</li>
 * </ol>
 *
 * <p><b>为什么不监听 build/test？</b>
 * IntelliJ Platform 把 {@code BuildProgressListener} 和 {@code SMTRunnerEventsListener}
 * 拆到产品级 plugin jar（{@code intellij.platform.smRunner}、Java 插件等），外部插件的
 * provided classpath 不含它们。MVP 阶段先靠编辑器 + VCS 两个最稳的信号，build/test 留给未来版本。</p>
 *
 * <p><b>为什么用 double-checked locking？</b>
 * PetFrame 是 host-global 的，多次 {@link #runActivity} 触发（每个项目一次）必须共用同一个窗口实例。
 * 用 volatile + 同步块避免不必要的锁开销，同时保证线程安全。</p>
 */
public final class PetStartupActivity implements StartupActivity, StartupActivity.DumbAware {

    private static final Logger LOG = Logger.getInstance(PetStartupActivity.class);
    /** 全局桌宠窗口（volatile 保证多线程可见性 + 防止指令重排） */
    private static volatile PetFrame petFrame;

    /**
     * 每个项目 ready 时被调用一次。
     *
     * @param project 刚打开的项目
     */
    @Override
    public void runActivity(@NotNull Project project) {
        PetStateService service = project.getService(PetStateService.class);

        // PetFrame 是 host-global：只在第一次项目时创建，后续项目复用。
        if (petFrame == null) {
            synchronized (PetStartupActivity.class) {
                if (petFrame == null) {
                    petFrame = new PetFrame(service);
                    petFrame.show();
                }
            }
        }

        // 每个项目都装一份 VFS 监听（不同项目可能有不同 git 目录）
        try {
            PetVcsListener vcs = new PetVcsListener(project, service);
            vcs.install();
        } catch (Throwable t) {
            LOG.warn("PetVcsListener install failed", t);
        }
    }

    /**
     * 显式销毁桌宠窗口（测试 / 插件卸载时调用）。双重检查同步块保证只 dispose 一次。
     */
    public static void disposeFrame() {
        synchronized (PetStartupActivity.class) {
            if (petFrame != null) {
                petFrame.dispose();
                petFrame = null;
            }
        }
    }

    /**
     * 取当前全局桌宠窗口（设置页 Apply 时实时生效用）。
     * 尚未创建（IDE 刚启动还没 ready）或已销毁时返回 {@code null}。
     */
    public static PetFrame currentFrame() {
        return petFrame;
    }
}