package arquivo.processor;

import java.awt.image.BufferedImage;

/**
 * Smart cropper that finds the main content block (text) and crops around it,
 * preserving the top of the image (headers/banners) while removing side margins.
 */
public final class ImageAutoCropper {

    private ImageAutoCropper() {}

    /**
     * Smart crop focusing on main content, NEVER cropping the top.
     *
     * @param img              BufferedImage to crop
     * @param tolerance        color tolerance for background detection
     * @param marginPixels     pixels to keep as margin after cropping
     * @param minCropThreshold minimum pixels to crop
     * @return cropped BufferedImage
     */
    public static BufferedImage autoCrop(BufferedImage img, int tolerance,
                                         int marginPixels, int minCropThreshold) {
        if (img == null) return null;

        final int w = img.getWidth();
        final int h = img.getHeight();

        if (w == 0 || h == 0) return img;

        // Get all pixels once
        final int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);

        // Determine background color
        final int bgColor = determineBackgroundColor(pixels, w, h);
        final int bgR = (bgColor >>> 16) & 0xFF;
        final int bgG = (bgColor >>> 8) & 0xFF;
        final int bgB = bgColor & 0xFF;

        // Find the main content block
        // Analyze the middle section (skip top 10% for analysis, but we'll keep it)
        int analyzeFromY = (int)(h * 0.1);
        int analyzeToY = (int)(h * 0.95); // Skip bottom 5%

        ContentBounds bounds = findMainContentBlock(pixels, w, h, bgR, bgG, bgB, tolerance, analyzeFromY, analyzeToY);

        // NEVER crop top - always start from 0
        bounds.top = 0;

        // Check if crop is significant
        int leftCrop = bounds.left;
        int rightCrop = w - bounds.right - 1;
        int bottomCrop = h - bounds.bottom - 1;

        if (leftCrop < minCropThreshold && rightCrop < minCropThreshold && bottomCrop < minCropThreshold) {
            return img;
        }

        // Apply margin (but keep top at 0)
        int contentLeft = Math.max(0, bounds.left - marginPixels);
        int contentRight = Math.min(w - 1, bounds.right + marginPixels);
        int contentTop = 0; // NEVER crop top
        int contentBottom = Math.min(h - 1, bounds.bottom + marginPixels);

        // Validate
        if (contentLeft >= contentRight || contentTop >= contentBottom) {
            return img;
        }

        int newWidth = contentRight - contentLeft + 1;
        int newHeight = contentBottom - contentTop + 1;

        return img.getSubimage(contentLeft, contentTop, newWidth, newHeight);
    }

    private static class ContentBounds {
        int left, right, top, bottom;

        ContentBounds(int left, int right, int top, int bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }

    /**
     * Find main content block by analyzing content density in a specific vertical range.
     */
    private static ContentBounds findMainContentBlock(int[] pixels, int w, int h,
                                                      int bgR, int bgG, int bgB, int tolerance,
                                                      int analyzeFromY, int analyzeToY) {
        // Analyze content density in horizontal slices within the analysis range
        int[] contentPixelsPerRow = new int[h];

        for (int y = analyzeFromY; y < analyzeToY; y++) {
            int rowOffset = y * w;
            int contentCount = 0;

            for (int x = 0; x < w; x++) {
                int pix = pixels[rowOffset + x];
                int r = (pix >>> 16) & 0xFF;
                int g = (pix >>> 8) & 0xFF;
                int b = pix & 0xFF;

                if (Math.abs(r - bgR) > tolerance ||
                        Math.abs(g - bgG) > tolerance ||
                        Math.abs(b - bgB) > tolerance) {
                    contentCount++;
                }
            }
            contentPixelsPerRow[y] = contentCount;
        }

        // Find bottom bound - where content ends
        int contentBottom = h - 1;

        // Calculate average content density
        int avgContentPerRow = 0;
        int countedRows = 0;
        for (int y = analyzeFromY; y < analyzeToY; y++) {
            if (contentPixelsPerRow[y] > w * 0.1) { // At least 10% of width has content
                avgContentPerRow += contentPixelsPerRow[y];
                countedRows++;
            }
        }
        if (countedRows > 0) {
            avgContentPerRow /= countedRows;
        }

        int densityThreshold = (int)(avgContentPerRow * 0.3); // 30% of average density

        // Find where consistent content ends (from bottom up)
        for (int y = analyzeToY - 1; y >= analyzeFromY; y--) {
            if (contentPixelsPerRow[y] >= densityThreshold) {
                contentBottom = y;
                break;
            }
        }

        // Now find left/right bounds across the ENTIRE height (including top)
        // This ensures we catch all content including headers
        int[] contentPixelsPerCol = new int[w];

        for (int x = 0; x < w; x++) {
            int contentCount = 0;

            // Scan entire height from 0 to contentBottom
            for (int y = 0; y <= contentBottom; y++) {
                int pix = pixels[y * w + x];
                int r = (pix >>> 16) & 0xFF;
                int g = (pix >>> 8) & 0xFF;
                int b = pix & 0xFF;

                if (Math.abs(r - bgR) > tolerance ||
                        Math.abs(g - bgG) > tolerance ||
                        Math.abs(b - bgB) > tolerance) {
                    contentCount++;
                }
            }
            contentPixelsPerCol[x] = contentCount;
        }

        // Calculate average content density per column
        int avgContentPerCol = 0;
        int countedCols = 0;
        for (int x = 0; x < w; x++) {
            if (contentPixelsPerCol[x] > 0) {
                avgContentPerCol += contentPixelsPerCol[x];
                countedCols++;
            }
        }
        if (countedCols > 0) {
            avgContentPerCol /= countedCols;
        }

        // Find left/right bounds where there's substantial content
        int colDensityThreshold = Math.max(3, (int)(avgContentPerCol * 0.15)); // At least 15% of average or 3 pixels

        int contentLeft = 0;
        for (int x = 0; x < w; x++) {
            if (contentPixelsPerCol[x] >= colDensityThreshold) {
                contentLeft = x;
                break;
            }
        }

        int contentRight = w - 1;
        for (int x = w - 1; x >= contentLeft; x--) {
            if (contentPixelsPerCol[x] >= colDensityThreshold) {
                contentRight = x;
                break;
            }
        }

        // Top is always 0 (never crop top)
        return new ContentBounds(contentLeft, contentRight, 0, contentBottom);
    }

    /**
     * Determine background color from image corners and edges.
     */
    private static int determineBackgroundColor(int[] pixels, int w, int h) {
        // Sample more points for better background detection
        int[] samples = new int[8];

        // Four corners
        samples[0] = pixels[0];                          // top-left
        samples[1] = pixels[w - 1];                      // top-right
        samples[2] = pixels[(h - 1) * w];                // bottom-left
        samples[3] = pixels[(h - 1) * w + (w - 1)];      // bottom-right

        // Edge midpoints
        samples[4] = pixels[w / 2];                      // top-center
        samples[5] = pixels[(h / 2) * w];                // left-center
        samples[6] = pixels[(h / 2) * w + (w - 1)];      // right-center
        samples[7] = pixels[(h - 1) * w + (w / 2)];      // bottom-center

        // Find the most common color
        int candidate = samples[0];
        int maxCount = 1;

        for (int i = 0; i < samples.length; i++) {
            int count = 0;
            for (int j = 0; j < samples.length; j++) {
                if (colorMatches(samples[i], samples[j], 15)) {
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