package com.dsh.petwhale.listener;

import com.dsh.petwhale.state.PetActivityPhase;
import com.dsh.petwhale.state.PetStateService;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * 编辑器生命周期监听器，把编辑器焦点事件翻译成桌宠状态。
 *
 * <p>策略：编辑器被创建（打开/拆分）→ 触发 THINKING（原地跑动）；编辑器关闭 →
 * 不做任何事（让 idle 定时器自然超时回 IDLE）。
 *
 * <p><b>为什么不监听 editorReleased 立即切回 IDLE？</b>
 * 真实使用中 tab 切换非常频繁（搜索结果、文件对比、快速打开……），每次都闪回 idle
 * 会让桌宠不停抽搐。所以让 VCS 的 settle 定时器兜底就好，30 秒无活动后由
 * {@link com.dsh.petwhale.state.PetStateMachine} 自动收敛。</p>
 *
 * <p>本监听器在 {@code plugin.xml} 中通过 {@code <editorFactoryListener>} 注册。
 * IDEA 平台会为每个项目实例化一份，构造时尝试从 {@link PetStateService} 拿实例。</p>
 */
public final class PetEditorListener implements EditorFactoryListener {

    /** 注入的状态服务（IDE 反射实例化时为 null，走 {@link #serviceOrNull} 兜底） */
    private final PetStateService service;

    /** IDE 反射实例化路径（plugin.xml 注册时没有参数）。 */
    public PetEditorListener() {
        this.service = null;
    }

    /** 测试或显式 new 时注入。 */
    public PetEditorListener(@NotNull PetStateService service) {
        this.service = service;
    }

    /**
     * 编辑器被创建（用户打开文件、拆分编辑器）。
     * 把活动阶段切到 THINKING，并标记当前有活动会话。
     */
    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        PetStateService svc = serviceOrNull(event);
        if (svc == null) return;
        svc.setSessionActive();
        svc.setPhase(PetActivityPhase.THINKING);
    }

    /**
     * 编辑器被释放（关闭/合并）。故意保持 no-op，让 idle 定时器收敛。
     * 注释里曾考虑加 30s 倒计时，会和 VCS 监听器的 settle 任务重复，留 TODO。
     */
    @Override
    public void editorReleased(@NotNull EditorFactoryEvent event) {
        // 故意 no-op —— 立即切 IDLE 会让 tab 切换时桌宠抽搐；
        // 由 VCS 监听器的 settle 调度统一收敛即可。
    }

    /**
     * 解析本事件关联的 {@link PetStateService}。
     * 优先使用注入字段，否则从编辑器所属项目取。
     * 若编辑器是脱管（如 Welcome 屏），则返回 null。
     */
    private PetStateService serviceOrNull(EditorFactoryEvent event) {
        if (service != null) return service;
        Project project = event.getEditor().getProject();
        return project == null ? null : project.getService(PetStateService.class);
    }
}