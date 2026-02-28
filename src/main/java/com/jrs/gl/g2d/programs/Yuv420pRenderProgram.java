package com.jrs.gl.g2d.programs;

import com.jrs.gl.resources.GLBuffer;
import com.jrs.gl.resources.GLVertexArray;
import com.jrs.gl.resources.ShaderProgram;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;

public final class Yuv420pRenderProgram {
    private final GLBuffer vbo;
    private final GLVertexArray vao;
    private final ShaderProgram shader;

    public Yuv420pRenderProgram(ShaderProgram shader) {
        this.shader = shader;

        this.vao = new GLVertexArray();
        this.vbo = new GLBuffer(GL_ARRAY_BUFFER);

        vao.bind();
        vbo.bind();

        glBufferData(GL_ARRAY_BUFFER, 24L * Float.BYTES, GL_DYNAMIC_DRAW);

        int stride = 4 * Float.BYTES;

        glVertexAttribPointer(0, 2, GL_FLOAT, false, stride, 0L);
        glEnableVertexAttribArray(0);

        glVertexAttribPointer(1, 2, GL_FLOAT, false, stride, 2L * Float.BYTES);
        glEnableVertexAttribArray(1);

        // GLBuffer.unbind(GL_ARRAY_BUFFER);
        vbo.unbind();
        GLVertexArray.unbind();
    }

    public GLBuffer getVBO() { return vbo; }
    public GLVertexArray getVAO() { return vao; }
    public ShaderProgram getShader() { return shader; }
}