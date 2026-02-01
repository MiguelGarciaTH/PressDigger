package arquivo.processor;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Utility to automatically crop whitespace/uniform borders from images.
 */
public final class ImageAutoCropper {

    private ImageAutoCropper() {}

    /**
     * Automatically crop uniform borders (whitespace) from an image.
     *
     * @param img                  BufferedImage to crop
     * @param tolerance            color tolerance per channel (0-255) for what counts as "background"
     * @param marginPixels         pixels to keep as margin after cropping
     * @param minCropThreshold     minimum pixels to crop (avoid cropping tiny amounts)
     * @return cropped BufferedImage, or original if no significant crop detected
     */
    public static BufferedImage autoCrop(BufferedImage img, int tolerance,
                                         int marginPixels, int minCropThreshold) {
        if (img == null) return null;

        final int w = img.getWidth();
        final int h = img.getHeight();

        if (w == 0 || h == 0) return img;

        // Get all pixels once
        final int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);

        // Determine background color (use corners, most screenshots have uniform borders)
        final int bgColor = determineBackgroundColor(pixels, w, h);
        final int bgR = (bgColor >>> 16) & 0xFF;
        final int bgG = (bgColor >>> 8) & 0xFF;
        final int bgB = bgColor & 0xFF;

        // Find content bounds
        int contentLeft = findLeftBoundary(pixels, w, h, bgR, bgG, bgB, tolerance);
        int contentRight = findRightBoundary(pixels, w, h, bgR, bgG, bgB, tolerance);
        int contentTop = findTopBoundary(pixels, w, h, bgR, bgG, bgB, tolerance);
        int contentBottom = findBottomBoundary(pixels, w, h, bgR, bgG, bgB, tolerance);

        // Check if crop is significant enough
        int leftCrop = contentLeft;
        int rightCrop = w - contentRight - 1;
        int topCrop = contentTop;
        int bottomCrop = h - contentBottom - 1;

        if (leftCrop < minCropThreshold && rightCrop < minCropThreshold &&
                topCrop < minCropThreshold && bottomCrop < minCropThreshold) {
            return img; // Not worth cropping
        }

        // Apply margin (but don't exceed original bounds)
        contentLeft = Math.max(0, contentLeft - marginPixels);
        contentRight = Math.min(w - 1, contentRight + marginPixels);
        contentTop = Math.max(0, contentTop - marginPixels);
        contentBottom = Math.min(h - 1, contentBottom + marginPixels);

        // Validate bounds
        if (contentLeft >= contentRight || contentTop >= contentBottom) {
            return img; // Invalid crop, return original
        }

        // Perform crop
        int newWidth = contentRight - contentLeft + 1;
        int newHeight = contentBottom - contentTop + 1;

        return img.getSubimage(contentLeft, contentTop, newWidth, newHeight);
    }

    /**
     * Determine background color from image corners.
     */
    private static int determineBackgroundColor(int[] pixels, int w, int h) {
        // Sample the four corners and use the most common color
        int[] corners = new int[4];
        corners[0] = pixels[0];                          // top-left
        corners[1] = pixels[w - 1];                      // top-right
        corners[2] = pixels[(h - 1) * w];                // bottom-left
        corners[3] = pixels[(h - 1) * w + (w - 1)];      // bottom-right

        // Simple mode: find most frequent corner color
        int candidate = corners[0];
        int maxCount = 1;

        for (int i = 0; i < corners.length; i++) {
            int count = 0;
            for (int j = 0; j < corners.length; j++) {
                if (colorMatches(corners[i], corners[j], 10)) {
                    count++;
                }
            }
            if (count > maxCount) {
                maxCount = count;
                candidate = corners[i];
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

    /**
     * Find leftmost column with non-background content.
     */
    private static int findLeftBoundary(int[] pixels, int w, int h,
                                        int bgR, int bgG, int bgB, int tolerance) {
        for (int x = 0; x < w; x++) {
            if (columnHasContent(pixels, w, h, x, bgR, bgG, bgB, tolerance)) {
                return x;
            }
        }
        return 0;
    }

    /**
     * Find rightmost column with non-background content.
     */
    private static int findRightBoundary(int[] pixels, int w, int h,
                                         int bgR, int bgG, int bgB, int tolerance) {
        for (int x = w - 1; x >= 0; x--) {
            if (columnHasContent(pixels, w, h, x, bgR, bgG, bgB, tolerance)) {
                return x;
            }
        }
        return w - 1;
    }

    /**
     * Find topmost row with non-background content.
     */
    private static int findTopBoundary(int[] pixels, int w, int h,
                                       int bgR, int bgG, int bgB, int tolerance) {
        for (int y = 0; y < h; y++) {
            if (rowHasContent(pixels, w, h, y, bgR, bgG, bgB, tolerance)) {
                return y;
            }
        }
        return 0;
    }

    /**
     * Find bottommost row with non-background content.
     */
    private static int findBottomBoundary(int[] pixels, int w, int h,
                                          int bgR, int bgG, int bgB, int tolerance) {
        for (int y = h - 1; y >= 0; y--) {
            if (rowHasContent(pixels, w, h, y, bgR, bgG, bgB, tolerance)) {
                return y;
            }
        }
        return h - 1;
    }

    /**
     * Check if a column has any non-background pixels.
     */
    private static boolean columnHasContent(int[] pixels, int w, int h, int x,
                                            int bgR, int bgG, int bgB, int tolerance) {
        // Sample every 4th pixel for speed
        for (int y = 0; y < h; y += 4) {
            int pix = pixels[y * w + x];
            int r = (pix >>> 16) & 0xFF;
            int g = (pix >>> 8) & 0xFF;
            int b = pix & 0xFF;

            if (Math.abs(r - bgR) > tolerance ||
                    Math.abs(g - bgG) > tolerance ||
                    Math.abs(b - bgB) > tolerance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a row has any non-background pixels.
     */
    private static boolean rowHasContent(int[] pixels, int w, int h, int y,
                                         int bgR, int bgG, int bgB, int tolerance) {
        int rowOffset = y * w;
        // Sample every 4th pixel for speed
        for (int x = 0; x < w; x += 4) {
            int pix = pixels[rowOffset + x];
            int r = (pix >>> 16) & 0xFF;
            int g = (pix >>> 8) & 0xFF;
            int b = pix & 0xFF;

            if (Math.abs(r - bgR) > tolerance ||
                    Math.abs(g - bgG) > tolerance ||
                    Math.abs(b - bgB) > tolerance) {
                return true;
            }
        }
        return false;
    }
}