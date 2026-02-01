package arquivo.processor;

import java.awt.image.BufferedImage;

/**
 * Detects blank or content-poor images (uniform colors, mostly whitespace, or just headers/footers).
 */
public final class ImageBlankDetector {

    public ImageBlankDetector() {}

    /**
     * Comprehensive check: detects truly blank images OR images with insufficient content.
     *
     * @param img                          BufferedImage to test
     * @param tolerancePerChannel          max difference per R/G/B/A channel for uniformity (0-255)
     * @param allowedNonMatchingFraction   fraction of pixels allowed to differ for uniformity
     * @param sampleStride                 pixel sampling stride (1 = check all pixels)
     * @param minContentHeightFraction     minimum fraction of height that must have content (e.g., 0.3 = 30%)
     * @param edgeTrimFraction             fraction of height to ignore at top/bottom (e.g., 0.15 = 15% each)
     */
    public static boolean isBlankOrUseless(BufferedImage img,
                                           int tolerancePerChannel,
                                           double allowedNonMatchingFraction,
                                           int sampleStride,
                                           double minContentHeightFraction,
                                           double edgeTrimFraction) {
        if (img == null) throw new IllegalArgumentException("img must not be null");

        // First check: is it uniformly blank?
        if (isBlank(img, tolerancePerChannel, allowedNonMatchingFraction, sampleStride)) {
            return true;
        }

        // Second check: does it have sufficient content density?
        return !hasSufficientContent(img, minContentHeightFraction, edgeTrimFraction);
    }

    /**
     * Original blank detection (uniform color).
     */
    public static boolean isBlank(BufferedImage img, int tolerancePerChannel,
                                  double allowedNonMatchingFraction, int sampleStride) {
        if (img == null) throw new IllegalArgumentException("img must not be null");

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

        final int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
        final int ref = pixels[0];
        final int refA = (ref >>> 24) & 0xFF;
        final int refR = (ref >>> 16) & 0xFF;
        final int refG = (ref >>> 8) & 0xFF;
        final int refB = ref & 0xFF;

        long mismatches = 0L;
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
                        if (mismatches > allowedMismatches) return false;
                    }
                }
            }
        }

        return mismatches <= allowedMismatches;
    }

    /**
     * Check if image has sufficient content density (not just header/footer).
     *
     * Analyzes horizontal bands and checks if enough of them contain varied content.
     */
    private static boolean hasSufficientContent(BufferedImage img,
                                                double minContentHeightFraction,
                                                double edgeTrimFraction) {
        final int w = img.getWidth();
        final int h = img.getHeight();

        if (w == 0 || h == 0) return false;

        // Trim top and bottom edges (often just headers/footers)
        final int trimPixels = (int) (h * edgeTrimFraction);
        final int startY = trimPixels;
        final int endY = h - trimPixels;
        final int contentHeight = endY - startY;

        if (contentHeight <= 0) return false;

        final int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);

        // Divide into horizontal bands (e.g., 20 bands)
        final int numBands = Math.min(20, contentHeight / 10);
        if (numBands == 0) return false;

        final int bandHeight = contentHeight / numBands;
        int bandsWithContent = 0;

        for (int band = 0; band < numBands; band++) {
            final int bandStartY = startY + (band * bandHeight);
            final int bandEndY = Math.min(bandStartY + bandHeight, endY);

            if (bandHasContent(pixels, w, bandStartY, bandEndY)) {
                bandsWithContent++;
            }
        }

        final double contentFraction = (double) bandsWithContent / numBands;
        return contentFraction >= minContentHeightFraction;
    }

    /**
     * Check if a horizontal band has content (varied colors, likely text/images).
     */
    private static boolean bandHasContent(int[] pixels, int width, int startY, int endY) {
        // Sample pixels in this band and check color variance
        final int sampleSize = Math.min(100, width * (endY - startY));
        final int[] samples = new int[sampleSize];
        int sampleIdx = 0;

        final int step = Math.max(1, (width * (endY - startY)) / sampleSize);

        for (int y = startY; y < endY && sampleIdx < sampleSize; y++) {
            final int rowOffset = y * width;
            for (int x = 0; x < width && sampleIdx < sampleSize; x += step) {
                samples[sampleIdx++] = pixels[rowOffset + x];
            }
        }

        // Check color variance: if all pixels are too similar, it's likely blank
        return hasColorVariance(samples, sampleIdx);
    }

    /**
     * Check if sampled pixels have sufficient color variance.
     */
    private static boolean hasColorVariance(int[] samples, int count) {
        if (count < 2) return false;

        long sumR = 0, sumG = 0, sumB = 0;

        // Calculate mean
        for (int i = 0; i < count; i++) {
            final int pix = samples[i];
            sumR += (pix >>> 16) & 0xFF;
            sumG += (pix >>> 8) & 0xFF;
            sumB += pix & 0xFF;
        }

        final double meanR = (double) sumR / count;
        final double meanG = (double) sumG / count;
        final double meanB = (double) sumB / count;

        // Calculate standard deviation
        double varR = 0, varG = 0, varB = 0;
        for (int i = 0; i < count; i++) {
            final int pix = samples[i];
            final int r = (pix >>> 16) & 0xFF;
            final int g = (pix >>> 8) & 0xFF;
            final int b = pix & 0xFF;

            varR += Math.pow(r - meanR, 2);
            varG += Math.pow(g - meanG, 2);
            varB += Math.pow(b - meanB, 2);
        }

        final double stdR = Math.sqrt(varR / count);
        final double stdG = Math.sqrt(varG / count);
        final double stdB = Math.sqrt(varB / count);

        // Threshold: if std dev is too low, it's uniform (blank/useless)
        // Typical text on white background has std dev > 30
        final double threshold = 25.0;
        return (stdR > threshold || stdG > threshold || stdB > threshold);
    }

    private static boolean matches(int r1, int g1, int b1, int a1,
                                   int r2, int g2, int b2, int a2, int tol) {
        return Math.abs(r1 - r2) <= tol
                && Math.abs(g1 - g2) <= tol
                && Math.abs(b1 - b2) <= tol
                && Math.abs(a1 - a2) <= tol;
    }
}