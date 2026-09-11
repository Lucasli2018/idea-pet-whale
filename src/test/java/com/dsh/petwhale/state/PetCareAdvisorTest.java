package com.dsh.petwhale.state;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link PetCareAdvisor} 单元测试（时间全部注入，无 sleep）。
 *
 * <p>测试节奏模拟真实场景：打字事件（onActivity）源源不断，关怀定时器（shouldRemind）
 * 周期性检查——<b>每次检查前都有新鲜活动</b>，间隔远小于 pause 阈值。
 * 两个专门的用例验证"离开"行为（长时间无活动 → 会话作废）。</p>
 *
 * <p>覆盖契约：
 * <ul>
 *   <li>无会话 / 关闭开关 → 永不提醒</li>
 *   <li>连续活跃未满阈值 → false；到阈值 → true；同一周期只提醒一次</li>
 *   <li>提醒后自动重置：再提醒需再等满一个阈值周期</li>
 *   <li>onActivity 间隔超 pause → 重新开 session；check 时发现离开超 pause → 会话作废</li>
 * </ul>
 */
public class PetCareAdvisorTest {

    /** 测试参数：阈值 100 分钟压缩为 100ms，pause 5 分钟压缩为 50ms，便于用毫秒推进模拟 */
    private static final long THRESHOLD = 100L;
    private static final long PAUSE = 50L;

    private final PetCareAdvisor advisor = new PetCareAdvisor(THRESHOLD, PAUSE);

    /** 模拟持续打字：从 from 到 to 每 step 毫秒一次活动。 */
    private void feed(long from, long to, long step) {
        for (long t = from; t <= to; t += step) {
            advisor.onActivity(t);
        }
    }

    @Test
    public void noSession_neverReminds() {
        assertFalse(advisor.shouldRemind(1000, true));
        assertFalse(advisor.shouldRemind(999_999, true));
    }

    @Test
    public void disabled_neverReminds() {
        advisor.onActivity(0);
        feed(10, 100, 10);
        assertFalse(advisor.shouldRemind(105, false));
        assertFalse(advisor.shouldRemind(THRESHOLD + 10, false));
    }

    @Test
    public void belowThreshold_doesNotRemind() {
        advisor.onActivity(0);
        feed(10, 60, 10);
        assertFalse(advisor.shouldRemind(65, true));
    }

    @Test
    public void atThreshold_remindsOnce() {
        advisor.onActivity(0);
        feed(10, 90, 10);
        assertFalse(advisor.shouldRemind(95, true));
        // 到点提醒（最后一次活动 90，间隔 5 < pause 50）
        advisor.onActivity(100);
        assertTrue(advisor.shouldRemind(105, true));
        // 同一周期不重复提醒
        advisor.onActivity(115);
        assertFalse(advisor.shouldRemind(120, true));
    }

    @Test
    public void keepTyping_afterRemind_accumulatesFromResetPoint() {
        advisor.onActivity(0);
        feed(10, 100, 10);
        assertTrue(advisor.shouldRemind(105, true));
        // 提醒后继续打字：会话从提醒时刻（105）重新累计
        feed(115, 195, 10);
        assertFalse(advisor.shouldRemind(199, true));
        advisor.onActivity(205);
        assertTrue(advisor.shouldRemind(210, true));
    }

    @Test
    public void pause_longerThanGap_resetsSession() {
        advisor.onActivity(0);
        advisor.onActivity(40);
        // 间隔 60ms > pause 50ms：视为离开休息，新会话从 100 重新累计
        advisor.onActivity(100);
        assertEquals(0, advisor.activeMillis(100));
        feed(110, 190, 10);
        assertFalse(advisor.shouldRemind(195, true));
        advisor.onActivity(200);
        assertTrue(advisor.shouldRemind(205, true));
    }

    @Test
    public void away_onCheck_sessionInvalidated() {
        advisor.onActivity(0);
        // 用户离开：距最后活动远超 pause，check 直接作废会话
        assertFalse(advisor.shouldRemind(1000, true));
        assertEquals(0, advisor.activeMillis(1000));
        // 回来后重新累计（回来后保持打字节奏，会话从 1010 连续累计）
        advisor.onActivity(1010);
        feed(1050, 1090, 10);
        assertFalse(advisor.shouldRemind(1095, true));
        advisor.onActivity(1110);
        assertTrue(advisor.shouldRemind(1115, true));
    }

    @Test
    public void reset_clearsEverything() {
        advisor.onActivity(0);
        advisor.reset();
        assertEquals(0, advisor.activeMillis(5000));
        assertFalse(advisor.shouldRemind(5000, true));
    }

    @Test
    public void defaults_matchContract() {
        PetCareAdvisor production = new PetCareAdvisor();
        long base = 1_000_000L;
        long minute = 60L * 1000;
        production.onActivity(base);
        // 持续打字到 59 分钟
        for (long m = 4; m <= 56; m += 4) {
            production.onActivity(base + m * minute);
        }
        // 59 分钟：还差一点，不提醒
        production.onActivity(base + 59 * minute);
        assertFalse(production.shouldRemind(base + 59 * minute + 30_000, true));
        // 60 分钟整（最后一次活动 30 秒前 < pause 5 分钟）→ 提醒
        assertTrue(production.shouldRemind(base + 60 * minute, true));
    }

    @Test
    public void setThresholdMs_updatesReminderBoundary() {
        // 阈值 100ms，pause 50ms。feed() 操作的是本类字段 advisor，故此处直接复用字段。
        advisor.onActivity(0);
        feed(10, 80, 10);                              // 活动到 80ms，尚未达 100ms 阈值
        assertFalse(advisor.shouldRemind(90, true));  // 90-0=90 < 100 → 不提醒
        // 运行中把阈值从 100 调大到 200：会话起点 sessionStart 不变（未触发过提醒），需累计到 200ms 才提醒
        advisor.setThresholdMs(200);
        assertEquals(200, advisor.getThresholdMs());
        feed(85, 195, 10);                             // 持续打字，保持 lastActivity 新鲜，推过 200ms
        assertFalse(advisor.shouldRemind(199, true)); // 199-0=199 < 200 → 不提醒
        advisor.onActivity(205);
        assertTrue(advisor.shouldRemind(210, true));  // 210-0=210 >= 200 → 提醒
    }
}