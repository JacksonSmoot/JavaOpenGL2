package com.jrs.gl.g2d;

import com.jrs.gl.g2d.programs.*;
import com.jrs.gl.resources.*;
import com.jrs.gl.managers.DiskShaderManager;
import com.jrs.gl.math.NdcMath;

import java.awt.*;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import com.jrs.gl.components.Window;
import com.jrs.gl.resources.texture.GLImage;
import com.jrs.gl.resources.texture.GLTexture;
import com.jrs.gl.resources.texture.GLYuv420pTexture;
import org.lwjgl.system.MemoryStack;

import com.jrs.gl.util.IOUtil;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

public class GL2D implements AutoCloseable {
    private final DiskShaderManager shaderManager;

    private final RectRenderProgram rectRenderProgram;
    private final TriangleRenderProgram triangleRenderProgram;
    private final ImageRenderProgram imageRenderProgram;
    private final ShadedTriangleRenderProgram shadedTriangleRenderProgram;
    private final Yuv420pRenderProgram yuv420pRenderProgram;

    private final Window window;

    public GL2D(Window window) {
        this.window = window;
        Path shaderDirPath = IOUtil.defaultShaderDir();
        try {
            shaderManager = new DiskShaderManager(
                    shaderDirPath
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize shaders.", e);
        }

        rectRenderProgram = new RectRenderProgram(shaderManager.getShaderProgram("rect"));
        triangleRenderProgram = new TriangleRenderProgram(shaderManager.getShaderProgram("rect"));
        imageRenderProgram = new ImageRenderProgram(shaderManager.getShaderProgram("textured"));
        shadedTriangleRenderProgram = new ShadedTriangleRenderProgram(shaderManager.getShaderProgram("shaded_triangle"));
        yuv420pRenderProgram = new Yuv420pRenderProgram(shaderManager.getShaderProgram("yuv"));
    }

    public void setClearColor(int r, int g, int b, int a) {
        glClearColor(
                r / 255f,
                g / 255f,
                b / 255f,
                a / 255f
        );
    }

    public void clear() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    public void clear(int mask) {
        glClear(mask);
    }

    // Normal Device Cords (NDC) render methods

    // render methods
    public void fillRect(int x, int y, int w, int h,
                         Color color) {
        fillRect(x, y, w, h, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    public void fillRect(int x, int y, int w, int h,
                         int r, int g, int b, int a) {

        Dimension windowSize = window.getWindowSize();

        float xx = NdcMath.ndcX(x, windowSize.width);
        float yy = NdcMath.ndcY(y + h, windowSize.height);

        float ww = NdcMath.ndcWidth(w, windowSize.width);
        float hh = NdcMath.ndcHeight(h, windowSize.height);

        float rr = r / 255.0f;
        float gg = g / 255.0f;
        float bb = b / 255.0f;
        float aa = a / 255.0f;

        float x0 = xx,     y0 = yy;
        float x1 = xx + ww, y1 = yy + hh;

        // two triangles (pixel coords)
        float[] verts = {
                x0, y0,
                x1, y0,
                x1, y1,
                x0, y0,
                x1, y1,
                x0, y1
        };

        ShaderProgram shader = rectRenderProgram.getShader();

        shader.bind();
        shader.setUniform4f("color", rr, gg, bb, aa);

        rectRenderProgram.getVAO().bind();
        rectRenderProgram.getVBO().bind();

        // upload this rectangle's vertices into the VBO
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, fb);
        }

        glDrawArrays(GL_TRIANGLES, 0, 6);

        GLBuffer.unbind(GL_ARRAY_BUFFER);
        GLVertexArray.unbind();
    }

    public void fillTriangle(int x1, int y1, int x2, int y2, int x3, int y3,
                        int r, int g, int b, int a) {

        Dimension windowSize = window.getWindowSize();

        float xx1 = NdcMath.ndcX(x1, windowSize.width);
        float yy1 = NdcMath.ndcY(y1, windowSize.height);

        float xx2 = NdcMath.ndcX(x2, windowSize.width);
        float yy2 = NdcMath.ndcY(y2, windowSize.height);

        float xx3 = NdcMath.ndcX(x3, windowSize.width);
        float yy3 = NdcMath.ndcY(y3, windowSize.height);

        float rr = r / 255.0f;
        float gg = g / 255.0f;
        float bb = b / 255.0f;
        float aa = a / 255.0f;

        // two triangles (pixel coords)
        float[] verts = {
                xx1, yy1,
                xx2, yy2,
                xx3, yy3,
        };

        ShaderProgram shader = triangleRenderProgram.getShader();

        shader.bind();
        shader.setUniform4f("color", rr, gg, bb, aa);

        triangleRenderProgram.getVAO().bind();
        triangleRenderProgram.getVBO().bind();

        // upload this rectangle's vertices into the VBO
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, fb);
        }

        glDrawArrays(GL_TRIANGLES, 0, 3);

