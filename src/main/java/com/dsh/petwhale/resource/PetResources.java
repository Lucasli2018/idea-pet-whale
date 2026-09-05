package com.dsh.petwhale.resource;

import com.dsh.petwhale.state.PetAnimation;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

/**
 * 加载并缓存内置桌宠主题。
 *
 * <p>精灵图被切成每动画一帧序列，存在 {@link Theme} record 里。Swing 渲染端拿到 record 后
 * 可以逐帧绘制，无需再次读取精灵图，避免每次重绘都做 IO。</p>
 *
 * <p><b>WebP 解码：</b>JDK 的 {@link ImageIO} 在 JDK 17 上默认不识别 WebP。
 * <ul>
 *   <li>JDK 21+ 自带 WebP codec（{@code com.sun.imageio} 内置）</li>
 *   <li>JDK 17 需要把 {@code com.twelvemonkeys.imageio:imageio-webp} 放到 module path</li>
 * </ul>
 * 解码失败时本类会立刻抛 {@link IllegalStateException} 并附上明确错误信息，
 * 让用户知道去哪排查，而不是桌宠悄悄不显示。</p>
 *
 * <p>缓存用 {@link EnumMap} + synchronized 方法，进程内单次加载。</p>
 */
public final class PetResources {

    /** 已加载主题缓存（key: 主题枚举，value: 完整 Theme 记录） */
    private static final Map<PetTheme, Theme> cache = new EnumMap<>(PetTheme.class);

    /** 私有构造，禁止实例化。 */
    private PetResources() {
    }

    /**
     * 加载指定主题（首次加载后缓存，重复调用零成本）。
     *
     * @param theme 主题枚举
     * @return 已切帧的主题记录（包含 manifest、完整精灵图、逐动画帧序列）
     * @throws IllegalStateException 清单缺失、精灵图缺失、WebP 解码失败、IO 错误
     */
    @NotNull
    public synchronized static Theme load(@NotNull PetTheme theme) {
        Theme cached = cache.get(theme);
        if (cached != null) return cached;
        Theme built = build(theme);
        cache.put(theme, built);
        return built;
    }

    /**
     * 解析清单 → 读精灵图 → 按行切帧。
     * 每一行对应 {@link PetAnimation} 的一个值，每行内按 {@link PetManifest#frameCount}
     * 切片。
     */
    private static Theme build(PetTheme theme) {
        // 1) 解析清单（fail-closed，缺字段直接抛）
        PetManifest manifest = PetManifestParser.parse(theme.manifestResource());
        // 2) 读取精灵图（WebP 解码；JDK 17 可能失败）
        BufferedImage sheet = loadSpritesheet(manifest.spritesheetPath());
        // 3) 按行切帧
        Map<PetAnimation, BufferedImage[]> frameMap = new EnumMap<>(PetAnimation.class);
        for (PetAnimation animation : PetAnimation.values()) {
            int row = rowOf(animation);
            int frameCount = manifest.frameCount(row);
            BufferedImage[] frames = new BufferedImage[frameCount];
            for (int col = 0; col < frameCount; col++) {
                frames[col] = sheet.getSubimage(
                        col * PetManifest.CELL_WIDTH,
                        row * PetManifest.CELL_HEIGHT,
                        PetManifest.CELL_WIDTH,
                        PetManifest.CELL_HEIGHT
                );
            }
            frameMap.put(animation, frames);
        }
        return new Theme(theme, manifest, sheet, frameMap);
    }

    /**
     * 从 classpath 读精灵图到 {@link BufferedImage}。
     * 任何失败都转译成 {@link IllegalStateException} 并附原始 classpath。
     */
    private static BufferedImage loadSpritesheet(String classpathPath) {
        try (InputStream in = PetResources.class.getResourceAsStream(classpathPath)) {
            if (in == null) {
                throw new IllegalStateException("missing spritesheet at " + classpathPath);
            }
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                throw new IllegalStateException(
                        "ImageIO could not decode " + classpathPath
                                + " — install JDK 21+ WebP support or the twelvemonkeys imageio-webp plugin"
                );
            }
            return img;
        } catch (IOException ex) {
            throw new IllegalStateException("failed reading spritesheet " + classpathPath, ex);
        }
    }

    /**
     * 精灵图行索引。必须与 {@link PetAnimation} 的声明顺序一致：
     * <pre>
     *   IDLE=0 / RUNNING_RIGHT=1 / RUNNING_LEFT=2 / WAVING=3 /
     *   JUMPING=4 / FAILED=5 / WAITING=6 / RUNNING=7 / REVIEW=8
     * </pre>
     *
     * <p>精灵图布局契约：8 列 × 9 行，每格 192×208 像素，原点 (0,0) = 左上角。</p>
     */
    public static int rowOf(PetAnimation animation) {
        switch (animation) {
            case IDLE:          return 0;
            case RUNNING_RIGHT: return 1;
            case RUNNING_LEFT:  return 2;
            case WAVING:        return 3;
            case JUMPING:       return 4;
            case FAILED:        return 5;
            case WAITING:       return 6;
            case RUNNING:       return 7;
            case REVIEW:        return 8;
            default:            return 0;
        }
    }

    /**
     * 已加载主题的不可变记录：清单 + 完整精灵图 + 每动画帧序列。
     * Swing 渲染端只读不写。
     */
    public record Theme(
            @NotNull PetTheme id,
            @NotNull PetManifest manifest,
            @NotNull BufferedImage spritesheet,
            @NotNull Map<PetAnimation, BufferedImage[]> frames
    ) {
        /** 取指定动画的帧序列；理论上永远非空（{@build} 已经塞齐），但防御性返空数组。 */
        @NotNull public BufferedImage[] framesFor(@NotNull PetAnimation animation) {
            BufferedImage[] arr = frames.get(animation);
            return arr == null ? new BufferedImage[0] : arr;
        }
    }
}