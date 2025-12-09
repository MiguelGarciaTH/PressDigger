package arquivo.processor;

import java.awt.image.BufferedImage;

/**
 * Utility to detect whether an image is "blank" (uniform color or fully transparent)
 * with configurable tolerance and sampling.
 */
public final class ImageBlankDetector {

    public ImageBlankDetector() {}

    /**
     * Return true if image is blank.
     *
     * @param img                          BufferedImage to test
     * @param tolerancePerChannel          max difference per R/G/B/A channel (0-255)
     * @param allowedNonMatchingFraction   fraction of sampled pixels allowed to differ (0.0 - 1.0)
     * @param sampleStride                 check every sampleStride-th pixel (1 = check all)
     */
    public static boolean isBlank(BufferedImage img, int tolerancePerChannel,
                                  double allowedNonMatchingFraction, int sampleStride) {
        if (img == null) throw new IllegalArgumentException("img must not be null");

        // sanitize inputs
        tolerancePerChannel = Math.max(0, Math.min(255, tolerancePerChannel));
        allowedNonMatchingFraction = Math.max(0.0, Math.min(1.0, allowedNonMatchingFraction));
        sampleStride = Math.max(1, sampleStride);

        final int w = img.getWidth();
        final int h = img.getHeight();
        if (w == 0 || h == 0) return true;

        final int samplesX = (w + sampleStride - 1) / sampleStride;
        final int samplesY = (h + sampleStride - 1) / sampleStride;
        final long totalSamples = (long) samplesX * (long) samplesY;
        final long allowedMismatches = (long) Math.floor(allowedNonMatchingFraction * totalSamples);

        // bulk read pixels to avoid repeated native calls
        final int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);

        // choose reference pixel (top-left). For most news screenshots this works well;
        // if needed, enhance to pick modal/median color.
        final int ref = pixels[0];
        final int refA = (ref >>> 24) & 0xFF;
        final int refR = (ref >>> 16) & 0xFF;
        final int refG = (ref >>> 8) & 0xFF;
        final int refB = ref & 0xFF;

        long mismatches = 0L;

        // fast path: exact equality required and no mismatches allowed
        final boolean exactFastPath = (tolerancePerChannel == 0 && allowedMismatches == 0);

        for (int y = 0; y < h; y += sampleStride) {
            final int rowOffset = y * w;
            for (int x = 0; x < w; x += sampleStride) {
                final int pix = pixels[rowOffset + x];
                if (exactFastPath) {
                    if (pix != ref) return false;
                } else {
                    final int a = (pix >>> 24) & 0xFF;
                    final int r = (pix >>> 16) & 0xFF;
                    final int g = (pix >>> 8) & 0xFF;
                    final int b = pix & 0xFF;
                    if (!matches(refR, refG, refB, refA, r, g, b, a, tolerancePerChannel)) {
                        mismatches++;
                        if (mismatches > allowedMismatches) return false; // early exit
                    }
                }
            }
        }

        return mismatches <= allowedMismatches;
    }

    private static boolean matches(int r1, int g1, int b1, int a1,
                                   int r2, int g2, int b2, int a2, int tol) {
        return Math.abs(r1 - r2) <= tol
                && Math.abs(g1 - g2) <= tol
                && Math.abs(b1 - b2) <= tol
                && Math.abs(a1 - a2) <= tol;
    }
}