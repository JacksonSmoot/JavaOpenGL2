package com.jrs.gl.g2d.programs;

import com.jrs.gl.resources.GLBuffer;
import com.jrs.gl.resources.GLVertexArray;
import com.jrs.gl.resources.ShaderProgram;

import static org.lwjgl.opengl.ARBVertexArrayObject.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;

public class TriangleRenderProgram {
    // target = GL_ARRAY_BUFFER
    private final GLBuffer vbo;

    private final GLVertexArray vao;

    private final ShaderProgram shader;

    public TriangleRenderProgram(ShaderProgram shader) {
        this.shader = shader;
        this.vao = new GLVertexArray();
        this.vbo = null;
    }

    public GLBuffer getVBO() { return vbo; }
    public GLVertexArray getVAO() { return vao; }
    public ShaderProgram getShader() { return shader; }
}
