package com.dsh.petwhale.state;

/**
 * 宠物所理解的 7 个活动阶段（ActivityPhase）。
 * 与 DSH 桌宠主机的词汇保持一致，确保同一份鲸鱼娘契约在 Web 客户端和 IDEA 插件上行为完全相同。
 *
 * <ul>
 *   <li>{@link #IDLE} — 空闲：无活动会话 / 待机</li>
 *   <li>{@link #WAITING} — 等待：期待用户输入（例如问题提示框）</li>
 *   <li>{@link #THINKING} — 思考中：模型正在推理</li>
 *   <li>{@link #TOOL} — 工具调用中：模型正在调用工具</li>
 *   <li>{@link #REVIEW} — 审视中：模型正在流式输出回复</li>
 *   <li>{@link #DONE} — 完成：本次回合成功结束（庆祝）</li>
 *   <li>{@link #FAILED} — 失败：本次回合以失败告终</li>
 * </ul>
 *
 * <p>枚举值顺序不可随意更改：{@link #ordinal()} 不会用于映射，
 * 但跨平台调试（如 MCP 序列化）时保持一致有助于排查问题。</p>
 */
public enum PetActivityPhase {
    /** 空闲状态：无活动会话或会话结束后回到默认 */
    IDLE,
    /** 等待用户输入 */
    WAITING,
    /** 思考中（模型推理） */
    THINKING,
    /** 工具调用中 */
    TOOL,
    /** 审视中（流式输出） */
    REVIEW,
    /** 完成（触发庆祝动画 jumping） */
    DONE,
    /** 失败（触发沮丧动画 failed） */
    FAILED;

    /**
     * 容错解析：把任意字符串规范化为枚举值。识别规则：
     * <ol>
     *   <li>{@code null} → 返回 {@link #IDLE}（最安全的默认值）</li>
     *   <li>去除首尾空白、转大写后调用 {@link #valueOf(String)}</li>
     *   <li>解析失败（如拼写错误）→ 返回 {@link #IDLE}（不抛异常）</li>
     * </ol>
     *
     * <p>这种宽松解析主要用于：监听器读取 IDE 事件字段、配置加载、序列化恢复。
     * 业务代码应优先使用枚举常量本身，避免不必要的字符串往返。</p>
     *
     * @param raw 原始字符串（可为空、可带空白、可大小写混用）
     * @return 解析得到的枚举值；任何失败都安全降级到 {@link #IDLE}
     */
    public static PetActivityPhase parse(String raw) {
        if (raw == null) return IDLE;
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return IDLE;
        }
    }
}