package com.dsh.petwhale.state;

import com.dsh.petwhale.resource.PetManifest;
import com.dsh.petwhale.resource.PetTheme;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link PetSettingsState} 单元测试。
 *
 * <p>覆盖契约：
 * <ul>
 *   <li>clamp 边界：低于下限 / 高于上限 / 正常值三段（纯静态函数直连）</li>
 *   <li>缩放数学：{@code scaledWidth/scaledHeight} 与 192×208 基准的比例关系</li>
 *   <li>主题名清洗：null / 未知值回退 WHALE</li>
 *   <li>位置哨兵值：默认 -1 表示自动定位</li>
 *   <li>loadState 脏数据收敛：越界值经 setter clamp 后落库</li>
 * </ul>
 *
 * <p>注意：{@link PetSettingsState} 是 IntelliJ 应用级服务，但本测试只走
 * POJO 语义（new + setter + loadState），不触碰平台服务总线，无需测试框架。</p>
 */
public class PetSettingsStateTest {

    // === clamp: sizePercent ===

    @Test
    public void clampSize_belowFloor_returnsFloor() {
        assertEquals(PetSettingsState.MIN_SIZE_PERCENT, PetSettingsState.clampSizePercent(-1));
        assertEquals(PetSettingsState.MIN_SIZE_PERCENT, PetSettingsState.clampSizePercent(0));
        assertEquals(PetSettingsState.MIN_SIZE_PERCENT, PetSettingsState.clampSizePercent(49));
    }

    @Test
    public void clampSize_aboveCeiling_returnsCeiling() {
        assertEquals(PetSettingsState.MAX_SIZE_PERCENT, PetSettingsState.clampSizePercent(201));
        assertEquals(PetSettingsState.MAX_SIZE_PERCENT, PetSettingsState.clampSizePercent(100000));
    }

    @Test
    public void clampSize_inRange_returnsValue() {
        assertEquals(100, PetSettingsState.clampSizePercent(100));
        assertEquals(137, PetSettingsState.clampSizePercent(137));
        assertEquals(PetSettingsState.MIN_SIZE_PERCENT, PetSettingsState.clampSizePercent(PetSettingsState.MIN_SIZE_PERCENT));
        assertEquals(PetSettingsState.MAX_SIZE_PERCENT, PetSettingsState.clampSizePercent(PetSettingsState.MAX_SIZE_PERCENT));
    }

    // === clamp: opacityPercent ===

    @Test
    public void clampOpacity_belowFloor_returnsFloor() {
        assertEquals(PetSettingsState.MIN_OPACITY_PERCENT, PetSettingsState.clampOpacityPercent(-50));
        assertEquals(PetSettingsState.MIN_OPACITY_PERCENT, PetSettingsState.clampOpacityPercent(29));
    }

    @Test
    public void clampOpacity_aboveCeiling_returnsCeiling() {
        assertEquals(PetSettingsState.MAX_OPACITY_PERCENT, PetSettingsState.clampOpacityPercent(101));
        assertEquals(PetSettingsState.MAX_OPACITY_PERCENT, PetSettingsState.clampOpacityPercent(999));
    }

    // === setter 内建 clamp ===

    @Test
    public void setters_clampDirtyValues() {
        PetSettingsState state = new PetSettingsState();
        state.setSizePercent(1);
        assertEquals(PetSettingsState.MIN_SIZE_PERCENT, state.getSizePercent());
        state.setSizePercent(5000);
        assertEquals(PetSettingsState.MAX_SIZE_PERCENT, state.getSizePercent());

        state.setOpacityPercent(0);
        assertEquals(PetSettingsState.MIN_OPACITY_PERCENT, state.getOpacityPercent());
        state.setOpacityPercent(250);
        assertEquals(PetSettingsState.MAX_OPACITY_PERCENT, state.getOpacityPercent());
    }

    // === 缩放数学 ===

    @Test
    public void scaledDimensions_defaultPercent_isCellSize() {
        assertEquals(PetManifest.CELL_WIDTH, PetSettingsState.scaledWidth(100));
        assertEquals(PetManifest.CELL_HEIGHT, PetSettingsState.scaledHeight(100));
    }

    @Test
    public void scaledDimensions_halfPercent_isHalfCell() {
        assertEquals(PetManifest.CELL_WIDTH / 2, PetSettingsState.scaledWidth(50));
        assertEquals(PetManifest.CELL_HEIGHT / 2, PetSettingsState.scaledHeight(50));
    }

    @Test
    public void scaledDimensions_neverBelowOne() {
        // 极端输入经过 clamp 后已不可能触底，但函数本身要有自保
        assertTrue(PetSettingsState.scaledWidth(0) >= 1);
        assertTrue(PetSettingsState.scaledHeight(-999) >= 1);
    }

