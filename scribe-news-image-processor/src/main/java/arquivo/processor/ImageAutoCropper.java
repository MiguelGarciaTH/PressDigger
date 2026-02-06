package arquivo.processor;

import java.awt.image.BufferedImage;

/**
 * Aggressive cropper that removes white/uniform side margins while preserving the top.
 */
public final class ImageAutoCropper {

    private ImageAutoCropper() {}

    /**
     * Aggressively crop side margins, NEVER cropping the top.
     */
    public static BufferedImage autoCrop(BufferedImage img, int tolerance,
                                         int marginPixels, int minCropThreshold) {
        if (img == null) return null;

        final int w = img.getWidth();
        final int h = img.getHeight();

        if (w == 0 || h == 0) return img;

        final int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);

        // Determine background color from edges
        final int bgColor = determineBackgroundColor(pixels, w, h);
        final int bgR = (bgColor >>> 16) & 0xFF;
        final int bgG = (bgColor >>> 8) & 0xFF;
        final int bgB = bgColor & 0xFF;

        // Find content bounds aggressively
        int contentLeft = findLeftBoundaryAggressive(pixels, w, h, bgR, bgG, bgB, tolerance);
        int contentRight = findRightBoundaryAggressive(pixels, w, h, bgR, bgG, bgB, tolerance);
        int contentBottomBound = findBottomBoundary(pixels, w, h, bgR, bgG, bgB, tolerance);

        // Check if crop is significant
        int leftCrop = contentLeft;
        int rightCrop = w - contentRight - 1;
        int bottomCrop = h - contentBottomBound - 1;

        if (leftCrop < minCropThreshold && rightCrop < minCropThreshold && bottomCrop < minCropThreshold) {
            return img;
        }

        // Apply margin
        contentLeft = Math.max(0, contentLeft - marginPixels);
        contentRight = Math.min(w - 1, contentRight + marginPixels);
        int contentTop = 0; // NEVER crop top
        int contentBottom = Math.min(h - 1, contentBottomBound + marginPixels);

        // Validate
        if (contentLeft >= contentRight || contentTop >= contentBottom) {
            return img;
        }

        int newWidth = contentRight - contentLeft + 1;
        int newHeight = contentBottom - contentTop + 1;

        return img.getSubimage(contentLeft, contentTop, newWidth, newHeight);
    }

    /**
     * Aggressively find left boundary - column must have SUBSTANTIAL content.
     */
    private static int findLeftBoundaryAggressive(int[] pixels, int w, int h,
                                                  int bgR, int bgG, int bgB, int tolerance) {
        // Analyze each column for content density
        for (int x = 0; x < w; x++) {
            int contentPixels = 0;
            int totalPixels = 0;

            // Sample the middle 60% of height (skip top 20% and bottom 20%)
            int startY = (int)(h * 0.2);
            int endY = (int)(h * 0.8);

            for (int y = startY; y < endY; y++) {
                totalPixels++;
                int pix = pixels[y * w + x];
                int r = (pix >>> 16) & 0xFF;
                int g = (pix >>> 8) & 0xFF;
                int b = pix & 0xFF;

                if (Math.abs(r - bgR) > tolerance ||
                        Math.abs(g - bgG) > tolerance ||
                        Math.abs(b - bgB) > tolerance) {
                    contentPixels++;
                }
            }

            // Column must have at least 20% content density
            double contentDensity = (double)contentPixels / totalPixels;
            if (contentDensity >= 0.20) {
                return x;
            }
        }

        return 0;
    }

    /**
     * Aggressively find right boundary - column must have SUBSTANTIAL content.
     */
    private static int findRightBoundaryAggressive(int[] pixels, int w, int h,
                                                   int bgR, int bgG, int bgB, int tolerance) {
        for (int x = w - 1; x >= 0; x--) {
            int contentPixels = 0;
            int totalPixels = 0;

            // Sample the middle 60% of height
            int startY = (int)(h * 0.2);
            int endY = (int)(h * 0.8);

            for (int y = startY; y < endY; y++) {
                totalPixels++;
                int pix = pixels[y * w + x];
                int r = (pix >>> 16) & 0xFF;
                int g = (pix >>> 8) & 0xFF;
                int b = pix & 0xFF;

                if (Math.abs(r - bgR) > tolerance ||
                        Math.abs(g - bgG) > tolerance ||
                        Math.abs(b - bgB) > tolerance) {
                    contentPixels++;
                }
            }

            // Column must have at least 20% content density
            double contentDensity = (double)contentPixels / totalPixels;
            if (contentDensity >= 0.20) {
                return x;
            }
        }

        return w - 1;
    }

    /**
     * Find bottom boundary by looking for rows with substantial content.
     */
    private static int findBottomBoundary(int[] pixels, int w, int h,
                                          int bgR, int bgG, int bgB, int tolerance) {
        // Start from bottom and find first row with substantial content
        for (int y = h - 1; y >= (int)(h * 0.5); y--) { // Don't go above middle
            int contentPixels = 0;
            int rowOffset = y * w;

            for (int x = 0; x < w; x++) {
                int pix = pixels[rowOffset + x];
                int r = (pix >>> 16) & 0xFF;
                int g = (pix >>> 8) & 0xFF;
                int b = pix & 0xFF;

                if (Math.abs(r - bgR) > tolerance ||
                        Math.abs(g - bgG) > tolerance ||
                        Math.abs(b - bgB) > tolerance) {
                    contentPixels++;
                }
            }

            // Row must have at least 15% content
            double contentDensity = (double)contentPixels / w;
            if (contentDensity >= 0.15) {
                return y;
            }
        }

        return h - 1;
    }

    /**
     * Determine background color by sampling left and right edges heavily.
     */
    private static int determineBackgroundColor(int[] pixels, int w, int h) {
        // Sample left and right edges at multiple heights
        int[] samples = new int[20];
        int idx = 0;

        // Left edge samples
        for (int i = 0; i < 5; i++) {
            int y = (h * i) / 5;
            samples[idx++] = pixels[y * w + 0];           // Left edge
            samples[idx++] = pixels[y * w + (w - 1)];     // Right edge
        }

        // Corners
        samples[idx++] = pixels[0];                        // top-left
        samples[idx++] = pixels[w - 1];                    // top-right
        samples[idx++] = pixels[(h - 1) * w];              // bottom-left
        samples[idx++] = pixels[(h - 1) * w + (w - 1)];    // bottom-right

        // Find most common color (likely white/background)
        int candidate = samples[0];
        int maxCount = 1;

        for (int i = 0; i < samples.length; i++) {
            int count = 0;
            for (int j = 0; j < samples.length; j++) {
                if (colorMatches(samples[i], samples[j], 20)) {
                    count++;
                }
            }
            if (count > maxCount) {
                maxCount = count;
                candidate = samples[i];
            }
        }

        return candidate;
    }

    private static boolean colorMatches(int c1, int c2, int tolerance) {
        int r1 = (c1 >>> 16) & 0xFF;
        int g1 = (c1 >>> 8) & 0xFF;
        int b1 = c1 & 0xFF;
        int r2 = (c2 >>> 16) & 0xFF;
        int g2 = (c2 >>> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        return Math.abs(r1 - r2) <= tolerance &&
                Math.abs(g1 - g2) <= tolerance &&
                Math.abs(b1 - b2) <= tolerance;
    }
}