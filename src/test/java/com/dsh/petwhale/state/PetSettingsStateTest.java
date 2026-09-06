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
    }

    // === loadState 脏数据收敛 ===

    @Test
    public void loadState_clampsAndNormalizes() {
        PetSettingsState dirty = new PetSettingsState();
        dirty.setSizePercent(9999);
        dirty.setOpacityPercent(-7);
        dirty.setThemeName("bogus");
        dirty.setStartHidden(true);
        dirty.setWindowLocation(88, 66);

        PetSettingsState target = new PetSettingsState();
        target.loadState(dirty);

        assertEquals(PetSettingsState.MAX_SIZE_PERCENT, target.getSizePercent());
        assertEquals(PetSettingsState.MIN_OPACITY_PERCENT, target.getOpacityPercent());
        assertEquals(PetTheme.WHALE, target.theme());
        assertTrue(target.isStartHidden());
        assertEquals(88, target.getX());
        assertEquals(66, target.getY());
    }
}