    // === 主题名清洗 ===

    @Test
    public void theme_validNames_parseBack() {
        PetSettingsState state = new PetSettingsState();
        state.setThemeName("WHALE_REFINED");
        assertEquals(PetTheme.WHALE_REFINED, state.theme());
        state.setThemeName("WHALE");
        assertEquals(PetTheme.WHALE, state.theme());
    }

    @Test
    public void theme_nullOrUnknown_fallsBackToWhale() {
        PetSettingsState state = new PetSettingsState();
        state.setThemeName(null);
        assertEquals(PetTheme.WHALE, state.theme());
        state.setThemeName("not-a-theme");
        assertEquals(PetTheme.WHALE, state.theme());
        state.setThemeName("");
        assertEquals(PetTheme.WHALE, state.theme());
    }

    // === 位置哨兵 ===

    @Test
    public void defaultLocation_isNegativeOne_sentinelForAuto() {
        PetSettingsState state = new PetSettingsState();
        assertEquals(-1, state.getX());
        assertEquals(-1, state.getY());
    }

    @Test
    public void setWindowLocation_writesBothAxes() {
        PetSettingsState state = new PetSettingsState();
        state.setWindowLocation(120, 240);
        assertEquals(120, state.getX());
        assertEquals(240, state.getY());
    }

    // === 默认值 ===

    @Test
    public void defaults_matchContract() {
        PetSettingsState state = new PetSettingsState();
        assertEquals(PetSettingsState.DEFAULT_SIZE_PERCENT, state.getSizePercent());
        assertEquals(PetSettingsState.DEFAULT_OPACITY_PERCENT, state.getOpacityPercent());
        assertEquals(PetTheme.WHALE, state.theme());
        assertFalse(state.isStartHidden());
        assertTrue(state.isShowDecorations());
        assertEquals(PetSettingsState.DEFAULT_PET_NAME, state.getPetName());
        assertEquals(PetSettingsState.DEFAULT_INTIMACY, state.getIntimacy());
        assertEquals(PetSettingsState.DEFAULT_FISH_COUNT, state.getFishCount());
        assertEquals(PetSettingsState.DEFAULT_POINTS, state.getPoints());
        // 新增：自动溜达默认关闭；关怀/喝水间隔默认 60 分钟；喝水提醒默认关闭
        assertFalse(state.isRoamEnabled());
        assertEquals(PetSettingsState.DEFAULT_ROAM_INTERVAL_SEC, state.getRoamIntervalSec());
        assertTrue(state.isCareEnabled());
        assertEquals(PetSettingsState.DEFAULT_CARE_INTERVAL_MIN, state.getCareIntervalMin());
        assertFalse(state.isWaterEnabled());
        assertEquals(PetSettingsState.DEFAULT_WATER_INTERVAL_MIN, state.getWaterIntervalMin());
    }

    @Test
    public void newIntervalSettings_clampAndToggle() {
        PetSettingsState state = new PetSettingsState();
        // careIntervalMin clamp
        state.setCareIntervalMin(0);
        assertEquals(PetSettingsState.MIN_CARE_INTERVAL_MIN, state.getCareIntervalMin());
        state.setCareIntervalMin(99999);
        assertEquals(PetSettingsState.MAX_CARE_INTERVAL_MIN, state.getCareIntervalMin());
        state.setCareIntervalMin(45);
        assertEquals(45, state.getCareIntervalMin());

        // waterIntervalMin clamp
        state.setWaterIntervalMin(-5);
        assertEquals(PetSettingsState.MIN_WATER_INTERVAL_MIN, state.getWaterIntervalMin());
        state.setWaterIntervalMin(12345);
        assertEquals(PetSettingsState.MAX_WATER_INTERVAL_MIN, state.getWaterIntervalMin());

        // 开关
        state.setRoamEnabled(true);
        assertTrue(state.isRoamEnabled());
        state.setRoamEnabled(false);
        assertFalse(state.isRoamEnabled());
        state.setWaterEnabled(true);
        assertTrue(state.isWaterEnabled());
        state.setWaterEnabled(false);
        assertFalse(state.isWaterEnabled());
    }

    @Test
    public void petProfile_setterNormalizes() {
        PetSettingsState state = new PetSettingsState();
        state.setPetName("  露露  ");
        assertEquals("露露", state.getPetName());
        state.setPetName(null);
        assertEquals(PetSettingsState.DEFAULT_PET_NAME, state.getPetName());
        state.setPetName("  ");
        assertEquals(PetSettingsState.DEFAULT_PET_NAME, state.getPetName());

        state.setIntimacy(-10);
        assertEquals(0, state.getIntimacy());
        state.setFishCount(-5);
        assertEquals(0, state.getFishCount());
        state.setPoints(-1);
        assertEquals(0, state.getPoints());
    }

