package com.dsh.petwhale.state;

import com.dsh.petwhale.resource.PetTheme;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 桌宠用户设置（应用级持久化）。
 *
 * <p>存储在 IDE config 目录的 {@code pet.whale.settings.xml}，覆盖：
 * <ul>
 *   <li>{@code sizePercent} —— 桌宠缩放比例（百分比，100 = 原始 192×208）</li>
 *   <li>{@code opacityPercent} —— 窗口不透明度（百分比，100 = 完全不透明）</li>
 *   <li>{@code x}/{@code y} —— 上次窗口位置（{@code -1} 表示自动定位右下角）</li>
 *   <li>{@code themeName} —— 默认主题名（{@link PetTheme} 枚举名）</li>
 *   <li>{@code startHidden} —— 启动时是否直接收起（仅显示"召唤鲸鱼娘"按钮）</li>
 * </ul>
 *
 * <p>所有取值在写入端（setter）统一 clamp，保证从 XML 反序列化进来的脏数据
 * （手工编辑、旧版本残留）也不会把窗口撑出屏幕。</p>
 *
 * <p>clamp 与缩放数学均为纯静态函数，无平台依赖，可被单元测试直接覆盖。</p>
 */
@Service(Service.Level.APP)
@State(
        name = "com.dsh.petwhale.PetSettingsState",
        storages = @Storage("pet.whale.settings.xml")
)
public final class PetSettingsState implements PersistentStateComponent<PetSettingsState> {

    /** 缩放比例下限（百分比） */
    public static final int MIN_SIZE_PERCENT = 50;
    /** 缩放比例上限（百分比） */
    public static final int MAX_SIZE_PERCENT = 200;
    /** 不透明度下限（百分比）—— 再低基本看不见了 */
    public static final int MIN_OPACITY_PERCENT = 30;
    /** 不透明度上限（百分比） */
    public static final int MAX_OPACITY_PERCENT = 100;

    /** 默认缩放比例 */
    public static final int DEFAULT_SIZE_PERCENT = 100;
    /** 默认不透明度 */
    public static final int DEFAULT_OPACITY_PERCENT = 100;

    private int sizePercent = DEFAULT_SIZE_PERCENT;
    private int opacityPercent = DEFAULT_OPACITY_PERCENT;
    /** -1 表示未保存过，启动时自动定位屏幕右下角 */
    private int x = -1;
    private int y = -1;
    @NotNull private String themeName = PetTheme.WHALE.name();
    private boolean startHidden = false;
    /** 久坐关怀提醒（连续编码 60 分钟提醒喝水/起身），默认开启 */
    private boolean careEnabled = true;

    /** 缩放比例（百分比，已 clamp 到 [{@link #MIN_SIZE_PERCENT}, {@link #MAX_SIZE_PERCENT}]）。 */
    public int getSizePercent() { return sizePercent; }

    public void setSizePercent(int value) {
        this.sizePercent = clampSizePercent(value);
    }

    /** 窗口不透明度（百分比，已 clamp 到 [{@link #MIN_OPACITY_PERCENT}, {@link #MAX_OPACITY_PERCENT}]）。 */
    public int getOpacityPercent() { return opacityPercent; }

    public void setOpacityPercent(int value) {
        this.opacityPercent = clampOpacityPercent(value);
    }

    /** 上次窗口左上角 X；{@code -1} 表示自动定位。 */
    public int getX() { return x; }

    public void setX(int value) { this.x = value; }

    /** 上次窗口左上角 Y；{@code -1} 表示自动定位。 */
    public int getY() { return y; }

    public void setY(int value) { this.y = value; }

    /** 默认主题名（{@link PetTheme} 枚举名）。 */
    @NotNull public String getThemeName() { return themeName; }

    public void setThemeName(@Nullable String name) {
        this.themeName = parseThemeName(name);
    }

    /** 启动时是否直接收起（只显示召唤按钮）。 */
    public boolean isStartHidden() { return startHidden; }

    public void setStartHidden(boolean value) { this.startHidden = value; }

    /** 久坐关怀提醒（连续编码 60 分钟提醒喝水/起身），默认开启。 */
    public boolean isCareEnabled() { return careEnabled; }

    public void setCareEnabled(boolean value) { this.careEnabled = value; }

    /** 把持久化的主题名解析回枚举；未知值安全回退到 {@link PetTheme#WHALE}。 */
    @NotNull public PetTheme theme() {
        try {
            return PetTheme.valueOf(themeName);
        } catch (IllegalArgumentException | NullPointerException ex) {
            return PetTheme.WHALE;
        }
    }

    /** 一次性写入窗口位置（拖拽结束 / 关闭时调用）。 */
    public void setWindowLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /**
     * 缩放比例 clamp。纯函数，测试直连。
     */
    public static int clampSizePercent(int value) {
        return Math.max(MIN_SIZE_PERCENT, Math.min(MAX_SIZE_PERCENT, value));
    }

    /**
     * 不透明度 clamp。纯函数，测试直连。
     */
    public static int clampOpacityPercent(int value) {
        return Math.max(MIN_OPACITY_PERCENT, Math.min(MAX_OPACITY_PERCENT, value));
    }

    /** 按缩放比例算桌宠显示宽度（像素）。 */
    public static int scaledWidth(int sizePercent) {
        return Math.max(1, com.dsh.petwhale.resource.PetManifest.CELL_WIDTH * clampSizePercent(sizePercent) / 100);
    }

    /** 按缩放比例算桌宠显示高度（像素）。 */
    public static int scaledHeight(int sizePercent) {
        return Math.max(1, com.dsh.petwhale.resource.PetManifest.CELL_HEIGHT * clampSizePercent(sizePercent) / 100);
    }

    /** 主题名清洗：null / 未知值回退 {@link PetTheme#WHALE} 的枚举名。 */
    @NotNull
    private static String parseThemeName(@Nullable String name) {
        if (name == null) return PetTheme.WHALE.name();
        for (PetTheme theme : PetTheme.values()) {
            if (theme.name().equals(name)) return name;
        }
        return PetTheme.WHALE.name();
    }

    @Override
    public @Nullable PetSettingsState getState() {
        return this;
    }

    /**
     * XML 反序列化入口。平台先把文件里的字段灌进一个新实例，再整体替换当前值；
     * setter 内部 clamp 保证脏数据收敛。
     */
    @Override
    public void loadState(@NotNull PetSettingsState state) {
        setSizePercent(state.sizePercent);
        setOpacityPercent(state.opacityPercent);
        this.x = state.x;
        this.y = state.y;
        setThemeName(state.themeName);
        this.startHidden = state.startHidden;
        this.careEnabled = state.careEnabled;
    }
}
