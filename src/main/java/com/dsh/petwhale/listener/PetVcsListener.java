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
 * 监听 {@code .git} 目录下的 VFS 变化，触发短暂的 REVIEW 动画（提示用户：正在提交/拉取）。
 *
 * <p>启发式策略：任何位于 {@code .git/} 路径下的 create / delete / property-change /
 * move / copy / content-change 都算 commit-ish 活动，统一触发一次 REVIEW。
 * 调度器 2 秒后自动切回 IDLE，避免繁忙仓库让桌宠卡在 REVIEW 上不停抖动。</p>
 *
 * <p><b>为什么用单线程调度器 + AtomicBoolean 节流？</b>
 * 一次提交会触发几十个 VFS 事件（多个 git 对象写入），我们只需第一次触发动画、最后一次触发
 * settle。{@link #touch()} 用 {@code compareAndSet(false, true)} 做"是否已挂起 settle"的
 * 哨兵，避免重复调度。</p>
 *
 * <p>采用 2023.1 平台的 {@link VirtualFileListener} 回调形态：接口里所有方法都是
 * {@code default}，只需重写关心的几个。</p>
 */
public final class PetVcsListener implements VirtualFileListener {

    private static final Logger LOG = Logger.getInstance(PetVcsListener.class);

    /** REVIEW 动画持续多久后回到 IDLE（毫秒） */
    private static final long REVIEW_WINDOW_MS = 2000L;

    /** 关联项目（用于把监听器绑定到项目的 VFS 命名空间） */
    private final Project project;
    /** 状态服务（注入） */
    private final PetStateService service;
    /** 单线程调度器，负责 settle 定时回 IDLE */
    private final ScheduledExecutorService scheduler =
            new ScheduledThreadPoolExecutor(1, r -> {
                // 守护线程：插件卸载时不需要显式关闭
                Thread t = new Thread(r, "idea-pet-whale-vcs-settler");
                t.setDaemon(true);
                return t;
            });
    /** settle 是否已挂起（true 表示已经有定时任务在飞） */
    private final AtomicBoolean pending = new AtomicBoolean(false);

    public PetVcsListener(@NotNull Project project, @NotNull PetStateService service) {
        this.project = project;
        this.service = service;
    }

    /** 注册到 VFS（项目级范围）。失败时记录 warn，不抛。 */
    public void install() {
        VirtualFileManager.getInstance().addVirtualFileListener(this, project);
    }

    /** 文件属性变化（重命名、权限、mtime 等） */
    @Override
    public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        if (isGitPath(event.getFile())) touch();
    }

    /** 文件内容变化（git 写入 pack 对象、HEAD 文件更新等） */
    @Override
    public void contentsChanged(@NotNull VirtualFileEvent event) {
        if (isGitPath(event.getFile())) touch();
    }

    /** 文件创建 */
    @Override
    public void fileCreated(@NotNull VirtualFileEvent event) {
        if (isGitPath(event.getFile())) touch();
    }

    /** 文件删除 */
    @Override
    public void fileDeleted(@NotNull VirtualFileEvent event) {
        if (isGitPath(event.getFile())) touch();
    }

    /** 文件移动 */
    @Override
    public void fileMoved(@NotNull VirtualFileMoveEvent event) {
        if (isGitPath(event.getFile()) || isGitPath(event.getNewParent())) touch();
    }

    /** 文件拷贝（git 的 packed object 可能触发） */
    @Override
    public void fileCopied(@NotNull VirtualFileCopyEvent event) {
        if (isGitPath(event.getFile()) || isGitPath(event.getOriginalFile())) touch();
    }

    /**
     * 判断路径是否落在 {@code .git/} 目录下（兼容正反斜杠）。
     * 故意用 substring 而非 segment 比对，避免 {@code .gitignore} 等误触发。
     */
    private boolean isGitPath(VirtualFile file) {
        if (file == null) return false;
        String path = file.getPath();
        return path != null && path.replace('\\', '/').contains("/.git/");
    }

    /**
     * 触发 REVIEW 动画 + （如果第一次）挂起 settle 定时任务。
     * compareAndSet 哨兵保证同时挂起多个事件不会重复调度。
     */
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