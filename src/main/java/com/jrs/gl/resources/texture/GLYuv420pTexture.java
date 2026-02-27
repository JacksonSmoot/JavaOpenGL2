package com.jrs.gl.resources.texture;

import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.javacpp.BytePointer;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL30.GL_R8;
import static org.lwjgl.opengl.GL33.*;

public final class GLYuv420pTexture implements AutoCloseable {
    private int texY, texU, texV;
    private int w, h;

    public GLYuv420pTexture(int w, int h) {
        this.w = w;
        this.h = h;
        initTextures();
    }

    public int getW() { return w; }
    public int getH() { return h; }
    public int texY() { return texY; }
    public int texU() { return texU; }
    public int texV() { return texV; }

    private void initTextures() {
        texY = glGenTextures();
        texU = glGenTextures();
        texV = glGenTextures();

        initPlane(texY, w, h);
        initPlane(texU, w / 2, h / 2);
        initPlane(texV, w / 2, h / 2);
    }

    private static void initPlane(int tex, int pw, int ph) {
        glBindTexture(GL_TEXTURE_2D, tex);

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        // Allocate storage once
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R8, pw, ph, 0, GL_RED, GL_UNSIGNED_BYTE, (ByteBuffer) null);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /** Upload from AV_PIX_FMT_YUV420P frame. Must be called on the GL context thread unless you use shared contexts. */
    public void upload(AVFrame frame) {
        uploadPlane(texY, frame.data(0), frame.linesize(0), w, h);
        uploadPlane(texU, frame.data(1), frame.linesize(1), w / 2, h / 2);
        uploadPlane(texV, frame.data(2), frame.linesize(2), w / 2, h / 2);

        // restore defaults so you don't break other texture uploads
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    }

    private static void uploadPlane(int tex, BytePointer data, int lineSizeBytes, int pw, int ph) {
        glBindTexture(GL_TEXTURE_2D, tex);

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);

        // 1 byte per pixel (GL_RED U8)
        glPixelStorei(GL_UNPACK_ROW_LENGTH, lineSizeBytes);

        int cap = lineSizeBytes * ph;
        ByteBuffer buf = data.position(0).capacity(cap).asBuffer();

        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, pw, ph, GL_RED, GL_UNSIGNED_BYTE, buf);

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    @Override
    public void close() {
        if (texY != 0) glDeleteTextures(texY);
        if (texU != 0) glDeleteTextures(texU);
        if (texV != 0) glDeleteTextures(texV);
        texY = texU = texV = 0;
    }
}