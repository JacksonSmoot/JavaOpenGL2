package com.jrs.gl.util;

import org.lwjgl.BufferUtils;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

public class GpuIO {

    public static int createEmptyTexture(int width, int height) {

        // Generate texture ID
        int textureId = glGenTextures();

        // Bind the texture
        glBindTexture(GL_TEXTURE_2D, textureId);

        // Create empty texture with default values (black/transparent)
        glTexImage2D(
                GL_TEXTURE_2D,      // Target
                0,                  // Mipmap level
                GL_RGBA8,           // Internal format
                width,              // Width
                height,             // Height
                0,                  // Border (must be 0)
                GL_RGBA,            // Format
                GL_UNSIGNED_BYTE,   // Type
                (java.nio.ByteBuffer) null  // Data (null = empty)
        );

        // Set texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR); // GL_NEAREST, GL_LINEAR
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
//
//        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
//        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
//
//        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
//        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);

        return textureId;
    }

//    public static GLTexture load(BufferedImage image) throws IOException {
//        int tex = GpuIO.createTextureFromBufferedImage(image);
//        if(tex == 0) throw new IOException("Failed to load texture");
//        return new GLTexture(tex);
//    }

//    public static GLImage fromBufferedImage(BufferedImage image) throws IOException {
//        return new GLImage(GLTexture.load(image), image.getWidth(), image.getHeight());
//    }
//
//    public static GLImage load(Path path) throws IOException {
//        BufferedImage image = ImageIO.read(path.toFile());
//        if(image == null){
//            throw new IOException("Failed to load image (image was null)");
//        }
//
//        return new GLImage(GLTexture.load(image), image.getWidth(), image.getHeight());
//    }

    public static ByteBuffer bufferedImageToRGBA(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        int[] argb = new int[w * h];
        image.getRGB(0, 0, w, h, argb, 0, w);

        ByteBuffer rgba = BufferUtils.createByteBuffer(w * h * 4);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = argb[y * w + x]; // ARGB

                byte a = (byte) ((pixel >> 24) & 0xFF);
                byte r = (byte) ((pixel >> 16) & 0xFF);
                byte g = (byte) ((pixel >>  8) & 0xFF);
                byte b = (byte) ( pixel        & 0xFF);

                rgba.put(r).put(g).put(b).put(a); // RGBA
            }
        }

        // OpenGL's origin is bottom-left; BufferedImage is top-left.
        // Flip vertically while copying so it appears right-side-up.
//        for (int y = h - 1; y >= 0; y--) {
//            for (int x = 0; x < w; x++) {
//                int pixel = argb[y * w + x]; // ARGB
//
//                byte a = (byte) ((pixel >> 24) & 0xFF);
//                byte r = (byte) ((pixel >> 16) & 0xFF);
//                byte g = (byte) ((pixel >>  8) & 0xFF);
//                byte b = (byte) ( pixel        & 0xFF);
//
//                rgba.put(r).put(g).put(b).put(a); // RGBA
//            }
//        }

        rgba.flip();
        return rgba;
    }

    public static int createTextureFromBufferedImage(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer pixels = bufferedImageToRGBA(img);

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);

        // texture sampling behavior
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST); // or GL_LINEAR
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Upload pixel data to GPU
        glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RGBA8,      // internal format on GPU
                w, h,
                0,
                GL_RGBA,       // format of our ByteBuffer
                GL_UNSIGNED_BYTE,
                pixels
        );

        glBindTexture(GL_TEXTURE_2D, 0);
        return texId;
    }
}