    @Test
    public void feedOne_consumesFishAndBoostsStats() {
        PetSettingsState state = new PetSettingsState();
        assertTrue(state.feedOne());
        assertEquals(PetSettingsState.DEFAULT_FISH_COUNT - 1, state.getFishCount());
        assertEquals(PetSettingsState.FEED_INTIMACY_BONUS, state.getIntimacy());
        assertEquals(PetSettingsState.FEED_POINTS_BONUS, state.getPoints());
    }

    @Test
    public void feedOne_emptyStock_returnsFalse() {
        PetSettingsState state = new PetSettingsState();
        state.setFishCount(0);
        assertFalse(state.feedOne());
        assertEquals(0, state.getIntimacy());
    }

    @Test
    public void intimacyTitle_tiersMatch() {
        assertEquals("素昧平生", PetSettingsState.intimacyTitle(0));
        assertEquals("素昧平生", PetSettingsState.intimacyTitle(99));
        assertEquals("一见如故", PetSettingsState.intimacyTitle(100));
        assertEquals("一见如故", PetSettingsState.intimacyTitle(299));
        assertEquals("心意相通", PetSettingsState.intimacyTitle(300));
        assertEquals("心意相通", PetSettingsState.intimacyTitle(599));
        assertEquals("心有灵犀", PetSettingsState.intimacyTitle(600));
        assertEquals("心有灵犀", PetSettingsState.intimacyTitle(999));
        assertEquals("灵魂伴侣", PetSettingsState.intimacyTitle(1000));
        assertEquals("灵魂伴侣", PetSettingsState.intimacyTitle(2000));
    }

    // === loadState 脏数据收敛 ===

    @Test
    public void loadState_clampsAndNormalizes() {
        PetSettingsState dirty = new PetSettingsState();
        dirty.setSizePercent(9999);
        dirty.setOpacityPercent(-7);
        dirty.setThemeName("bogus");
        dirty.setStartHidden(true);
        dirty.setShowDecorations(false);
        dirty.setWindowLocation(88, 66);
        dirty.setPetName("露露");
        dirty.setIntimacy(341);
        dirty.setFishCount(20);
        dirty.setPoints(1200);

        PetSettingsState target = new PetSettingsState();
        target.loadState(dirty);

        assertEquals(PetSettingsState.MAX_SIZE_PERCENT, target.getSizePercent());
        assertEquals(PetSettingsState.MIN_OPACITY_PERCENT, target.getOpacityPercent());
        assertEquals(PetTheme.WHALE, target.theme());
        assertTrue(target.isStartHidden());
        assertFalse(target.isShowDecorations());
        assertEquals(88, target.getX());
        assertEquals(66, target.getY());
        assertEquals("露露", target.getPetName());
        assertEquals(341, target.getIntimacy());
        assertEquals(20, target.getFishCount());
        assertEquals(1200, target.getPoints());
    }

    @Test
    public void showDecorations_persistsThroughLoadState() {
        PetSettingsState state = new PetSettingsState();
        assertTrue(state.isShowDecorations());
        state.setShowDecorations(false);
        assertFalse(state.isShowDecorations());

        PetSettingsState target = new PetSettingsState();
        target.loadState(state);
        assertFalse(target.isShowDecorations());
    }

    @Test
    public void loadState_negativeProfile_clampedToZero() {
        PetSettingsState dirty = new PetSettingsState();
        dirty.setIntimacy(-50);
        dirty.setFishCount(-1);
        dirty.setPoints(-999);

        PetSettingsState target = new PetSettingsState();
        target.loadState(dirty);

        assertEquals(0, target.getIntimacy());
        assertEquals(0, target.getFishCount());
        assertEquals(0, target.getPoints());
    }

    @Test
    public void loadState_transfersNewCareAndRoamFields() {
        PetSettingsState dirty = new PetSettingsState();
        dirty.setRoamEnabled(true);
        dirty.setCareEnabled(false);
        dirty.setCareIntervalMin(30);
        dirty.setWaterEnabled(true);
        dirty.setWaterIntervalMin(90);

        PetSettingsState target = new PetSettingsState();
        target.loadState(dirty);

        assertTrue(target.isRoamEnabled());
        assertFalse(target.isCareEnabled());
        assertEquals(30, target.getCareIntervalMin());
        assertTrue(target.isWaterEnabled());
        assertEquals(90, target.getWaterIntervalMin());
    }
}
