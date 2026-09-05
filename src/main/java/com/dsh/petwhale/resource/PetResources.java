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
 * Loads and caches built-in pet themes. The atlas is sliced per-animation
 * into a {@Theme} record that the Swing renderer can paint frame-by-frame
 * without re-reading the spritesheet.
 *
 * <p>JDK's {@ImageIO} does not natively read WebP; on JDK 17 the built-in
 * com.sun.imageio does not ship WebP. We require either JDK 21+ (which
 * bundles WebP support) or a {@com.twelvemonkeys.imageio} plugin on the
 * module path. When WebP isn't supported, the loader fails fast with a
 * descriptive error so the plugin doesn't silently show nothing.
 */
public final class PetResources {

    private static final Map<PetTheme, Theme> cache = new EnumMap<>(PetTheme.class);

    private PetResources() {
    }

    @NotNull
    public synchronized static Theme load(@NotNull PetTheme theme) {
        Theme cached = cache.get(theme);
        if (cached != null) return cached;
        Theme built = build(theme);
        cache.put(theme, built);
        return built;
    }

    private static Theme build(PetTheme theme) {
        PetManifest manifest = PetManifestParser.parse(theme.manifestResource());
        BufferedImage sheet = loadSpritesheet(manifest.spritesheetPath());
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

    /** Spritesheet row index — must match {@PetAnimation} declaration order. */
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

    /** Loaded theme: manifest metadata plus sliced frames per animation. */
    public record Theme(
            @NotNull PetTheme id,
            @NotNull PetManifest manifest,
            @NotNull BufferedImage spritesheet,
            @NotNull Map<PetAnimation, BufferedImage[]> frames
    ) {
        @NotNull public BufferedImage[] framesFor(@NotNull PetAnimation animation) {
            BufferedImage[] arr = frames.get(animation);
            return arr == null ? new BufferedImage[0] : arr;
        }
    }
}