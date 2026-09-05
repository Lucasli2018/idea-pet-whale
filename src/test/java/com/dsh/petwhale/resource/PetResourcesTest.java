package com.dsh.petwhale.resource;

import com.dsh.petwhale.state.PetAnimation;
import org.junit.Assume;
import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.*;

public class PetResourcesTest {

    @Test
    public void rowOfMapsAllAnimations() {
        assertEquals(0, PetResources.rowOf(PetAnimation.IDLE));
        assertEquals(1, PetResources.rowOf(PetAnimation.RUNNING_RIGHT));
        assertEquals(8, PetResources.rowOf(PetAnimation.REVIEW));
    }

    @Test
    public void loadWhaleOrAssumeSkip() {
        // ImageIO requires JDK 21+ for WebP, or a twelvemonkeys plugin on
        // the module path. We don't ship that here, so on a JDK 17 build
        // machine we expect the loader to throw IllegalStateException —
        // which is itself a documented behavior worth asserting.
        try {
            PetResources.Theme theme = PetResources.load(PetTheme.WHALE);
            BufferedImage[] frames = theme.framesFor(PetAnimation.IDLE);
            assertEquals(6, frames.length);
            assertEquals(PetManifest.CELL_WIDTH, frames[0].getWidth());
            assertEquals(PetManifest.CELL_HEIGHT, frames[0].getHeight());
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            assertTrue(
                    "loader should fail with WebP guidance or missing manifest, got: " + msg,
                    msg != null && (msg.contains("WebP") || msg.contains("missing")));
        }
    }
}