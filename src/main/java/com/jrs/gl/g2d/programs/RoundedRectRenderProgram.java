package com.jrs.gl.g2d.programs;

import com.jrs.gl.resources.GLBuffer;
import com.jrs.gl.resources.GLVertexArray;
import com.jrs.gl.resources.ShaderProgram;

import static org.lwjgl.opengl.ARBVertexArrayObject.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

public class RoundedRectRenderProgram {
    // target = GL_ARRAY_BUFFER
    private final GLBuffer vbo;

    private final GLVertexArray vao;

    private final ShaderProgram shader;

    public RoundedRectRenderProgram(ShaderProgram shader) {
        this.shader = shader;

        this.vao = new GLVertexArray();
        this.vbo = new GLBuffer(GL_ARRAY_BUFFER);

        vao.bind();
        vbo.bind();

        // 6 vertices, each has vec2 aLocal in [0..1]
        float[] quad = {
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 0f,
                1f, 1f,
                0f, 1f
        };

        glBufferData(GL_ARRAY_BUFFER, quad, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L);
        glEnableVertexAttribArray(0);

        // GLBuffer.unbind(GL_ARRAY_BUFFER);
        vbo.unbind();
        GLVertexArray.unbind();
    }

    public GLBuffer getVBO() { return vbo; }
    public GLVertexArray getVAO() { return vao; }
    public ShaderProgram getShader() { return shader; }
}
