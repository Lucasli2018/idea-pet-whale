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
 * 应用级别（{@link Service.Level#APP}）的状态服务。
 *
 * <p>它拥有唯一的 {@link PetStateMachine} 实例，并把每一次输入广播给所有注册过的
 * {@link Listener}。监听器列表用 {@link CopyOnWriteArrayList}，原因是：
 * <ul>
 *   <li>Swing EDT 在 dispose 时会调用 {@link #removeListener}；</li>
 *   <li>而广播（broadcast）发生在任意线程（监听器回调时）。</li>
 * </ul>
 * 普通 {@link java.util.ArrayList} 会因为并发修改抛 {@link java.util.ConcurrentModificationException}，
 * CopyOnWriteArrayList 用拷贝 + 替换彻底规避这个问题。</p>
 *
 * <p>典型使用：
 * <ul>
 *   <li>UI 组件（透明桌宠窗口）在构造时 {@link #addListener}，销毁时 {@link #removeListener}</li>
 *   <li>IDE 事件监听器（编辑器、构建、VCS、测试）调用 {@link #setPhase} 等方法喂数据</li>
 * </ul>
 *
 * <p>广播策略：每次状态变更后立刻生成一次 {@link PetStateSnapshot} 并 push 给所有监听器。
 * 单个监听器抛异常被吞掉，避免一个坏监听器阻塞整个广播链。</p>
 */
@Service(Service.Level.APP)
public final class PetStateService implements Disposable {

    /** 状态机实例，整个应用生命周期内唯一 */
    private final PetStateMachine machine = new PetStateMachine();
    /** 监听器列表（线程安全） */
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    /** 当前主题，使用 {@code volatile} 保证多线程可见性 */
    private volatile PetTheme theme = PetTheme.WHALE;
    /** 是否显示状态装饰（喷水、小鱼等表情气泡装饰），运行时标志 */
    private volatile boolean showDecorations = true;
    /** 久坐关怀顾问（打字活动计时 → 60 分钟提醒休息） */
    private final PetCareAdvisor careAdvisor = new PetCareAdvisor();

    /** 久坐关怀顾问（编辑器打字活动喂数据，关怀定时器周期检查）。 */
    @NotNull public PetCareAdvisor care() { return careAdvisor; }

    /**
     * 取当前快照（不触发广播）。供不需要响应、只想读最新状态的场景使用。
     */
    @NotNull
    public PetStateSnapshot render() {
        return machine.render();
    }

    /**
     * 加载当前主题对应的资源（精灵图、manifest）。首次访问会触发 IO，
     * 之后走 {@link PetResources} 的缓存。
     */
    @NotNull
    public PetResources.Theme currentTheme() {
        return PetResources.load(theme);
    }

    /**
     * 切换主题。变更后立刻广播一次快照，让 UI 刷新到新主题。
     *
     * @param next 新主题；与当前相同则 no-op
     */
    public void setTheme(@NotNull PetTheme next) {
        if (next == theme) return;
        this.theme = next;
        broadcast("theme-switch");
    }

    /** 取当前主题。 */
    @NotNull public PetTheme theme() { return theme; }

    /** 是否显示状态装饰。 */
    public boolean isShowDecorations() { return showDecorations; }

    /**
     * 设置是否显示状态装饰。
     */
    public void setShowDecorations(boolean value) {
        this.showDecorations = value;
    }

    /**
     * 只设置阶段，不携带气泡文案。
     */
    public void setPhase(@NotNull PetActivityPhase phase) {
        machine.onActivityStatus(new PetStateInput(phase, null, null));
        broadcast(phase.name().toLowerCase());
    }

    /**
     * 设置阶段并携带气泡文案。
     *
     * @param phase 活动阶段
     * @param line 状态文案（次选；可空）
     * @param phrase 俏皮语（最优先；可空）
     */
    public void setPhase(@NotNull PetActivityPhase phase, @Nullable String line, @Nullable String phrase) {
        machine.onActivityStatus(new PetStateInput(phase, line, phrase));
        broadcast(phase.name().toLowerCase());
    }

    /** 标记活动会话开始（项目打开、测试运行等）。 */
    public void setSessionActive() {
        machine.onSessionActive();
        broadcast("session-active");
    }

    /** 标记活动会话结束（项目关闭、用户主动停止）。 */
    public void clearSession() {
        machine.onSessionDisposed();
        broadcast("session-disposed");
    }

    /**
     * 注册监听器（幂等：同一监听器注册多次不会重复触发）。
     */
    public void addListener(@NotNull Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    /**
     * 注销监听器。监听器应当在 dispose 时调用，避免内存泄漏。
     */
    public void removeListener(@NotNull Listener listener) {
        listeners.remove(listener);
    }

    /**
     * 把当前快照推给所有监听器。单个监听器异常被吞，不影响其他监听器。
     *
     * @param reason 变更原因（如阶段名、theme-switch、session-disposed），便于监听器调试
     */
    private void broadcast(@NotNull String reason) {
        PetStateSnapshot snapshot = machine.render();
        for (Listener listener : listeners) {
            try {
                listener.onSnapshot(snapshot, reason);
            } catch (RuntimeException ignored) {
                // 一个坏监听器不能拖垮整条广播链
            }
        }
    }

    @Override
    public void dispose() {
        listeners.clear();
    }

    /**
     * 监听器接口。{@link com.dsh.petwhale.ui.PetPanel} 是典型实现。
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * 状态变更回调（在广播线程上被调用，不一定是 EDT，UI 实现须自行
         * {@code SwingUtilities.invokeLater} 切到 EDT）。
         *
         * @param snapshot 最新快照
         * @param reason 触发本次广播的原因（小写字符串，便于调试）
         */
        void onSnapshot(@NotNull PetStateSnapshot snapshot, @NotNull String reason);
    }
}