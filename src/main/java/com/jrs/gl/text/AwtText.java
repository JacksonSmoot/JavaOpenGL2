package com.jrs.gl.text;

import com.jrs.gl.resources.texture.GLBufferedTexture;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AwtText {

    private AwtText() {}

    /** Loaded font + size. Keep this around; creating Fonts repeatedly is expensive. */
    public static Font loadTtf(Path ttfPath, float sizePx) {
        try {
            byte[] bytes = Files.readAllBytes(ttfPath);
            Font base = Font.createFont(Font.TRUETYPE_FONT, new java.io.ByteArrayInputStream(bytes));
            return base.deriveFont(Font.PLAIN, sizePx);
        } catch (FontFormatException | IOException e) {
            throw new RuntimeException("Failed to load TTF: " + ttfPath, e);
        }
    }

    /** Simple width/height measurement (no raster). */
    public static Dimension measureText(Font font, String text) {
        if (text == null) text = "";
        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = font.createGlyphVector(frc, text);
        Rectangle2D vb = gv.getVisualBounds();

        int w = (int) Math.ceil(vb.getWidth());
        int h = (int) Math.ceil(vb.getHeight());
        if (w < 1) w = 1;
        if (h < 1) h = 1;
        return new Dimension(w, h);
    }

    /**
     * Renders text into a LONG-LIVED direct RGBA buffer (top-left origin image).
     *
     * IMPORTANT on macOS Retina:
     * Use scale=2 for crisp text.
     *
     * @param font AWT font (already sized in logical px)
     * @param text text to render
     * @param color fill color (alpha respected)
     * @param padding padding in logical pixels
     * @param scale oversample factor (2 recommended on macOS)
     * @param premultiplied if true: render into ARGB_PRE (recommended) and use GL blend ONE, ONE_MINUS_SRC_ALPHA
     */
    public static TextBitmap renderTextRgba(Font font, String text, Color color, int padding, int scale, boolean premultiplied) {
        if (text == null) text = "";
        if (color == null) color = Color.WHITE;
        if (padding < 0) padding = 0;
        if (scale < 1) scale = 1;

        // Scale font + padding
        Font scaledFont = font.deriveFont(font.getStyle(), font.getSize2D() * scale);
        int pad = padding * scale;

        FontRenderContext frc = new FontRenderContext(null, true, true);
        GlyphVector gv = scaledFont.createGlyphVector(frc, text);
        Rectangle2D vb = gv.getVisualBounds();

        int w = (int) Math.ceil(vb.getWidth()) + pad * 2;
        int h = (int) Math.ceil(vb.getHeight()) + pad * 2;
        if (w < 1) w = 1;
        if (h < 1) h = 1;

        float drawX = (float) (-vb.getX()) + pad;
        float drawY = (float) (-vb.getY()) + pad;

        int imgType = premultiplied ? BufferedImage.TYPE_INT_ARGB_PRE : BufferedImage.TYPE_INT_ARGB;
        BufferedImage img = new BufferedImage(w, h, imgType);

        Graphics2D g = img.createGraphics();
        try {
            g.setComposite(AlphaComposite.Src);
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect(0, 0, w, h);

            // Quality settings
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Text AA: avoid LCD subpixel because it often looks worse after texture sampling
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            g.setFont(scaledFont);
            g.setColor(color);
            g.drawGlyphVector(gv, drawX, drawY);
        } finally {
            g.dispose();
        }

        ByteBuffer rgba = argbBufferedImageToRgba(img);

        // Metrics (scaled) -> convert back to logical pixels
        FontMetrics fm = getFontMetrics(scaledFont);
        int ascent = fm.getAscent() / scale;
        int descent = fm.getDescent() / scale;
        int lineHeight = fm.getHeight() / scale;

        int logicalW = Math.max(1, w / scale);
        int logicalH = Math.max(1, h / scale);

        return new TextBitmap(rgba, w, h, logicalW, logicalH, ascent, descent, lineHeight, premultiplied, scale);
    }

    /** Convenience defaults for macOS UI text: scale=2, premultiplied=true. */
    public static TextBitmap renderTextRgba(Font font, String text, Color color, int padding) {
        return renderTextRgba(font, text, color, padding, 2, true);
    }

    /** Convenience: render text and upload to GLBufferedTexture. */
    public static GLBufferedTexture uploadTextToTexture(Font font, String text, Color color, int padding) {
        TextBitmap bmp = renderTextRgba(font, text, color, padding, 2, true);
        GLBufferedTexture tex = new GLBufferedTexture(bmp.width, bmp.height);
        tex.upload(bmp.rgba);
        tex.send();
        return tex;
    }

    // ---------------- internal helpers ----------------

    private static FontMetrics getFontMetrics(Font font) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        try {
            g.setFont(font);
            return g.getFontMetrics();
        } finally {
            g.dispose();
        }
    }

    /** Returns a DIRECT ByteBuffer in RGBA order (row-major, top-left origin). */
    private static ByteBuffer argbBufferedImageToRgba(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);

        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());

        for (int p : pixels) {
            int a = (p >>> 24) & 0xFF;
            int r = (p >>> 16) & 0xFF;
            int g = (p >>> 8) & 0xFF;
            int b = (p) & 0xFF;

            buf.put((byte) r);
            buf.put((byte) g);
            buf.put((byte) b);
            buf.put((byte) a);
        }
        buf.flip();
        return buf;
    }

    // ---------------- result type ----------------

    public static final class TextBitmap {
        public final ByteBuffer rgba; // DIRECT, RGBA, long-lived
        public final int width;       // actual texture width (scaled)
        public final int height;      // actual texture height (scaled)

        // Recommended draw size in logical pixels (width/scale, height/scale)
        public final int logicalWidth;
        public final int logicalHeight;

        public final int ascentPx;
        public final int descentPx;
        public final int lineHeightPx;

        public final boolean premultiplied;
        public final int scale;

        public TextBitmap(ByteBuffer rgba, int width, int height,
                          int logicalWidth, int logicalHeight,
                          int ascentPx, int descentPx, int lineHeightPx,
                          boolean premultiplied, int scale) {
            this.rgba = rgba;
            this.width = width;
            this.height = height;
            this.logicalWidth = logicalWidth;
            this.logicalHeight = logicalHeight;
            this.ascentPx = ascentPx;
            this.descentPx = descentPx;
            this.lineHeightPx = lineHeightPx;
            this.premultiplied = premultiplied;
            this.scale = scale;
        }
    }
}