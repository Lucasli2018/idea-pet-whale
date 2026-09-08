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
 *   <li>{@code startHidden} —— 启动时是否直接收起（隐藏后无召唤按钮，需到设置页重新显示）</li>
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
    /** 是否显示状态装饰（喷水、小鱼等表情气泡装饰），默认开启 */
    private boolean showDecorations = true;

    /** 宠物档案：名字（默认"鲸鱼娘"） */
    @NotNull private String petName = DEFAULT_PET_NAME;
    /** 宠物档案：亲密度 */
    private int intimacy = DEFAULT_INTIMACY;
    /** 宠物档案：小鱼干库存 */
    private int fishCount = DEFAULT_FISH_COUNT;
    /** 宠物档案：累计点数 */
    private int points = DEFAULT_POINTS;

    /** 默认宠物名 */
    public static final String DEFAULT_PET_NAME = "鲸鱼娘";
    /** 默认亲密度 */
    public static final int DEFAULT_INTIMACY = 0;
    /** 默认小鱼干数量 */
    public static final int DEFAULT_FISH_COUNT = 20;
    /** 默认点数 */
    public static final int DEFAULT_POINTS = 0;
    /** 亲密度单次喂食增量 */
    public static final int FEED_INTIMACY_BONUS = 10;
    /** 点数单次喂食增量 */
    public static final int FEED_POINTS_BONUS = 5;

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

    /** 是否显示状态装饰（喷水、小鱼等表情气泡装饰）。 */
    public boolean isShowDecorations() { return showDecorations; }

    public void setShowDecorations(boolean value) { this.showDecorations = value; }

    /** 宠物名字。 */
    @NotNull public String getPetName() { return petName; }

    public void setPetName(@Nullable String name) {
        this.petName = (name == null || name.isBlank()) ? DEFAULT_PET_NAME : name.trim();
    }

    /** 亲密度。 */
    public int getIntimacy() { return intimacy; }

    public void setIntimacy(int value) { this.intimacy = Math.max(0, value); }

    /** 小鱼干数量。 */
    public int getFishCount() { return fishCount; }

    public void setFishCount(int value) { this.fishCount = Math.max(0, value); }

    /** 累计点数。 */
    public int getPoints() { return points; }

    public void setPoints(int value) { this.points = Math.max(0, value); }

    /** 尝试投喂一条小鱼干：有库存时扣减并增加亲密度/点数，返回是否成功。 */
    public boolean feedOne() {
        if (fishCount <= 0) return false;
        fishCount--;
        intimacy += FEED_INTIMACY_BONUS;
        points += FEED_POINTS_BONUS;
        return true;
    }

    /** 把亲密度换算成等级称号。 */
    @NotNull
    public static String intimacyTitle(int intimacy) {
        if (intimacy < 100) return "素昧平生";
        if (intimacy < 300) return "一见如故";
        if (intimacy < 600) return "心意相通";
        if (intimacy < 1000) return "心有灵犀";
        return "灵魂伴侣";
    }

    /** 把亲密度换算成等级：每 200 点升 1 级，从 Lv.1 起算（悬浮框数值条显示用）。 */
    public static int intimacyLevel(int intimacy) {
        return Math.max(0, intimacy) / 200 + 1;
    }

    /**
     * 亲密度在当前称号区间内的进度百分比（0~100，悬浮框进度条用）。
     * 区间与 {@link #intimacyTitle(int)} 一致：0-99 / 100-299 / 300-599 / 600-999 / 1000+。
     */
    public static int intimacyProgress(int intimacy) {
        int v = Math.max(0, intimacy);
        if (v < 100) return v;                              // 素昧平生：0~99
        if (v < 300) return (v - 100) * 100 / 200;          // 一见如故
        if (v < 600) return (v - 300) * 100 / 300;          // 心意相通
        if (v < 1000) return (v - 600) * 100 / 400;         // 心有灵犀
        return 100;                                          // 灵魂伴侣：满
    }

    /** 小鱼干进度条展示上限（库存达到即满条）。 */
    public static final int FISH_CAP = 99;
    /** 点数进度条展示上限（累计达到即满条）。 */
    public static final int POINTS_CAP = 500;

    /** 小鱼干库存进度百分比（0~100，按 {@link #FISH_CAP} 折算）。 */
    public static int fishProgress(int fish) {
        return Math.min(100, Math.max(0, fish) * 100 / FISH_CAP);
    }

    /** 点数进度百分比（0~100，按 {@link #POINTS_CAP} 折算）。 */
    public static int pointsProgress(int points) {
        return Math.min(100, Math.max(0, points) * 100 / POINTS_CAP);
    }

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
        this.showDecorations = state.showDecorations;
        setPetName(state.petName);
        this.intimacy = Math.max(0, state.intimacy);
        this.fishCount = Math.max(0, state.fishCount);
        this.points = Math.max(0, state.points);
    }
}
