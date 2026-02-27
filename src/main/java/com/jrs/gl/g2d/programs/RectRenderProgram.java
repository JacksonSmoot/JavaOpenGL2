package com.jrs.gl.g2d.programs;

import com.jrs.gl.resources.GLBuffer;
import com.jrs.gl.resources.GLVertexArray;
import com.jrs.gl.resources.ShaderProgram;

import static org.lwjgl.opengl.ARBVertexArrayObject.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

public class RectRenderProgram {
    // target = GL_ARRAY_BUFFER
    private final GLBuffer vbo;

    private final GLVertexArray vao;

    private final ShaderProgram shader;

    public RectRenderProgram(ShaderProgram shader) {
        this.shader = shader;

        this.vao = new GLVertexArray();
        this.vbo = new GLBuffer(GL_ARRAY_BUFFER);

        vao.bind();
        vbo.bind();

        glBufferData(GL_ARRAY_BUFFER, 12L * Float.BYTES, GL_DYNAMIC_DRAW);

        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0);
        glEnableVertexAttribArray(0);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }

    public GLBuffer getVBO() { return vbo; }
    public GLVertexArray getVAO() { return vao; }
    public ShaderProgram getShader() { return shader; }
}
