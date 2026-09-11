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
 *   <li>{@code roamSpeed} —— 自动溜达行走速度（像素/帧，{@code 1~8}，默认 {@code 3} ≈ 75px/s）</li>
 *   <li>{@code roamIntervalSec} —— 自动溜达出发间隔（秒，{@code 1~30}，默认 {@code 5}）：控制两次跑步之间的休息时长</li>
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
    /** 溜达速度下限（像素/帧） */
    public static final int MIN_ROAM_SPEED = 1;
    /** 溜达速度上限（像素/帧） */
    public static final int MAX_ROAM_SPEED = 8;
    /** 默认溜达速度（像素/帧，约 75px/s，从容小碎步） */
    public static final int DEFAULT_ROAM_SPEED = 3;

    /** 跑步出发间隔下限（秒）：两次自动溜达之间的最短等待 */
    public static final int MIN_ROAM_INTERVAL_SEC = 1;
    /** 跑步出发间隔上限（秒） */
    public static final int MAX_ROAM_INTERVAL_SEC = 30;
    /** 默认跑步出发间隔（秒） */
    public static final int DEFAULT_ROAM_INTERVAL_SEC = 5;
    /**
     * 是否开启自动溜达（左右跑动）。默认关闭——桌宠默认安静待在角落，
     * 需要时在设置页「行为 → 自动溜达」打开，并可在「出发间隔」里设每次开跑的等待秒数。
     */
    public static final boolean DEFAULT_ROAM_ENABLED = false;

    /** 久坐关怀提醒间隔下限（分钟） */
    public static final int MIN_CARE_INTERVAL_MIN = 1;
    /** 久坐关怀提醒间隔上限（分钟） */
    public static final int MAX_CARE_INTERVAL_MIN = 600;
    /** 默认久坐关怀提醒间隔（分钟，连续编码满此值提醒休息/喝水） */
    public static final int DEFAULT_CARE_INTERVAL_MIN = 60;
    /** 默认是否开启喝水提醒（与久坐关怀独立，默认关闭） */
    public static final boolean DEFAULT_WATER_ENABLED = false;
    /** 喝水提醒间隔下限（分钟） */
    public static final int MIN_WATER_INTERVAL_MIN = 1;
    /** 喝水提醒间隔上限（分钟） */
    public static final int MAX_WATER_INTERVAL_MIN = 600;
    /** 默认喝水提醒间隔（分钟） */
    public static final int DEFAULT_WATER_INTERVAL_MIN = 60;

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
    /** 自动溜达行走速度（像素/帧，{@code 1~8}，默认 {@code 3} ≈ 75px/s） */
    private int roamSpeed = DEFAULT_ROAM_SPEED;
    /** 自动溜达出发间隔（秒，{@code 1~30}，默认 {@code 5}）：控制两次跑步之间的休息时长 */
    private int roamIntervalSec = DEFAULT_ROAM_INTERVAL_SEC;
    /** 是否开启自动溜达（左右跑动），默认关闭 */
    private boolean roamEnabled = DEFAULT_ROAM_ENABLED;
    /** 久坐关怀提醒（连续编码达阈值分钟数提醒喝水/起身），默认开启 */
    private boolean careEnabled = true;
    /** 久坐关怀提醒间隔（分钟，{@code 1~600}，默认 {@code 60}）：连续编码满此值提醒一次 */
    private int careIntervalMin = DEFAULT_CARE_INTERVAL_MIN;
    /** 是否开启喝水提醒（与久坐关怀独立、默认关闭；开启后按 waterIntervalMin 提醒） */
    private boolean waterEnabled = DEFAULT_WATER_ENABLED;
    /** 喝水提醒间隔（分钟，{@code 1~600}，默认 {@code 60}）：连续编码满此值提醒一次 */
    private int waterIntervalMin = DEFAULT_WATER_INTERVAL_MIN;
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

    /** 自动溜达行走速度（像素/帧，已 clamp 到 [{@link #MIN_ROAM_SPEED}, {@link #MAX_ROAM_SPEED}]）。 */
    public int getRoamSpeed() { return roamSpeed; }

    public void setRoamSpeed(int value) { this.roamSpeed = clampRoamSpeed(value); }

    /** 是否开启自动溜达（左右跑动），默认关闭。 */
    public boolean isRoamEnabled() { return roamEnabled; }

    public void setRoamEnabled(boolean value) { this.roamEnabled = value; }

    /** 自动溜达出发间隔（秒，已 clamp 到 [{@link #MIN_ROAM_INTERVAL_SEC}, {@link #MAX_ROAM_INTERVAL_SEC}]）。 */
    public int getRoamIntervalSec() { return roamIntervalSec; }

    public void setRoamIntervalSec(int value) { this.roamIntervalSec = clampRoamIntervalSec(value); }

    /** 久坐关怀提醒（连续编码达阈值分钟数提醒喝水/起身），默认开启。 */
    public boolean isCareEnabled() { return careEnabled; }

    public void setCareEnabled(boolean value) { this.careEnabled = value; }

    /** 久坐关怀提醒间隔（分钟，已 clamp 到 [{@link #MIN_CARE_INTERVAL_MIN}, {@link #MAX_CARE_INTERVAL_MIN}]）。 */
    public int getCareIntervalMin() { return careIntervalMin; }

    public void setCareIntervalMin(int value) { this.careIntervalMin = clampCareIntervalMin(value); }

    /** 是否开启喝水提醒（与久坐关怀独立的提醒），默认关闭。 */
    public boolean isWaterEnabled() { return waterEnabled; }

    public void setWaterEnabled(boolean value) { this.waterEnabled = value; }

    /** 喝水提醒间隔（分钟，已 clamp 到 [{@link #MIN_WATER_INTERVAL_MIN}, {@link #MAX_WATER_INTERVAL_MIN}]）。 */
    public int getWaterIntervalMin() { return waterIntervalMin; }

    public void setWaterIntervalMin(int value) { this.waterIntervalMin = clampWaterIntervalMin(value); }

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

    /**
     * 称号 / 等级的分界阈值（亲密度达到即进入对应档位，升序）。索引即档位序号。
     * {@link #intimacyTitle} 与 {@link #intimacyLevel} 共用此表，保证「等级」与「称号」
     * 永不漂移——不会出现"Lv.1 却配一见如故"的矛盾。
     */
    private static final int[] TIER_THRESHOLDS = {0, 100, 300, 600, 1000};
    /** 与 {@link #TIER_THRESHOLDS} 一一对应的称号（索引即档位）。 */
    private static final String[] TIER_TITLES = {
            "素昧平生", "一见如故", "心意相通", "心有灵犀", "灵魂伴侣"
    };

    /** 把亲密度换算成当前档位索引（0~4）。 */
    private static int tierIndex(int intimacy) {
        int v = Math.max(0, intimacy);
        int idx = 0;
        for (int i = 0; i < TIER_THRESHOLDS.length; i++) {
            if (v >= TIER_THRESHOLDS[i]) idx = i;
            else break;
        }
        return idx;
    }

    /** 把亲密度换算成等级称号（与 {@link #intimacyLevel} 同档位）。 */
    @NotNull
    public static String intimacyTitle(int intimacy) {
        return TIER_TITLES[tierIndex(intimacy)];
    }

    /**
     * 把亲密度换算成等级：等级与称号档位严格对齐——素昧平生=Lv.1，一见如故=Lv.2，
     * 心意相通=Lv.3，心有灵犀=Lv.4，灵魂伴侣=Lv.5（悬浮框数值条显示用）。
     */
    public static int intimacyLevel(int intimacy) {
        return tierIndex(intimacy) + 1;
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

    /**
     * 溜达速度 clamp。纯函数，测试直连。
     */
    public static int clampRoamSpeed(int value) {
        return Math.max(MIN_ROAM_SPEED, Math.min(MAX_ROAM_SPEED, value));
    }

    /**
     * 跑步出发间隔 clamp。纯函数，测试直连。
     */
    public static int clampRoamIntervalSec(int value) {
        return Math.max(MIN_ROAM_INTERVAL_SEC, Math.min(MAX_ROAM_INTERVAL_SEC, value));
    }

    /**
     * 久坐关怀提醒间隔 clamp（分钟）。纯函数，测试直连。
     */
    public static int clampCareIntervalMin(int value) {
        return Math.max(MIN_CARE_INTERVAL_MIN, Math.min(MAX_CARE_INTERVAL_MIN, value));
    }

    /**
     * 喝水提醒间隔 clamp（分钟）。纯函数，测试直连。
     */
    public static int clampWaterIntervalMin(int value) {
        return Math.max(MIN_WATER_INTERVAL_MIN, Math.min(MAX_WATER_INTERVAL_MIN, value));
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
        setRoamSpeed(state.roamSpeed);
        setRoamIntervalSec(state.roamIntervalSec);
        this.roamEnabled = state.roamEnabled;
        this.careEnabled = state.careEnabled;
        setCareIntervalMin(state.careIntervalMin);
        this.waterEnabled = state.waterEnabled;
        setWaterIntervalMin(state.waterIntervalMin);
        this.showDecorations = state.showDecorations;
        setPetName(state.petName);
        this.intimacy = Math.max(0, state.intimacy);
        this.fishCount = Math.max(0, state.fishCount);
        this.points = Math.max(0, state.points);
    }
}
