package com.jrs.gl.resources.texture;

import com.jrs.gl.util.GpuIO;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

public class GLBufferedTexture extends GLImage {
    private ByteBuffer rgba;
    private int[] argb;

    public GLBufferedTexture(int textureId, int width, int height) {
        super(textureId, width, height);
        rgba = ByteBuffer.allocateDirect(width * height * 4);
        argb = new int[width * height];
    }

    public GLBufferedTexture(int width, int height) {
        this(GpuIO.createEmptyTexture(width, height), width, height);
    }

    public void reload(){
        free();
        super.textureID = GpuIO.createEmptyTexture(width, height);
    }

    public void upload(BufferedImage image){
        int w = image.getWidth();
        int h = image.getHeight();
        image.getRGB(0, 0, w, h, argb, 0, w);
        rgba.clear();

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
        rgba.flip();
    }

    public void upload(ByteBuffer image){
        rgba.clear();
        rgba.put(image);
        rgba.flip();
    }

    public void resize(int width, int height) {
        super.width = width;
        super.height = height;
        rgba = ByteBuffer.allocateDirect(width * height * 4);
        argb = new int[width * height];
        free();
        super.textureID = GpuIO.createEmptyTexture(width, height);
    }

    public ByteBuffer getRgba() {
        return rgba;
    }

    public void send(){
        glBindTexture(GL_TEXTURE_2D, super.textureID);
        // Upload pixel data to the texture
        glTexSubImage2D(
                GL_TEXTURE_2D,      // Target
                0,                  // Mipmap level
                0,                  // X offset
                0,                  // Y offset
                width,              // Width
                height,             // Height
                GL_RGBA,            // Format
                GL_UNSIGNED_BYTE,   // Type
                rgba           // Pixel data buffer
        );

        // Unbind texture
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public GLImage asGLImage() {
        return new GLImage(textureID, width, height);
    }
}