        GLBuffer.unbind(GL_ARRAY_BUFFER);
        GLVertexArray.unbind();
    }



    public void fillShadedTriangle(int[] vert1, int[] vert2, int[] vert3) {

        Dimension windowSize = window.getWindowSize();

        float vx1 = NdcMath.ndcX(vert1[0], windowSize.width);
        float vy1 = NdcMath.ndcY(vert1[1], windowSize.height);
        float vr1 = vert1[2] / 255.0f;
        float vg1 = vert1[3] / 255.0f;
        float vb1 = vert1[4] / 255.0f;

        float vx2 = NdcMath.ndcX(vert2[0], windowSize.width);
        float vy2 = NdcMath.ndcY(vert2[1], windowSize.height);
        float vr2 = vert2[2] / 255.0f;
        float vg2 = vert2[3] / 255.0f;
        float vb2 = vert2[4] / 255.0f;

        float vx3 = NdcMath.ndcX(vert3[0], windowSize.width);
        float vy3 = NdcMath.ndcY(vert3[1], windowSize.height);
        float vr3 = vert3[2] / 255.0f;
        float vg3 = vert3[3] / 255.0f;
        float vb3 = vert3[4] / 255.0f;

        float[] verts = {
                vx1, vy1, vr1, vg1, vb1,
                vx2, vy2, vr2, vg2, vb2,
                vx3, vy3, vr3, vg3, vb3
        };

        ShaderProgram shader = shadedTriangleRenderProgram.getShader();

        shader.bind();

        shadedTriangleRenderProgram.getVAO().bind();
        shadedTriangleRenderProgram.getVBO().bind();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, fb);
        }

        glDrawArrays(GL_TRIANGLES, 0, 3);

        GLBuffer.unbind(GL_ARRAY_BUFFER);
        GLVertexArray.unbind();
    }

    // u0, v0, u1, v1
    public static final float[] IMAGE_FLIPPED = {0f, 0f, 1f, 1f};

    public static final float[] IMAGE_NORMAL = {1f, 1f, 0f, 0f};

    public static final float[] IMAGE_VFLIP = {0f, 1f, 1f, 0f};

    public static final float[] IMAGE_HFLIP = {1f, 0f, 0f, 1f};

    public void drawImage(int textureId, int x, int y, int w, int h, float[] samplingMatrix){
        Dimension windowSize = window.getWindowSize();

        // Convert pixel top-left coords to NDC
        float x0 = NdcMath.ndcX(x, windowSize.width);
        float yTop = NdcMath.ndcY(y, windowSize.height);

        float wNdc = NdcMath.ndcWidth(w, windowSize.width);
        float hNdc = NdcMath.ndcHeight(h, windowSize.height);

        float x1 = x0 + wNdc;
        float y0 = yTop - hNdc;   // bottom
        float y1 = yTop;          // top
        float[] verts = {
                // x,  y,  u,  v
                x0, y0, samplingMatrix[0], samplingMatrix[1],
                x1, y0, samplingMatrix[2], samplingMatrix[1],
                x1, y1, samplingMatrix[2], samplingMatrix[3],

                x0, y0, samplingMatrix[0], samplingMatrix[1],
                x1, y1, samplingMatrix[2], samplingMatrix[3],
                x0, y1, samplingMatrix[0], samplingMatrix[3]
        };

        ShaderProgram shader = imageRenderProgram.getShader();

        shader.bind();

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureId);
        shader.setUniform1i("tex", 0);

        imageRenderProgram.getVAO().bind();
        imageRenderProgram.getVBO().bind();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, fb);
        }

        glDrawArrays(GL_TRIANGLES, 0, 6);

        GLBuffer.unbind(GL_ARRAY_BUFFER);
        GLVertexArray.unbind();

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void drawImage(GLYuv420pTexture yuv, int x, int y, int w, int h, float[] samplingMatrix) {
        Dimension windowSize = window.getWindowSize();

        float x0 = NdcMath.ndcX(x, windowSize.width);
        float yTop = NdcMath.ndcY(y, windowSize.height);

        float wNdc = NdcMath.ndcWidth(w, windowSize.width);
        float hNdc = NdcMath.ndcHeight(h, windowSize.height);

        float x1 = x0 + wNdc;
        float y0 = yTop - hNdc;   // bottom
        float y1 = yTop;          // top

        float[] verts = {
                // x,  y,  u,  v
                x0, y0, samplingMatrix[0], samplingMatrix[1],
                x1, y0, samplingMatrix[2], samplingMatrix[1],
                x1, y1, samplingMatrix[2], samplingMatrix[3],

                x0, y0, samplingMatrix[0], samplingMatrix[1],
                x1, y1, samplingMatrix[2], samplingMatrix[3],
                x0, y1, samplingMatrix[0], samplingMatrix[3]
        };

        ShaderProgram shader = yuv420pRenderProgram.getShader();
        shader.bind();

        // Bind planes to texture units 0/1/2
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, yuv.texY());
        shader.setUniform1i("texY", 0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, yuv.texU());
        shader.setUniform1i("texU", 1);

        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, yuv.texV());
        shader.setUniform1i("texV", 2);

        yuv420pRenderProgram.getVAO().bind();
        yuv420pRenderProgram.getVBO().bind();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(verts.length);
            fb.put(verts).flip();
            glBufferSubData(GL_ARRAY_BUFFER, 0, fb);
        }

        glDrawArrays(GL_TRIANGLES, 0, 6);

        GLBuffer.unbind(GL_ARRAY_BUFFER);
        GLVertexArray.unbind();

        // unbind textures
        glActiveTexture(GL_TEXTURE2);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, 0);

        ShaderProgram.unbind();
    }

    public void drawImage(GLTexture texture, int x, int y, int w, int h, float[] samplingMatrix){
        drawImage(texture.getId(), x, y, w, h, samplingMatrix);
    }

    public void drawImage(GLTexture texture, int x, int y, int w, int h){
        drawImage(texture, x, y, w, h, IMAGE_FLIPPED);
    }

    public void drawImage(GLImage image, int x, int y) {
        drawImage(image, x, y, image.getWidth(), image.getHeight());
    }

    public void drawImage(GLImage image, int x, int y, float[] samplingMatrix) {
        drawImage(image, x, y, image.getWidth(), image.getHeight(), samplingMatrix);
    }

    // other
    @Override
    public void close(){
        shaderManager.close();

    }
}
